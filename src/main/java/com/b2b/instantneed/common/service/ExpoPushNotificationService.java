package com.b2b.instantneed.common.service;

import com.b2b.instantneed.order.dto.OrderResponse;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class ExpoPushNotificationService {

    @Async
    public void sendOrderStatusUpdate(String fcmToken, OrderResponse order) {
        if (fcmToken == null || fcmToken.isBlank()) return;
        if (FirebaseApp.getApps().isEmpty()) return; // FCM not configured

        String title = "Order #" + order.orderNumber() + " Update";
        String body = statusMessage(order.status());

        try {
            Message message = Message.builder()
                    .setToken(fcmToken)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .setNotification(AndroidNotification.builder()
                                    .setSound("default")
                                    .setColor("#1D4ED8")
                                    .build())
                            .build())
                    .setApnsConfig(ApnsConfig.builder()
                            .setAps(Aps.builder()
                                    .setSound("default")
                                    .build())
                            .build())
                    .putAllData(Map.of(
                            "orderId", order.id().toString(),
                            "orderNumber", order.orderNumber(),
                            "status", order.status()
                    ))
                    .build();

            String messageId = FirebaseMessaging.getInstance().send(message);
            log.debug("FCM sent for order {}: {}", order.orderNumber(), messageId);
        } catch (Exception e) {
            log.warn("FCM failed for order {}: {}", order.orderNumber(), e.getMessage());
        }
    }

    private String statusMessage(String status) {
        return switch (status.toUpperCase()) {
            case "CONFIRMED"  -> "Your order has been confirmed!";
            case "PROCESSING" -> "Your order is being processed.";
            case "SHIPPED"    -> "Your order has been shipped!";
            case "DELIVERED"  -> "Your order has been delivered.";
            case "CANCELLED"  -> "Your order has been cancelled.";
            default           -> "Your order status has been updated.";
        };
    }
}
