package com.example.PaymentService.controller;

import com.example.PaymentService.client.OrderClient;
import com.example.PaymentService.entity.PaymentRecordEntity;
import com.example.PaymentService.repository.PaymentRecordRepository;
import com.example.PaymentService.service.PaymentService;
import com.example.PaymentService.config.VNPayConfig;
import com.example.PaymentService.util.VNPayUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final OrderClient orderClient;
    private final PaymentRecordRepository paymentRecordRepository;
    private final VNPayConfig vnPayConfig;
    private final org.springframework.kafka.core.KafkaTemplate<String, String> kafkaTemplate;

    @GetMapping("/create-vnpay-url")
    public ResponseEntity<Map<String, String>> createPayment(
            @RequestParam("amount") String amountStr,
            @RequestParam("orderId") String orderId,
            HttpServletRequest request) {

        long amount;
        try {
            // Loại bỏ các ký tự dư thừa như dấu chấm, dấu phẩy nếu có
            String cleanAmount = amountStr.split("\\.")[0].replaceAll("[^\\d]", "");
            amount = Long.parseLong(cleanAmount);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Số tiền không hợp lệ: " + amountStr);
            return ResponseEntity.badRequest().body(errorResponse);
        }

        // 1. Kiểm tra trạng thái đơn hàng trước khi cho phép thanh toán (Chống thanh
        // toán lại)
        try {
            Object orderObj = orderClient.getOrderById(orderId);
            if (orderObj instanceof java.util.Map) {
                java.util.Map<?, ?> orderMap = (java.util.Map<?, ?>) orderObj;
                Object statusObj = orderMap.get("status");
                if (statusObj != null) {
                    String status = statusObj.toString();
                    if ("PAID".equalsIgnoreCase(status) || "SHIPPING".equalsIgnoreCase(status)
                            || "DELIVERED".equalsIgnoreCase(status)) {
                        java.util.Map<String, String> errorResponse = new java.util.HashMap<>();
                        errorResponse.put("error",
                                "Đơn hàng này đã thanh toán hoặc đang giao, không thể thanh toán lại.");
                        return ResponseEntity.status(400).body(errorResponse);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("⚠️ Không tìm thấy đơn hàng {} trong CSDL hoặc OrderService lỗi. (Chi tiết: {})", orderId,
                    e.getMessage());
            // Bạn có thể chọn chặn luôn ở đây nếu muốn:
            // return ResponseEntity.status(404).body(Collections.singletonMap("error", "Đơn
            // hàng không tồn tại"));
        }

        String paymentUrl = paymentService.createPaymentUrl(request, amount, orderId);
        Map<String, String> response = new HashMap<>();
        response.put("url", paymentUrl);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/vnpay-callback")
    public ResponseEntity<String> vnpayCallback(HttpServletRequest request) {
        Map<String, String> fields = new HashMap<>();
        for (Enumeration<String> params = request.getParameterNames(); params.hasMoreElements();) {
            String fieldName = params.nextElement();
            String fieldValue = request.getParameter(fieldName);
            if ((fieldValue != null) && (fieldValue.length() > 0)) {
                fields.put(fieldName, fieldValue);
            }
        }

        String vnp_SecureHash = request.getParameter("vnp_SecureHash");
        if (vnp_SecureHash == null || vnp_SecureHash.isBlank()) {
            log.warn("VNPay callback received without secure hash.");
            return ResponseEntity.badRequest().body("Missing secure hash");
        }

        fields.remove("vnp_SecureHashType");
        fields.remove("vnp_SecureHash");

        List<String> fieldNames = new ArrayList<>(fields.keySet());
        Collections.sort(fieldNames);
        StringBuilder hashData = new StringBuilder();
        Iterator<String> itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String fieldName = itr.next();
            String fieldValue = fields.get(fieldName);
            if (fieldValue != null && !fieldValue.isEmpty()) {
                hashData.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII));
                hashData.append('=');
                hashData.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));
                if (itr.hasNext())
                    hashData.append('&');
            }
        }

        String computedHash = VNPayUtils.hmacSHA512(vnPayConfig.getVnp_HashSecret(), hashData.toString());
        if (!computedHash.equalsIgnoreCase(vnp_SecureHash)) {
            log.warn("VNPay callback: invalid signature.");
            return ResponseEntity.badRequest().body("Invalid signature");
        }

        String responseCode = request.getParameter("vnp_ResponseCode");
        String transactionId = request.getParameter("vnp_TransactionNo");
        String txnRef = fields.get("vnp_TxnRef");
        String orderId = (txnRef != null && txnRef.contains("_")) ? txnRef.split("_")[0] : txnRef;

        if ("00".equals(responseCode)) {
            processSuccessPayment(orderId, transactionId, fields.get("vnp_Amount"));
            // Redirect về trang Success của React (mặc định port 5173)
            return ResponseEntity.status(org.springframework.http.HttpStatus.FOUND)
                    .location(java.net.URI.create("http://localhost:5173/success?orderId=" + orderId))
                    .build();
        } else {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FOUND)
                    .location(java.net.URI.create("http://localhost:5173/orders?error=payment_failed"))
                    .build();
        }
    }

    // API mới để bạn test lưu dữ liệu ngay lập tức mà không cần qua VNPay
    @GetMapping("/mock-success")
    public ResponseEntity<String> mockSuccess(@RequestParam("orderId") String orderId,
            @RequestParam("amount") String amount) {
        log.info("MOCK: Giả lập thanh toán thành công cho đơn hàng: {}", orderId);
        processSuccessPayment(orderId, "MOCK-" + System.currentTimeMillis(),
                String.valueOf(Long.parseLong(amount) * 100));
        return ResponseEntity.ok("MOCK THÀNH CÔNG! Hãy kiểm tra CSDL và Order Service.");
    }

    private void processSuccessPayment(String orderId, String transactionId, String vnpAmount) {
        log.info("Processing success payment for order: {}", orderId);
        orderClient.markOrderAsPaid(orderId);

        BigDecimal finalAmount = vnpAmount != null ? new BigDecimal(vnpAmount).divide(new BigDecimal(100))
                : BigDecimal.ZERO;

        paymentRecordRepository.save(PaymentRecordEntity.builder()
                .orderId(orderId)
                .transactionId(transactionId)
                .amount(finalAmount)
                .paymentMethod("VNPAY_SIMULATED")
                .responseCode("00")
                .status("SUCCESS")
                .build());

        try {
            if (kafkaTemplate != null) {
                com.example.PaymentService.dto.NotificationEvent event = com.example.PaymentService.dto.NotificationEvent.builder()
                        .orderId(orderId)
                        .amount(vnpAmount)
                        .status("SUCCESS")
                        .type("PAYMENT_SUCCESS") // Thêm phân loại
                        .build();
                String jsonEvent = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(event);
                kafkaTemplate.send("payment-success-topic", jsonEvent);
                log.info("Sent payment success notification to Kafka");
            }
        } catch (Exception e) {
            log.error("Failed to send Kafka notification (Kafka might be disabled): {}", e.getMessage());
        }
    }
}
