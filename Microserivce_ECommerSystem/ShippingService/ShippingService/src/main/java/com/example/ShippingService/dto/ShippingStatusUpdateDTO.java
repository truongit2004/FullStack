package com.example.ShippingService.dto;

import com.example.ShippingService.entity.ShippingStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ShippingStatusUpdateDTO {
    
    @NotNull(message = "Status is required")
    private ShippingStatus status;
}
