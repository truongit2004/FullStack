package com.example.User.Service.dto;

import com.example.User.Service.dto.external.OrderResponseDTO;
import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class UserProfileResponse {
    private UserDto userInfo;
    private List<OrderResponseDTO> orderHistory;
}
