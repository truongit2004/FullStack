package com.example.ProductService.scheduler;

import com.example.ProductService.entity.ProductEntity;
import com.example.ProductService.repository.ProductRepository;
import com.example.ProductService.service.HolidayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class HolidaySaleScheduler {

    private final ProductRepository productRepository;
    private final HolidayService holidayService;

    /**
     * Chạy định kỳ vào 00:00:01 mỗi ngày để cập nhật giá.
     */
    @Scheduled(cron = "1 0 0 * * *")
    @Transactional
    public void schedulePriceUpdate() {
        processPrices();
    }

    /**
     * Chạy ngay khi ứng dụng khởi động xong để đảm bảo giá đúng với ngày hiện tại.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onStartup() {
        log.info("🚀 Ứng dụng khởi động: Kiểm tra giá khuyến mãi ngày lễ...");
        processPrices();
    }

    private void processPrices() {
        boolean isHoliday = holidayService.isHoliday();
        String holidayName = holidayService.getHolidayName();
        List<ProductEntity> products = productRepository.findAll();

        log.info("🧐 Đang xử lý giá cho {} sản phẩm. Hôm nay là: {}", 
                products.size(), isHoliday ? holidayName : "Ngày thường");

        boolean changed = false;
        for (ProductEntity p : products) {
            if (isHoliday) {
                // Nếu là ngày lễ mà chưa áp dụng sale
                if (p.getIsOnSale() == null || !p.getIsOnSale()) {
                    p.setOriginalPrice(p.getPrice()); // Giữ lại giá gốc
                    BigDecimal discountedPrice = p.getPrice()
                            .multiply(BigDecimal.valueOf(0.7))
                            .setScale(0, RoundingMode.HALF_UP);
                    p.setPrice(discountedPrice);
                    p.setIsOnSale(true);
                    p.setDiscountPercentage(30);
                    changed = true;
                }
            } else {
                // Nếu là ngày thường mà vẫn đang để giá sale
                if (p.getIsOnSale() != null && p.getIsOnSale()) {
                    if (p.getOriginalPrice() != null) {
                        p.setPrice(p.getOriginalPrice());
                    }
                    p.setIsOnSale(false);
                    p.setDiscountPercentage(0);
                    changed = true;
                }
            }
        }

        if (changed) {
            productRepository.saveAll(products);
            log.info("✅ Đã cập nhật giá mới vào CSDL cho ngày {}", isHoliday ? holidayName : "thường");
        } else {
            log.info("😴 Không có thay đổi nào về giá cần cập nhật.");
        }
    }
}
