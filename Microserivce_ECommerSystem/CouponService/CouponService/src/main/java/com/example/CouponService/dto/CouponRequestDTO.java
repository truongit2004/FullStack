package com.example.CouponService.dto;

import com.example.CouponService.entity.CouponType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Dữ liệu ADMIN gửi lên khi tạo / sửa mã giảm giá. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponRequestDTO {

    @NotBlank(message = "Mã không được để trống")
    @Pattern(regexp = "^[A-Za-z0-9_-]{3,30}$", message = "Mã chỉ gồm chữ, số, gạch dưới, gạch ngang; dài 3-30 ký tự")
    private String code;

    private String description;

    @NotNull(message = "Phải chọn kiểu giảm (PERCENT hoặc FIXED)")
    private CouponType type;

    @NotNull(message = "Giá trị giảm không được để trống")
    @DecimalMin(value = "0.01", message = "Giá trị giảm phải lớn hơn 0")
    private BigDecimal value;

    @DecimalMin(value = "0.01", message = "Mức giảm tối đa phải lớn hơn 0")
    private BigDecimal maxDiscount;

    @DecimalMin(value = "0", message = "Đơn tối thiểu không được âm")
    private BigDecimal minOrderAmount;

    private LocalDateTime startAt;
    private LocalDateTime endAt;

    @Min(value = 1, message = "Tổng lượt dùng phải từ 1 trở lên")
    private Integer usageLimit;

    @Min(value = 1, message = "Số lần mỗi khách phải từ 1 trở lên")
    private Integer perUserLimit;

    private Boolean active;
}
