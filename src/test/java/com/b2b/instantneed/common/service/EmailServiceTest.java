package com.b2b.instantneed.common.service;

import com.b2b.instantneed.order.dto.OrderItemResponse;
import com.b2b.instantneed.order.dto.OrderResponse;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock JavaMailSender mailSender;

    @InjectMocks EmailService emailService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "fromAddress", "noreply@instantneed.com");
        ReflectionTestUtils.setField(emailService, "appUrl",      "http://localhost:3000");
    }

    // ── When mail is DISABLED (default dev mode) ──────────────────────────────

    @Test
    void sendPasswordReset_disabled_doesNotCallMailSender() {
        ReflectionTestUtils.setField(emailService, "mailEnabled", false);

        emailService.sendPasswordReset("user@test.com", "reset-token-123");

        verifyNoInteractions(mailSender);
    }

    @Test
    void sendOrderConfirmation_disabled_doesNotCallMailSender() {
        ReflectionTestUtils.setField(emailService, "mailEnabled", false);

        emailService.sendOrderConfirmation("user@test.com", sampleOrder());

        verifyNoInteractions(mailSender);
    }

    @Test
    void sendOrderStatusUpdate_disabled_doesNotCallMailSender() {
        ReflectionTestUtils.setField(emailService, "mailEnabled", false);

        emailService.sendOrderStatusUpdate("user@test.com", sampleOrder());

        verifyNoInteractions(mailSender);
    }

    // ── When mail is ENABLED ──────────────────────────────────────────────────

    @Test
    void sendPasswordReset_enabled_callsMailSender() throws MessagingException {
        ReflectionTestUtils.setField(emailService, "mailEnabled", true);

        MimeMessage mockMessage = mock(MimeMessage.class);
        given(mailSender.createMimeMessage()).willReturn(mockMessage);

        emailService.sendPasswordReset("user@test.com", "abc-token");

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendOrderConfirmation_enabled_callsMailSender() throws MessagingException {
        ReflectionTestUtils.setField(emailService, "mailEnabled", true);

        MimeMessage mockMessage = mock(MimeMessage.class);
        given(mailSender.createMimeMessage()).willReturn(mockMessage);

        emailService.sendOrderConfirmation("buyer@test.com", sampleOrder());

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendOrderStatusUpdate_enabled_callsMailSender() throws MessagingException {
        ReflectionTestUtils.setField(emailService, "mailEnabled", true);

        MimeMessage mockMessage = mock(MimeMessage.class);
        given(mailSender.createMimeMessage()).willReturn(mockMessage);

        emailService.sendOrderStatusUpdate("buyer@test.com", sampleOrder());

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendPasswordReset_enabled_mailSenderThrows_doesNotPropagateException() {
        ReflectionTestUtils.setField(emailService, "mailEnabled", true);

        MimeMessage mockMessage = mock(MimeMessage.class);
        given(mailSender.createMimeMessage()).willReturn(mockMessage);
        willThrow(new RuntimeException("SMTP connection refused"))
                .given(mailSender).send(any(MimeMessage.class));

        // Must never throw — email failure must not break the caller's transaction
        assertThatCode(() -> emailService.sendPasswordReset("user@test.com", "tok"))
                .doesNotThrowAnyException();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private OrderResponse sampleOrder() {
        OrderItemResponse item = new OrderItemResponse(
                UUID.randomUUID(), UUID.randomUUID(),
                "A4 Paper", "PAPER-A4",
                5, new BigDecimal("250.00"), new BigDecimal("1250.00"), "INR", null);

        return new OrderResponse(
                UUID.randomUUID(),
                "WB-20260524-0001",
                "CONFIRMED",
                "cod",
                new BigDecimal("1250.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("1250.00"),
                "INR",
                null,
                Instant.now(),
                null,
                null,
                List.of(item),
                "Raj Sharma",
                "Sharma Traders"
        );
    }
}
