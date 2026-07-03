package com.b2b.instantneed.common.service;

import com.b2b.instantneed.order.dto.OrderResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpoPushNotificationService {

    private static final String EXPO_PUSH_URL = "https://exp.host/--/api/v2/push/send";
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private final ObjectMapper objectMapper;

    @Async
    public void sendOrderStatusUpdate(String pushToken, OrderResponse order) {
        if (pushToken == null || pushToken.isBlank()) return;

        String title = "Order #" + order.orderNumber() + " Update";
        String body = statusMessage(order.status());

        try {
            Map<String, Object> payload = Map.of(
                    "to", pushToken,
                    "title", title,
                    "body", body,
                    "sound", "default",
                    "priority", "high",
                    "channelId", "order-updates",
                    "data", Map.of(
                            "orderId", order.id().toString(),
                            "orderNumber", order.orderNumber(),
                            "status", order.status()
                    )
            );

            String json = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(EXPO_PUSH_URL))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            log.debug("Expo push sent for order {}: status={}", order.orderNumber(), response.statusCode());
        } catch (Exception e) {
            log.warn("Expo push failed for order {}: {}", order.orderNumber(), e.getMessage());
        }
    }

    private String statusMessage(String status) {
        return switch (status.toUpperCase()) {
            case "CONFIRMED"  -> "Your order has been confirmed! 🎉";
            case "PROCESSING" -> "Your order is being processed.";
            case "SHIPPED"    -> "Your order has been shipped! 🚚";
            case "DELIVERED"  -> "Your order has been delivered. ✅";
            case "CANCELLED"  -> "Your order has been cancelled.";
            default           -> "Your order status has been updated.";
        };
    }
}
