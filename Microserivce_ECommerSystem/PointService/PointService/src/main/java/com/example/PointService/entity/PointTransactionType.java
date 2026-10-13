package com.example.PointService.entity;

public enum PointTransactionType {
    EARN,           // Cộng điểm khi đơn hàng DELIVERED (10% giá trị đơn)
    REDEEM,         // Trừ điểm khi dùng để giảm giá đơn tiếp theo
    REVERSE_EARN,   // Thu hồi điểm đã cộng (đơn bị hủy/trả hàng sau khi đã DELIVERED)
    REVERSE_REDEEM  // Hoàn lại điểm đã dùng (đơn dùng điểm bị hủy/trả hàng)
}
