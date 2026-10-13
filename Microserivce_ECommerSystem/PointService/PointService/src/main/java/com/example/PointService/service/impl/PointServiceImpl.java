package com.example.PointService.service.impl;

import com.example.PointService.dto.*;
import com.example.PointService.entity.PointAccountEntity;
import com.example.PointService.entity.PointTransactionEntity;
import com.example.PointService.entity.PointTransactionType;
import com.example.PointService.exception.AppException;
import com.example.PointService.exception.ErrorCode;
import com.example.PointService.repository.PointAccountRepository;
import com.example.PointService.repository.PointTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PointServiceImpl implements com.example.PointService.service.PointService {

    private final PointAccountRepository accountRepository;
    private final PointTransactionRepository transactionRepository;

    @Value("${point.earn-rate:0.10}")
    private BigDecimal earnRate;

    @Value("${point.max-redeem-ratio:0.50}")
    private BigDecimal maxRedeemRatio;

    @Override
    public PointConfigResponseDTO getConfig() {
        return PointConfigResponseDTO.builder()
                .earnRate(earnRate)
                .maxRedeemRatio(maxRedeemRatio)
                .build();
    }

    // ─────────────────────────────────────────────
    // CỘNG ĐIỂM (EARN) — khi đơn hàng DELIVERED,
    // tính trên giá trị gốc (tiền hàng) của đơn
    // ─────────────────────────────────────────────
    @Override
    @Transactional
    public PointEarnResponseDTO earn(PointEarnRequestDTO request) {
        // Idempotent: nếu orderId này đã được cộng điểm rồi (Kafka gửi lại / gọi lại) thì không cộng thêm lần 2
        Optional<PointTransactionEntity> existing =
                transactionRepository.findByOrderIdAndType(request.getOrderId(), PointTransactionType.EARN);

        PointAccountEntity account = getOrCreateAccount(request.getUserId());

        if (existing.isPresent()) {
            log.info("ℹ️ Order {} đã được cộng điểm trước đó, bỏ qua.", request.getOrderId());
            return PointEarnResponseDTO.builder()
                    .orderId(request.getOrderId())
                    .pointsEarned(existing.get().getPoints())
                    .balanceAfter(account.getBalance())
                    .build();
        }

        BigDecimal pointsEarned = request.getOrderAmount()
                .multiply(earnRate)
                .setScale(0, RoundingMode.DOWN);

        account.setBalance(account.getBalance().add(pointsEarned));
        accountRepository.save(account);

        transactionRepository.save(PointTransactionEntity.builder()
                .userId(request.getUserId())
                .orderId(request.getOrderId())
                .type(PointTransactionType.EARN)
                .points(pointsEarned)
                .balanceAfter(account.getBalance())
                .build());

        log.info("✅ Cộng {} điểm cho user {} từ đơn {}", pointsEarned, request.getUserId(), request.getOrderId());

        return PointEarnResponseDTO.builder()
                .orderId(request.getOrderId())
                .pointsEarned(pointsEarned)
                .balanceAfter(account.getBalance())
                .build();
    }

    // ─────────────────────────────────────────────
    // DÙNG ĐIỂM (REDEEM) — giảm giá đơn tiếp theo
    // ─────────────────────────────────────────────
    @Override
    @Transactional
    public PointRedeemResponseDTO redeem(PointRedeemRequestDTO request) {
        Optional<PointTransactionEntity> existing =
                transactionRepository.findByOrderIdAndType(request.getOrderId(), PointTransactionType.REDEEM);

        PointAccountEntity account = getOrCreateAccount(request.getUserId());

        if (existing.isPresent()) {
            log.info("ℹ️ Order {} đã dùng điểm trước đó, trả lại kết quả cũ.", request.getOrderId());
            return PointRedeemResponseDTO.builder()
                    .orderId(request.getOrderId())
                    .pointsRedeemed(existing.get().getPoints())
                    .discountAmount(existing.get().getPoints())
                    .balanceAfter(account.getBalance())
                    .build();
        }

        // Giới hạn theo tiền hàng để đơn không bao giờ về 0đ
        BigDecimal maxRedeemable = request.getOrderAmount().multiply(maxRedeemRatio);
        if (request.getPoints().compareTo(maxRedeemable) > 0) {
            throw new AppException(ErrorCode.POINTS_EXCEED_LIMIT);
        }

        if (account.getBalance().compareTo(request.getPoints()) < 0) {
            throw new AppException(ErrorCode.INSUFFICIENT_POINTS);
        }

        account.setBalance(account.getBalance().subtract(request.getPoints()));
        accountRepository.save(account);

        transactionRepository.save(PointTransactionEntity.builder()
                .userId(request.getUserId())
                .orderId(request.getOrderId())
                .type(PointTransactionType.REDEEM)
                .points(request.getPoints())
                .balanceAfter(account.getBalance())
                .build());

        log.info("✅ User {} dùng {} điểm cho đơn {}", request.getUserId(), request.getPoints(), request.getOrderId());

        return PointRedeemResponseDTO.builder()
                .orderId(request.getOrderId())
                .pointsRedeemed(request.getPoints())
                .discountAmount(request.getPoints()) // quy đổi 1 điểm = 1 VNĐ
                .balanceAfter(account.getBalance())
                .build();
    }

    // ─────────────────────────────────────────────
    // ĐẢO NGƯỢC (REVERSE) — khi đơn hàng bị hủy/trả hàng
    // ─────────────────────────────────────────────
    @Override
    @Transactional
    public void reverseForOrder(String orderId) {
        List<PointTransactionEntity> transactions = transactionRepository.findByOrderId(orderId);

        for (PointTransactionEntity tx : transactions) {
            if (tx.getType() == PointTransactionType.EARN) {
                reverseIfNotAlready(orderId, tx, PointTransactionType.REVERSE_EARN, false);
            } else if (tx.getType() == PointTransactionType.REDEEM) {
                reverseIfNotAlready(orderId, tx, PointTransactionType.REVERSE_REDEEM, true);
            }
        }
    }

    /**
     * @param giveBack true = cộng lại điểm cho user (hoàn điểm đã dùng ở REDEEM),
     *                 false = trừ lại điểm đã cộng (thu hồi điểm ở EARN)
     */
    private void reverseIfNotAlready(String orderId, PointTransactionEntity original,
                                      PointTransactionType reverseType, boolean giveBack) {
        if (transactionRepository.findByOrderIdAndType(orderId, reverseType).isPresent()) {
            return; // đã đảo ngược rồi, tránh xử lý trùng
        }

        PointAccountEntity account = getOrCreateAccount(original.getUserId());

        // Số dư không được âm: nếu user đã tiêu bớt số điểm cần thu hồi thì chỉ thu hồi
        // phần còn lại. Dòng sổ cái ghi đúng số điểm thực thu (có thể = 0) và vẫn được
        // lưu để đánh dấu đơn này đã đảo ngược.
        BigDecimal points = giveBack
                ? original.getPoints()
                : original.getPoints().min(account.getBalance()).max(BigDecimal.ZERO);
        if (points.compareTo(original.getPoints()) < 0) {
            log.warn("⚠️ Đơn {}: cần thu hồi {} điểm nhưng user {} chỉ còn {}, thu hồi {}",
                    orderId, original.getPoints(), original.getUserId(), account.getBalance(), points);
        }

        BigDecimal newBalance = giveBack
                ? account.getBalance().add(points)
                : account.getBalance().subtract(points);

        account.setBalance(newBalance);
        accountRepository.save(account);

        transactionRepository.save(PointTransactionEntity.builder()
                .userId(original.getUserId())
                .orderId(orderId)
                .type(reverseType)
                .points(points)
                .balanceAfter(newBalance)
                .build());

        log.info("✅ Đảo ngược {} cho đơn {}: {} điểm, số dư mới {}", reverseType, orderId, points, newBalance);
    }

    // ─────────────────────────────────────────────
    // TRA CỨU
    // ─────────────────────────────────────────────
    @Override
    public PointBalanceResponseDTO getBalance(String userId) {
        PointAccountEntity account = getOrCreateAccount(userId);
        return PointBalanceResponseDTO.builder()
                .userId(userId)
                .balance(account.getBalance())
                .build();
    }

    @Override
    public List<PointTransactionResponseDTO> getHistory(String userId) {
        return transactionRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(tx -> PointTransactionResponseDTO.builder()
                        .id(tx.getId())
                        .orderId(tx.getOrderId())
                        .type(tx.getType())
                        .points(tx.getPoints())
                        .balanceAfter(tx.getBalanceAfter())
                        .createdAt(tx.getCreatedAt())
                        .build())
                .toList();
    }

    private PointAccountEntity getOrCreateAccount(String userId) {
        return accountRepository.findById(userId)
                .orElseGet(() -> accountRepository.save(
                        PointAccountEntity.builder().userId(userId).balance(BigDecimal.ZERO).build()));
    }
}
