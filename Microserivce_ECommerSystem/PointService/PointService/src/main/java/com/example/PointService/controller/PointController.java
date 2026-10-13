package com.example.PointService.controller;

import com.example.PointService.dto.*;
import com.example.PointService.service.PointService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/points")
@RequiredArgsConstructor
public class PointController {

    private final PointService pointService;

    @GetMapping("/config")
    public ResponseEntity<PointConfigResponseDTO> getConfig() {
        return ResponseEntity.ok(pointService.getConfig());
    }

    @GetMapping("/{userId}")
    public ResponseEntity<PointBalanceResponseDTO> getBalance(@PathVariable String userId) {
        return ResponseEntity.ok(pointService.getBalance(userId));
    }

    @GetMapping("/{userId}/history")
    public ResponseEntity<List<PointTransactionResponseDTO>> getHistory(@PathVariable String userId) {
        return ResponseEntity.ok(pointService.getHistory(userId));
    }

    @PostMapping("/earn")
    public ResponseEntity<PointEarnResponseDTO> earn(@Valid @RequestBody PointEarnRequestDTO request) {
        return ResponseEntity.ok(pointService.earn(request));
    }

    @PostMapping("/redeem")
    public ResponseEntity<PointRedeemResponseDTO> redeem(@Valid @RequestBody PointRedeemRequestDTO request) {
        return ResponseEntity.ok(pointService.redeem(request));
    }

    @PostMapping("/{orderId}/reverse")
    public ResponseEntity<Void> reverse(@PathVariable String orderId) {
        pointService.reverseForOrder(orderId);
        return ResponseEntity.ok().build();
    }
}
