package com.example.CouponService.entity;

public enum CouponUsageStatus {
    USED,      // Mã đang được tính cho đơn hàng
    REVERSED   // Đã hoàn lại lượt (đơn bị hủy / trả hàng / tạo đơn thất bại)
}
