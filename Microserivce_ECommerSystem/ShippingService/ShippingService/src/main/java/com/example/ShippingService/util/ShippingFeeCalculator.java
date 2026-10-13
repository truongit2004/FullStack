package com.example.ShippingService.util;

import com.example.ShippingService.dto.ShippingFeeResponseDTO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Tính phí vận chuyển dựa trên từ khoá địa danh trong địa chỉ giao hàng.
 * Đây là quy tắc đơn giản hoá (không dùng API định vị/khoảng cách thật) để
 * thay thế mức phí cố định trước đây, phân theo khoảng cách tới kho hàng
 * giả định đặt tại TP. Hồ Chí Minh.
 */
@Component
public class ShippingFeeCalculator {

    private static final BigDecimal SAME_CITY_FEE = new BigDecimal("2.00");
    private static final BigDecimal NEARBY_FEE = new BigDecimal("3.50");
    private static final BigDecimal FAR_FEE = new BigDecimal("6.00");
    private static final BigDecimal DEFAULT_FEE = new BigDecimal("8.00");

    private static final List<String> SAME_CITY_KEYWORDS = List.of(
            "ho chi minh", "tp.hcm", "tphcm", "tp hcm", "sai gon", "hcmc"
    );

    private static final List<String> NEARBY_KEYWORDS = List.of(
            "dong nai", "binh duong", "long an", "tay ninh", "ba ria", "vung tau"
    );

    private static final List<String> KNOWN_PROVINCE_KEYWORDS = List.of(
            "ha noi", "da nang", "can tho", "hai phong", "hue", "nha trang",
            "khanh hoa", "quang ninh", "thanh hoa", "nghe an", "lam dong", "an giang"
    );

    public ShippingFeeResponseDTO calculate(String address) {
        String normalized = normalize(address);

        if (normalized.isBlank()) {
            return new ShippingFeeResponseDTO(DEFAULT_FEE, "UNKNOWN");
        }
        if (containsAny(normalized, SAME_CITY_KEYWORDS)) {
            return new ShippingFeeResponseDTO(SAME_CITY_FEE, "SAME_CITY");
        }
        if (containsAny(normalized, NEARBY_KEYWORDS)) {
            return new ShippingFeeResponseDTO(NEARBY_FEE, "NEARBY_PROVINCE");
        }
        if (containsAny(normalized, KNOWN_PROVINCE_KEYWORDS)) {
            return new ShippingFeeResponseDTO(FAR_FEE, "FAR_PROVINCE");
        }
        return new ShippingFeeResponseDTO(DEFAULT_FEE, "UNKNOWN");
    }

    private boolean containsAny(String normalizedAddress, List<String> keywords) {
        return keywords.stream().anyMatch(normalizedAddress::contains);
    }

    private String normalize(String address) {
        if (address == null) {
            return "";
        }
        String noAccents = Normalizer.normalize(address, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        noAccents = noAccents.replace('đ', 'd').replace('Đ', 'D');
        return Pattern.compile("\\s+").matcher(noAccents.toLowerCase().trim()).replaceAll(" ");
    }
}
