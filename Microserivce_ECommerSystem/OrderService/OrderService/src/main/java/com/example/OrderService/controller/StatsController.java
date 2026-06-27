package com.example.OrderService.controller;

import com.example.OrderService.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/orders/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getOverviewStats() {
        return ResponseEntity.ok(statsService.getOverviewStats());
    }

    @GetMapping("/revenue")
    public ResponseEntity<Map<String, Object>> getRevenue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate) {
        return ResponseEntity.ok(statsService.getRevenue(fromDate, toDate));
    }

    @GetMapping("/best-selling")
    public ResponseEntity<?> getBestSelling(
            @RequestParam(defaultValue = "5") int top) {
        return ResponseEntity.ok(statsService.getBestSellingProducts(top));
    }
}