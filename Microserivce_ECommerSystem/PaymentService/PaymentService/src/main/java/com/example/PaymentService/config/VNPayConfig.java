package com.example.PaymentService.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;

@Configuration
@Getter
public class VNPayConfig {
    @Value("${vnpay.url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}")
    private String vnp_PayUrl;

    @Value("${vnpay.returnUrl:http://localhost:8085/api/payment/vnpay-callback}")
    private String vnp_ReturnUrl;

    @Value("${vnpay.tmnCode:YQ8390LF}")
    private String vnp_TmnCode;

    @Value("${vnpay.hashSecret:56FZJSHHPFXCCXBXVHT9HL0J9W8RWG3T}")
    private String vnp_HashSecret;

    @Value("${vnpay.apiUrl:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}")
    private String vnp_ApiUrl;

    public static String vnp_Version = "2.1.0";
    public static String vnp_Command = "pay";
}
