package com.example.PaymentService.service;

import com.example.PaymentService.config.VNPayConfig;
import com.example.PaymentService.util.VNPayUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final int    PAYMENT_EXPIRE_MINUTES = 15;
    private static final String DATE_FORMAT            = "yyyyMMddHHmmss";
    private static final String TIMEZONE_VN            = "Asia/Ho_Chi_Minh";
    private static final String CURRENCY               = "VND";
    private static final String ORDER_TYPE             = "other";
    private static final String LOCALE                 = "vn";

    private final VNPayConfig vnPayConfig;

    public String createPaymentUrl(HttpServletRequest request, long amount, String orderId) {
        String txnRef  = orderId + "_" + VNPayUtils.getRandomNumber(4);
        String ipAddr  = VNPayUtils.getIpAddress(request);
        if (ipAddr == null || ipAddr.contains(":") || ipAddr.equals("0:0:0:0:0:0:0:1")) {
            ipAddr = "127.0.0.1";
        }
        String[] dates = buildDateRange();

        Map<String, String> params = buildVnpayParams(amount, orderId, txnRef, ipAddr, dates);

        String[] queryAndHash = buildQueryAndHashData(params);
        String queryUrl   = queryAndHash[0];
        String hashData   = queryAndHash[1];
        String secureHash = VNPayUtils.hmacSHA512(vnPayConfig.getVnp_HashSecret(), hashData);

        return vnPayConfig.getVnp_PayUrl() + "?" + queryUrl + "&vnp_SecureHash=" + secureHash;
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /** Trả về [createDate, expireDate] theo format VNPay. */
    private String[] buildDateRange() {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone(TIMEZONE_VN));
        SimpleDateFormat fmt = new SimpleDateFormat(DATE_FORMAT);
        String createDate = fmt.format(cal.getTime());
        cal.add(Calendar.MINUTE, PAYMENT_EXPIRE_MINUTES);
        String expireDate = fmt.format(cal.getTime());
        return new String[]{createDate, expireDate};
    }

    /** Xây dựng map các tham số gửi lên VNPay. */
    private Map<String, String> buildVnpayParams(long amount, String orderId,
                                                  String txnRef, String ipAddr, String[] dates) {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_Version",    VNPayConfig.vnp_Version);
        params.put("vnp_Command",    VNPayConfig.vnp_Command);
        params.put("vnp_TmnCode",    vnPayConfig.getVnp_TmnCode());
        params.put("vnp_Amount",     String.valueOf(amount * 100)); // VNPay tính theo đơn vị nhỏ nhất (x100)
        params.put("vnp_CurrCode",   CURRENCY);
        params.put("vnp_TxnRef",     txnRef);
        params.put("vnp_OrderInfo",  "Thanh toan don hang: " + orderId);
        params.put("vnp_OrderType",  ORDER_TYPE);
        params.put("vnp_Locale",     LOCALE);
        params.put("vnp_ReturnUrl",  vnPayConfig.getVnp_ReturnUrl());
        params.put("vnp_IpAddr",     ipAddr);
        params.put("vnp_CreateDate", dates[0]);
        params.put("vnp_ExpireDate", dates[1]);
        return params;
    }

    /**
     * Sắp xếp params theo alphabet, xây dựng chuỗi query (URL-encoded)
     * và chuỗi hashData (raw value, không encode — theo đúng chuẩn VNPay).
     *
     * @return [queryString, hashData]
     */
    private String[] buildQueryAndHashData(Map<String, String> params) {
        List<String> fieldNames = new ArrayList<>(params.keySet());
        Collections.sort(fieldNames);

        StringBuilder query    = new StringBuilder();
        StringBuilder hashData = new StringBuilder();
        Iterator<String> itr = fieldNames.iterator();

        while (itr.hasNext()) {
            String key   = itr.next();
            String value = params.get(key);
            if (value == null || value.isEmpty()) continue;

            // Xây dựng chuỗi Query (để truyền đi) và HashData (để tính chữ ký) 
            // Cả hai đều cần được URL-Encode theo chuẩn VNPay 2.1.0
            String encodedKey = URLEncoder.encode(key, StandardCharsets.US_ASCII);
            String encodedValue = URLEncoder.encode(value, StandardCharsets.US_ASCII);

            query.append(encodedKey).append('=').append(encodedValue);
            hashData.append(encodedKey).append('=').append(encodedValue);

            if (itr.hasNext()) {
                query.append('&');
                hashData.append('&');
            }
        }
        return new String[]{query.toString(), hashData.toString()};
    }
}
