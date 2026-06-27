package com.example.NotificationService.service;

import com.example.NotificationService.dto.NotificationEvent;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    public void sendOrderPlacedEmail(NotificationEvent event) throws MessagingException {
        sendEmail(event, "order-placed", "Xác nhận đặt hàng thành công #" + event.getOrderId());
    }

    public void sendPaymentSuccessEmail(NotificationEvent event) throws MessagingException {
        sendEmail(event, "payment-success", "Thông báo thanh toán đơn hàng #" + event.getOrderId());
    }

    public void sendOrderReturnedEmail(NotificationEvent event) throws MessagingException {
        sendEmail(event, "order-returned", "Thông báo hoàn trả tiền đơn hàng #" + event.getOrderId());
    }

    private void sendEmail(NotificationEvent event, String templateName, String subject) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        Context context = new Context();
        context.setVariable("name", event.getCustomerName());
        context.setVariable("orderId", event.getOrderId());
        context.setVariable("amount", event.getAmount());

        String html = templateEngine.process(templateName, context);

        helper.setTo(event.getEmail());
        helper.setSubject(subject);
        helper.setText(html, true);

        mailSender.send(message);
        log.info("Email sent successfully to {} with template {}", event.getEmail(), templateName);
    }
}
