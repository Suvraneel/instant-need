package com.b2b.instantneed.common.service;

import com.b2b.instantneed.order.dto.OrderResponse;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.time.ZoneId;

/**
 * Sends transactional emails (password reset, order confirmation, status updates).
 * <p>
 * When {@code app.mail.enabled=false} (the default in dev) every method logs the
 * email content instead of actually sending it, so the application starts cleanly
 * without SMTP credentials configured.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${app.mail.from:noreply@instantneed.com}")
    private String fromAddress;

    @Value("${app.mail.app-url:http://localhost:3000}")
    private String appUrl;

    // ── Password reset ────────────────────────────────────────────────────────

    @Async
    public void sendPasswordReset(String toEmail, String resetToken) {
        String resetUrl = appUrl + "/reset-password?token=" + resetToken;
        String subject  = "Reset your InstantNeed password";
        String html = """
                <div style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto">
                  <h2 style="color:#1a1a2e">Password Reset Request</h2>
                  <p>We received a request to reset the password for your InstantNeed account.</p>
                  <p>Click the button below to choose a new password. This link expires in <strong>1 hour</strong>.</p>
                  <p style="margin:32px 0">
                    <a href="%s"
                       style="background:#4f46e5;color:#fff;padding:12px 24px;border-radius:6px;
                              text-decoration:none;font-weight:600">
                      Reset Password
                    </a>
                  </p>
                  <p style="color:#6b7280;font-size:14px">
                    If you didn't request a password reset you can safely ignore this email.
                    Your password will not be changed.
                  </p>
                  <hr style="border:none;border-top:1px solid #e5e7eb;margin:24px 0"/>
                  <p style="color:#9ca3af;font-size:12px">
                    InstantNeed B2B Wholesale Platform
                  </p>
                </div>
                """.formatted(resetUrl);

        send(toEmail, subject, html);
    }

    // ── Order confirmation ────────────────────────────────────────────────────

    @Async
    public void sendOrderConfirmation(String toEmail, OrderResponse order) {
        String subject = "Order Confirmed — " + order.orderNumber();
        String html    = buildOrderEmail(order,
                "Your Order is Confirmed!",
                "Thank you for your order. We've received it and will begin processing shortly.");
        send(toEmail, subject, html);
    }

    // ── Order status update ───────────────────────────────────────────────────

    @Async
    public void sendOrderStatusUpdate(String toEmail, OrderResponse order) {
        String label   = humanStatus(order.status());
        String subject = "Order " + order.orderNumber() + " — " + label;
        String html    = buildOrderEmail(order,
                "Order Status Update: " + label,
                "Your order status has been updated to <strong>" + label + "</strong>.");
        send(toEmail, subject, html);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void send(String to, String subject, String html) {
        if (!mailEnabled) {
            log.info("[EMAIL DISABLED] To={} | Subject={} | (set MAIL_ENABLED=true to send for real)", to, subject);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("[EMAIL SENT] To={} | Subject={}", to, subject);
        } catch (Exception e) {
            log.error("[EMAIL FAILED] To={} | Subject={} | error={}", to, subject, e.getMessage());
            // Never throw — email failure must not break the transaction that triggered it
        }
    }

    private String buildOrderEmail(OrderResponse order, String heading, String intro) {
        String orderUrl = appUrl + "/account/orders/" + order.id();

        StringBuilder itemRows = new StringBuilder();
        if (order.items() != null) {
            for (var item : order.items()) {
                itemRows.append("""
                        <tr>
                          <td style="padding:8px;border-bottom:1px solid #e5e7eb">%s</td>
                          <td style="padding:8px;border-bottom:1px solid #e5e7eb;text-align:center">%d</td>
                          <td style="padding:8px;border-bottom:1px solid #e5e7eb;text-align:right">
                            %s %s
                          </td>
                        </tr>
                        """.formatted(
                        item.productName(),
                        item.quantity(),
                        item.currencyCode(),
                        item.lineTotal()
                ));
            }
        }

        String placedAt = order.placedAt() != null
                ? order.placedAt().atZone(ZoneId.of("UTC"))
                        .format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm 'UTC'"))
                : "";

        return """
                <div style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto">
                  <h2 style="color:#1a1a2e">%s</h2>
                  <p>%s</p>
                  <table style="width:100%%;border-collapse:collapse;margin:16px 0">
                    <tr style="background:#f9fafb">
                      <th style="padding:8px;text-align:left">Item</th>
                      <th style="padding:8px;text-align:center">Qty</th>
                      <th style="padding:8px;text-align:right">Total</th>
                    </tr>
                    %s
                  </table>
                  <p style="text-align:right;font-size:16px">
                    <strong>Order Total: %s %s</strong>
                  </p>
                  <p style="color:#6b7280;font-size:14px">
                    Order Number: <strong>%s</strong><br/>
                    Placed: %s
                  </p>
                  <p style="margin:24px 0">
                    <a href="%s"
                       style="background:#4f46e5;color:#fff;padding:12px 24px;border-radius:6px;
                              text-decoration:none;font-weight:600">
                      View Order
                    </a>
                  </p>
                  <hr style="border:none;border-top:1px solid #e5e7eb;margin:24px 0"/>
                  <p style="color:#9ca3af;font-size:12px">InstantNeed B2B Wholesale Platform</p>
                </div>
                """.formatted(
                heading, intro,
                itemRows,
                order.currencyCode(), order.totalAmount(),
                order.orderNumber(), placedAt,
                orderUrl
        );
    }

    private static String humanStatus(String status) {
        if (status == null) return "Updated";
        return switch (status.toUpperCase()) {
            case "PENDING"    -> "Pending";
            case "CONFIRMED"  -> "Confirmed";
            case "PROCESSING" -> "Processing";
            case "SHIPPED"    -> "Shipped";
            case "DELIVERED"  -> "Delivered";
            case "CANCELLED"  -> "Cancelled";
            default           -> status;
        };
    }
}
