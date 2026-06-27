package com.example.ProductService.service;

import com.example.ProductService.entity.ProductEntity;
import com.example.ProductService.repository.ProductRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class HolidayService {

    private final ProductRepository productRepository;

    private static final Map<MonthDay, String> FIXED_HOLIDAYS = new HashMap<>();

    static {
        FIXED_HOLIDAYS.put(MonthDay.of(1, 1), "Tết Dương Lịch");
        FIXED_HOLIDAYS.put(MonthDay.of(4, 30), "Giải phóng miền Nam");
        FIXED_HOLIDAYS.put(MonthDay.of(5, 1), "Quốc tế Lao động");
        FIXED_HOLIDAYS.put(MonthDay.of(9, 2), "Quốc khánh");
        FIXED_HOLIDAYS.put(MonthDay.of(12, 24), "Giáng sinh (Eve)");
        FIXED_HOLIDAYS.put(MonthDay.of(4, 18), "Giáng sinh");
    }

    public String getHolidayName() {
        return FIXED_HOLIDAYS.get(MonthDay.from(LocalDate.now()));
    }

    public boolean isHoliday() {
        return getHolidayName() != null;
    }

    @PostConstruct
    public void init() {
        processHolidayDiscounts();
    }

    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void scheduledTask() {
        processHolidayDiscounts();
    }

    @Transactional
    public void processHolidayDiscounts() {
        String holidayName = getHolidayName();
        List<ProductEntity> products = productRepository.findAll();
        boolean isHoliday = (holidayName != null);
        String targetEventId = isHoliday ? "HOLIDAY_" + holidayName.replaceAll(" ", "_").toUpperCase() : null;

        log.info("🔍 Kiểm tra giá ngày lễ: {}. Mục tiêu EventId: {}", isHoliday ? holidayName : "N/A", targetEventId);

        boolean dataChanged = false;

        for (ProductEntity product : products) {
            String currentEventId = product.getActiveEventId();
            
            // TRƯỜNG HỢP 1: ĐANG TRONG NGÀY LỄ
            if (isHoliday) {
                if (targetEventId.equals(currentEventId)) continue;

                // Khôi phục giá gốc trước khi áp dụng giảm giá (nếu đang bị giảm bởi event khác)
                if (product.getOriginalPrice() != null) {
                    product.setPrice(product.getOriginalPrice());
                }

                BigDecimal originalPrice = product.getPrice();
                product.setOriginalPrice(originalPrice);
                product.setPrice(originalPrice.multiply(BigDecimal.valueOf(0.7))); // Giảm 30%
                product.setIsOnSale(true);
                product.setDiscountPercentage(30);
                product.setActiveEventId(targetEventId);
                dataChanged = true;
            } 
            
            // TRƯỜNG HỢP 2: KHÔNG PHẢI NGÀY LỄ
            else {
                if (currentEventId != null && currentEventId.startsWith("HOLIDAY_")) {
                    if (product.getOriginalPrice() != null) {
                        product.setPrice(product.getOriginalPrice());
                    }
                    product.setIsOnSale(false);
                    product.setDiscountPercentage(0);
                    product.setActiveEventId(null);
                    dataChanged = true;
                }
            }
        }

        if (dataChanged) {
            productRepository.saveAll(products);
            log.info("✅ Đã cập nhật xong Database.");
        }
    }
}
