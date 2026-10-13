package com.example.CouponService;

import com.example.CouponService.dto.CouponDiscountResponseDTO;
import com.example.CouponService.dto.CouponRedeemRequestDTO;
import com.example.CouponService.dto.CouponRequestDTO;
import com.example.CouponService.dto.CouponResponseDTO;
import com.example.CouponService.entity.CouponType;
import com.example.CouponService.exception.AppException;
import com.example.CouponService.exception.ErrorCode;
import com.example.CouponService.repository.CouponRepository;
import com.example.CouponService.repository.CouponUsageRepository;
import com.example.CouponService.service.CouponService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm tra quy tắc mã giảm giá trên MySQL thật (cần MySQL ở localhost).
 * Dùng database riêng "coupon_service_test".
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3306/coupon_service_test?createDatabaseIfNotExist=true",
        "spring.jpa.show-sql=false",
        "eureka.client.enabled=false"
})
class CouponServiceTest {

    @Autowired CouponService couponService;
    @Autowired CouponRepository couponRepository;
    @Autowired CouponUsageRepository usageRepository;

    @BeforeEach
    void clean() {
        usageRepository.deleteAll();
        couponRepository.deleteAll();
    }

    private CouponRequestDTO.CouponRequestDTOBuilder percent(String code, String percent) {
        return CouponRequestDTO.builder().code(code).type(CouponType.PERCENT).value(new BigDecimal(percent));
    }

    private CouponRequestDTO.CouponRequestDTOBuilder fixed(String code, String amount) {
        return CouponRequestDTO.builder().code(code).type(CouponType.FIXED).value(new BigDecimal(amount));
    }

    private CouponRedeemRequestDTO redeemRequest(String user, String order, String code, String amount) {
        return CouponRedeemRequestDTO.builder().userId(user).orderId(order).code(code).orderAmount(new BigDecimal(amount)).build();
    }

    private void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    private ErrorCode errorOf(Runnable action) {
        return assertThrows(AppException.class, action::run).getErrorCode();
    }

    private int usedCount(String code) {
        return couponRepository.findByCode(code).orElseThrow().getUsedCount();
    }

    // ── Tính tiền giảm ───────────────────────────────────────────────────────

    @Test
    void percentCoupon_isCappedByMaxDiscount() {
        couponService.create(percent("SALE10", "10").maxDiscount(new BigDecimal("20")).build());

        assertMoney("8.00", couponService.preview("SALE10", "u1", new BigDecimal("80")).getDiscountAmount());
        assertMoney("20.00", couponService.preview("SALE10", "u1", new BigDecimal("500")).getDiscountAmount());
    }

    @Test
    void fixedCoupon_neverExceedsOrderAmount() {
        couponService.create(fixed("GIAM50", "50").build());

        assertMoney("50.00", couponService.preview("GIAM50", "u1", new BigDecimal("120")).getDiscountAmount());
        assertMoney("30.00", couponService.preview("GIAM50", "u1", new BigDecimal("30")).getDiscountAmount());
    }

    @Test
    void codeIsCaseInsensitive_andTrimmed() {
        couponService.create(percent("Sale10", "10").build());

        assertEquals("SALE10", couponService.preview("  sale10 ", "u1", new BigDecimal("100")).getCode());
    }

    // ── Điều kiện dùng mã: sai ở đâu báo đúng lý do đó ───────────────────────

    @Test
    void rejectsUnknown_inactive_notStarted_expired() {
        LocalDateTime now = LocalDateTime.now();
        CouponResponseDTO paused = couponService.create(percent("PAUSED", "10").build());
        couponService.setActive(paused.getId(), false);
        couponService.create(percent("FUTURE", "10").startAt(now.plusDays(1)).endAt(now.plusDays(2)).build());
        couponService.create(percent("OLD", "10").startAt(now.minusDays(2)).endAt(now.minusDays(1)).build());
        BigDecimal amount = new BigDecimal("100");

        assertEquals(ErrorCode.COUPON_NOT_FOUND, errorOf(() -> couponService.preview("KHONGCO", "u1", amount)));
        assertEquals(ErrorCode.COUPON_INACTIVE, errorOf(() -> couponService.preview("PAUSED", "u1", amount)));
        assertEquals(ErrorCode.COUPON_NOT_STARTED, errorOf(() -> couponService.preview("FUTURE", "u1", amount)));
        assertEquals(ErrorCode.COUPON_EXPIRED, errorOf(() -> couponService.preview("OLD", "u1", amount)));
    }

    @Test
    void minOrderAmount_messageSaysHowMuchIsMissing() {
        couponService.create(percent("MIN50", "10").minOrderAmount(new BigDecimal("50")).build());

        AppException ex = assertThrows(AppException.class,
                () -> couponService.preview("MIN50", "u1", new BigDecimal("42.5")));
        assertEquals(ErrorCode.COUPON_MIN_ORDER_NOT_MET, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("$7.5"), ex.getMessage());
        assertDoesNotThrow(() -> couponService.preview("MIN50", "u1", new BigDecimal("50")));
    }

    // ── Dùng mã, giới hạn lượt ───────────────────────────────────────────────

    @Test
    void preview_doesNotConsumeUsage_redeemDoes() {
        couponService.create(percent("SALE10", "10").usageLimit(5).build());

        couponService.preview("SALE10", "u1", new BigDecimal("100"));
        assertEquals(0, usedCount("SALE10"));

        CouponDiscountResponseDTO result = couponService.redeem(redeemRequest("u1", "order-1", "sale10", "100"));
        assertMoney("10.00", result.getDiscountAmount());
        assertEquals(1, usedCount("SALE10"));
    }

    @Test
    void redeem_isIdempotentPerOrder() {
        couponService.create(percent("SALE10", "10").usageLimit(5).build());

        couponService.redeem(redeemRequest("u1", "order-1", "SALE10", "100"));
        CouponDiscountResponseDTO again = couponService.redeem(redeemRequest("u1", "order-1", "SALE10", "100"));

        assertMoney("10.00", again.getDiscountAmount());
        assertEquals(1, usedCount("SALE10"), "Gọi lại cho cùng một đơn không được trừ thêm lượt");
        assertEquals(1, usageRepository.count());
    }

    @Test
    void perUserLimit_andTotalLimit_areEnforced() {
        couponService.create(percent("ONCE", "10").perUserLimit(1).usageLimit(2).build());

        couponService.redeem(redeemRequest("u1", "o1", "ONCE", "100"));
        assertEquals(ErrorCode.COUPON_USER_LIMIT_REACHED,
                errorOf(() -> couponService.redeem(redeemRequest("u1", "o2", "ONCE", "100"))));
        assertEquals(ErrorCode.COUPON_USER_LIMIT_REACHED,
                errorOf(() -> couponService.preview("ONCE", "u1", new BigDecimal("100"))));

        couponService.redeem(redeemRequest("u2", "o3", "ONCE", "100"));
        assertEquals(ErrorCode.COUPON_EXHAUSTED,
                errorOf(() -> couponService.redeem(redeemRequest("u3", "o4", "ONCE", "100"))));
        assertEquals(2, usedCount("ONCE"));
    }

    @Test
    void manyCustomersRacingForTheLastSlots_neverExceedTheLimit() throws Exception {
        int limit = 3, customers = 12;
        couponService.create(percent("FLASH", "10").usageLimit(limit).build());

        ExecutorService pool = Executors.newFixedThreadPool(customers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < customers; i++) {
            int n = i;
            results.add(pool.submit(() -> {
                start.await();
                try {
                    couponService.redeem(redeemRequest("user-" + n, "order-" + n, "FLASH", "100"));
                    return true;
                } catch (AppException e) {
                    assertEquals(ErrorCode.COUPON_EXHAUSTED, e.getErrorCode());
                    return false;
                }
            }));
        }
        start.countDown();
        int succeeded = 0;
        for (Future<Boolean> result : results) {
            if (result.get(30, TimeUnit.SECONDS)) succeeded++;
        }
        pool.shutdown();

        assertEquals(limit, succeeded, "Đúng " + limit + " khách được dùng mã, không hơn");
        assertEquals(limit, usedCount("FLASH"));
        assertEquals(limit, usageRepository.count());
    }

    // ── Hoàn mã ──────────────────────────────────────────────────────────────

    @Test
    void reverse_givesTheSlotBack_toCouponAndToCustomer() {
        couponService.create(percent("ONCE", "10").perUserLimit(1).usageLimit(1).build());
        couponService.redeem(redeemRequest("u1", "o1", "ONCE", "100"));

        couponService.reverseForOrder("o1");
        couponService.reverseForOrder("o1");   // gọi lại không trừ thêm

        assertEquals(0, usedCount("ONCE"));
        // Khách dùng lại được cho đơn khác, và cả cho chính đơn cũ
        assertDoesNotThrow(() -> couponService.redeem(redeemRequest("u1", "o2", "ONCE", "100")));
        couponService.reverseForOrder("o2");
        assertDoesNotThrow(() -> couponService.redeem(redeemRequest("u1", "o1", "ONCE", "100")));
        assertEquals(1, usedCount("ONCE"));
    }

    @Test
    void reverse_forOrderWithoutCoupon_isNoOp() {
        assertDoesNotThrow(() -> couponService.reverseForOrder("order-khong-dung-ma"));
    }

    // ── Quản trị ─────────────────────────────────────────────────────────────

    @Test
    void admin_validation() {
        couponService.create(percent("SALE10", "10").build());
        LocalDateTime now = LocalDateTime.now();

        assertEquals(ErrorCode.COUPON_CODE_EXISTS, errorOf(() -> couponService.create(percent("sale10", "5").build())));
        assertEquals(ErrorCode.INVALID_REQUEST, errorOf(() -> couponService.create(percent("BIG", "150").build())));
        assertEquals(ErrorCode.INVALID_REQUEST, errorOf(() -> couponService.create(
                percent("DATES", "10").startAt(now).endAt(now.minusDays(1)).build())));
    }

    @Test
    void usedCoupon_cannotChangeItsDiscountRule_butCanExtendDeadlineAndLimit() {
        CouponResponseDTO coupon = couponService.create(percent("SALE10", "10").usageLimit(1).build());
        couponService.redeem(redeemRequest("u1", "o1", "SALE10", "100"));

        assertEquals(ErrorCode.COUPON_ALREADY_USED,
                errorOf(() -> couponService.update(coupon.getId(), percent("SALE10", "50").usageLimit(1).build())));

        CouponResponseDTO updated = couponService.update(coupon.getId(),
                percent("SALE10", "10").usageLimit(10).endAt(LocalDateTime.now().plusDays(30)).build());
        assertEquals(10, updated.getUsageLimit());
        assertEquals(1, updated.getUsedCount());
    }

    @Test
    void available_listsOnlyCouponsCustomersCanUseNow() {
        LocalDateTime now = LocalDateTime.now();
        couponService.create(percent("GOOD", "10").build());
        couponService.create(percent("OLD", "10").endAt(now.minusDays(1)).build());
        couponService.create(percent("FULL", "10").usageLimit(1).build());
        couponService.redeem(redeemRequest("u1", "o1", "FULL", "100"));
        couponService.setActive(couponService.create(percent("OFF", "10").build()).getId(), false);

        assertEquals(List.of("GOOD"), couponService.getAvailable().stream().map(CouponResponseDTO::getCode).toList());
    }
}
