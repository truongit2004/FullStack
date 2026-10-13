package com.example.CouponService.service;

import com.example.CouponService.dto.CouponDiscountResponseDTO;
import com.example.CouponService.dto.CouponRedeemRequestDTO;
import com.example.CouponService.dto.CouponRequestDTO;
import com.example.CouponService.dto.CouponResponseDTO;
import com.example.CouponService.entity.CouponEntity;
import com.example.CouponService.entity.CouponType;
import com.example.CouponService.entity.CouponUsageEntity;
import com.example.CouponService.entity.CouponUsageStatus;
import com.example.CouponService.exception.AppException;
import com.example.CouponService.exception.ErrorCode;
import com.example.CouponService.repository.CouponRepository;
import com.example.CouponService.repository.CouponUsageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final CouponRepository couponRepository;
    private final CouponUsageRepository usageRepository;

    // ─────────────────────────────────────────────
    // XEM TRƯỚC (PREVIEW) — khách bấm "Áp dụng" ở Checkout. Chỉ kiểm tra và tính, KHÔNG trừ lượt.
    // ─────────────────────────────────────────────
    @Transactional(readOnly = true)
    public CouponDiscountResponseDTO preview(String code, String userId, BigDecimal orderAmount) {
        CouponEntity coupon = couponRepository.findByCode(normalize(code))
                .orElseThrow(() -> new AppException(ErrorCode.COUPON_NOT_FOUND));
        validateUsable(coupon, userId, orderAmount);
        return CouponDiscountResponseDTO.builder()
                .code(coupon.getCode())
                .description(coupon.getDescription())
                .discountAmount(calculateDiscount(coupon, orderAmount))
                .build();
    }

    // ─────────────────────────────────────────────
    // DÙNG MÃ (REDEEM) — Order Service gọi khi tạo đơn. Đây là lúc lượt dùng thực sự bị trừ.
    // ─────────────────────────────────────────────
    @Transactional
    public CouponDiscountResponseDTO redeem(CouponRedeemRequestDTO request) {
        // Idempotent: đơn này đã dùng mã rồi (Order Service gọi lại do mạng lỗi) thì trả kết quả cũ
        Optional<CouponUsageEntity> existing = usageRepository.findByOrderId(request.getOrderId());
        if (existing.isPresent() && existing.get().getStatus() == CouponUsageStatus.USED) {
            log.info("ℹ️ Order {} đã dùng mã {} trước đó, trả lại kết quả cũ.", request.getOrderId(), existing.get().getCode());
            return toDiscountResponse(existing.get(), null);
        }

        // Khóa dòng của mã: kiểm tra "còn lượt" và "tăng lượt" phải là một thao tác không bị chen ngang
        CouponEntity coupon = couponRepository.findByCodeForUpdate(normalize(request.getCode()))
                .orElseThrow(() -> new AppException(ErrorCode.COUPON_NOT_FOUND));

        // Kiểm tra lại toàn bộ: từ lúc khách xem trước tới giờ mã có thể đã hết lượt / hết hạn
        validateUsable(coupon, request.getUserId(), request.getOrderAmount());
        BigDecimal discount = calculateDiscount(coupon, request.getOrderAmount());

        coupon.setUsedCount(coupon.getUsedCount() + 1);
        couponRepository.save(coupon);

        // Đơn từng dùng mã rồi bị hoàn (hiếm) thì dùng lại chính dòng đó, vì order_id là duy nhất
        CouponUsageEntity usage = existing.orElseGet(CouponUsageEntity::new);
        usage.setCouponId(coupon.getId());
        usage.setCode(coupon.getCode());
        usage.setUserId(request.getUserId());
        usage.setOrderId(request.getOrderId());
        usage.setDiscountAmount(discount);
        usage.setStatus(CouponUsageStatus.USED);
        usage.setReversedAt(null);
        usageRepository.save(usage);

        log.info("✅ User {} dùng mã {} cho đơn {}: giảm {}", request.getUserId(), coupon.getCode(), request.getOrderId(), discount);
        return toDiscountResponse(usage, coupon.getDescription());
    }

    // ─────────────────────────────────────────────
    // HOÀN MÃ (REVERSE) — đơn bị hủy / trả hàng / tạo đơn thất bại: trả lại lượt cho mã và cho khách
    // ─────────────────────────────────────────────
    @Transactional
    public void reverseForOrder(String orderId) {
        Optional<CouponUsageEntity> found = usageRepository.findByOrderId(orderId);
        if (found.isEmpty() || found.get().getStatus() == CouponUsageStatus.REVERSED) {
            return; // đơn không dùng mã, hoặc đã hoàn rồi -> gọi lại nhiều lần vẫn an toàn
        }
        CouponUsageEntity usage = found.get();

        couponRepository.findByIdForUpdate(usage.getCouponId()).ifPresent(coupon -> {
            coupon.setUsedCount(Math.max(0, coupon.getUsedCount() - 1));
            couponRepository.save(coupon);
        });

        usage.setStatus(CouponUsageStatus.REVERSED);
        usage.setReversedAt(LocalDateTime.now());
        usageRepository.save(usage);
        log.info("✅ Hoàn mã {} cho đơn {} (user {})", usage.getCode(), orderId, usage.getUserId());
    }

    // ─────────────────────────────────────────────
    // DANH SÁCH MÃ ĐANG DÙNG ĐƯỢC — cho khách xem ở Checkout
    // ─────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<CouponResponseDTO> getAvailable() {
        LocalDateTime now = LocalDateTime.now();
        return couponRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(c -> Boolean.TRUE.equals(c.getActive()))
                .filter(c -> c.getStartAt() == null || !now.isBefore(c.getStartAt()))
                .filter(c -> c.getEndAt() == null || !now.isAfter(c.getEndAt()))
                .filter(c -> c.getUsageLimit() == null || c.getUsedCount() < c.getUsageLimit())
                .map(this::toResponse)
                .toList();
    }

    // ─────────────────────────────────────────────
    // QUẢN TRỊ
    // ─────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<CouponResponseDTO> getAll() {
        return couponRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public CouponResponseDTO create(CouponRequestDTO request) {
        String code = normalize(request.getCode());
        if (couponRepository.existsByCode(code)) {
            throw new AppException(ErrorCode.COUPON_CODE_EXISTS);
        }
        validateDefinition(request);

        CouponEntity coupon = CouponEntity.builder()
                .code(code)
                .description(request.getDescription())
                .type(request.getType())
                .value(request.getValue())
                .maxDiscount(request.getType() == CouponType.PERCENT ? request.getMaxDiscount() : null)
                .minOrderAmount(request.getMinOrderAmount())
                .startAt(request.getStartAt())
                .endAt(request.getEndAt())
                .usageLimit(request.getUsageLimit())
                .perUserLimit(request.getPerUserLimit() != null ? request.getPerUserLimit() : 1)
                .active(request.getActive() == null || request.getActive())
                .build();
        return toResponse(couponRepository.save(coupon));
    }

    @Transactional
    public CouponResponseDTO update(String id, CouponRequestDTO request) {
        CouponEntity coupon = couponRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.COUPON_ID_NOT_FOUND));
        validateDefinition(request);

        String code = normalize(request.getCode());
        boolean ruleChanged = !code.equals(coupon.getCode())
                || request.getType() != coupon.getType()
                || request.getValue().compareTo(coupon.getValue()) != 0;

        // Đã có người dùng thì không đổi luật giảm nữa: các đơn cũ đã được tính theo luật cũ
        if (ruleChanged && usageRepository.existsByCouponId(coupon.getId())) {
            throw new AppException(ErrorCode.COUPON_ALREADY_USED);
        }
        if (!code.equals(coupon.getCode()) && couponRepository.existsByCode(code)) {
            throw new AppException(ErrorCode.COUPON_CODE_EXISTS);
        }

        coupon.setCode(code);
        coupon.setDescription(request.getDescription());
        coupon.setType(request.getType());
        coupon.setValue(request.getValue());
        coupon.setMaxDiscount(request.getType() == CouponType.PERCENT ? request.getMaxDiscount() : null);
        coupon.setMinOrderAmount(request.getMinOrderAmount());
        coupon.setStartAt(request.getStartAt());
        coupon.setEndAt(request.getEndAt());
        coupon.setUsageLimit(request.getUsageLimit());
        if (request.getPerUserLimit() != null) coupon.setPerUserLimit(request.getPerUserLimit());
        if (request.getActive() != null) coupon.setActive(request.getActive());
        return toResponse(couponRepository.save(coupon));
    }

    @Transactional
    public CouponResponseDTO setActive(String id, boolean active) {
        CouponEntity coupon = couponRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.COUPON_ID_NOT_FOUND));
        coupon.setActive(active);
        return toResponse(couponRepository.save(coupon));
    }

    // ─────────────────────────────────────────────
    // HELPER
    // ─────────────────────────────────────────────

    /** Kiểm tra lần lượt các điều kiện; sai ở đâu ném lỗi nêu đúng lý do đó. */
    private void validateUsable(CouponEntity coupon, String userId, BigDecimal orderAmount) {
        LocalDateTime now = LocalDateTime.now();

        if (!Boolean.TRUE.equals(coupon.getActive())) {
            throw new AppException(ErrorCode.COUPON_INACTIVE);
        }
        if (coupon.getStartAt() != null && now.isBefore(coupon.getStartAt())) {
            throw new AppException(ErrorCode.COUPON_NOT_STARTED);
        }
        if (coupon.getEndAt() != null && now.isAfter(coupon.getEndAt())) {
            throw new AppException(ErrorCode.COUPON_EXPIRED);
        }
        if (coupon.getUsageLimit() != null && coupon.getUsedCount() >= coupon.getUsageLimit()) {
            throw new AppException(ErrorCode.COUPON_EXHAUSTED);
        }
        if (userId != null && !userId.isBlank()) {
            long usedByUser = usageRepository.countByCouponIdAndUserIdAndStatus(
                    coupon.getId(), userId, CouponUsageStatus.USED);
            if (usedByUser >= coupon.getPerUserLimit()) {
                throw new AppException(ErrorCode.COUPON_USER_LIMIT_REACHED);
            }
        }
        if (coupon.getMinOrderAmount() != null && orderAmount.compareTo(coupon.getMinOrderAmount()) < 0) {
            BigDecimal missing = coupon.getMinOrderAmount().subtract(orderAmount).stripTrailingZeros();
            throw new AppException(ErrorCode.COUPON_MIN_ORDER_NOT_MET,
                    "Đơn hàng cần thêm $" + missing.toPlainString() + " tiền hàng để dùng mã này (tối thiểu $"
                            + coupon.getMinOrderAmount().stripTrailingZeros().toPlainString() + ")");
        }
    }

    private BigDecimal calculateDiscount(CouponEntity coupon, BigDecimal orderAmount) {
        BigDecimal discount;
        if (coupon.getType() == CouponType.PERCENT) {
            discount = orderAmount.multiply(coupon.getValue()).divide(HUNDRED, 2, RoundingMode.DOWN);
            if (coupon.getMaxDiscount() != null && discount.compareTo(coupon.getMaxDiscount()) > 0) {
                discount = coupon.getMaxDiscount();
            }
        } else {
            discount = coupon.getValue();
        }
        // Không bao giờ giảm quá tiền hàng
        return discount.min(orderAmount).setScale(2, RoundingMode.DOWN);
    }

    private void validateDefinition(CouponRequestDTO request) {
        if (request.getType() == CouponType.PERCENT && request.getValue().compareTo(HUNDRED) > 0) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Giảm theo phần trăm không được vượt quá 100");
        }
        if (request.getStartAt() != null && request.getEndAt() != null
                && !request.getEndAt().isAfter(request.getStartAt())) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Ngày kết thúc phải sau ngày bắt đầu");
        }
    }

    private String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }

    private CouponDiscountResponseDTO toDiscountResponse(CouponUsageEntity usage, String description) {
        return CouponDiscountResponseDTO.builder()
                .orderId(usage.getOrderId())
                .code(usage.getCode())
                .description(description)
                .discountAmount(usage.getDiscountAmount())
                .build();
    }

    private CouponResponseDTO toResponse(CouponEntity c) {
        return CouponResponseDTO.builder()
                .id(c.getId())
                .code(c.getCode())
                .description(c.getDescription())
                .type(c.getType())
                .value(c.getValue())
                .maxDiscount(c.getMaxDiscount())
                .minOrderAmount(c.getMinOrderAmount())
                .startAt(c.getStartAt())
                .endAt(c.getEndAt())
                .usageLimit(c.getUsageLimit())
                .perUserLimit(c.getPerUserLimit())
                .usedCount(c.getUsedCount())
                .active(c.getActive())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
