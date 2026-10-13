package com.example.PointService.service;

import com.example.PointService.dto.*;

import java.util.List;

public interface PointService {

    PointEarnResponseDTO earn(PointEarnRequestDTO request);

    PointRedeemResponseDTO redeem(PointRedeemRequestDTO request);

    void reverseForOrder(String orderId);

    PointConfigResponseDTO getConfig();

    PointBalanceResponseDTO getBalance(String userId);

    List<PointTransactionResponseDTO> getHistory(String userId);
}
