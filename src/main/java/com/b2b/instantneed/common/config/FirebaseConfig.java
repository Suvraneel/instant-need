package com.b2b.instantneed.common.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
@Configuration
public class FirebaseConfig {

    @Value("${app.fcm.enabled:false}")
    private boolean fcmEnabled;

    // Set FIREBASE_SERVICE_ACCOUNT_JSON to the full JSON string of your service account key,
    // OR set FIREBASE_SERVICE_ACCOUNT_PATH to the file path.
    @Value("${FIREBASE_SERVICE_ACCOUNT_JSON:}")
    private String serviceAccountJson;

    @Value("${FIREBASE_SERVICE_ACCOUNT_PATH:}")
    private String serviceAccountPath;

    @PostConstruct
    public void initialize() {
        if (!fcmEnabled) {
            log.info("FCM disabled (app.fcm.enabled=false) — push notifications are off");
            return;
        }
        if (!FirebaseApp.getApps().isEmpty()) return;

        try {
            InputStream stream;
            if (!serviceAccountJson.isBlank()) {
                stream = new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8));
            } else if (!serviceAccountPath.isBlank()) {
                stream = new FileInputStream(serviceAccountPath);
            } else {
                log.warn("FCM enabled but no service account configured. Set FIREBASE_SERVICE_ACCOUNT_JSON or FIREBASE_SERVICE_ACCOUNT_PATH.");
                return;
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(stream))
                    .build();
            FirebaseApp.initializeApp(options);
            log.info("Firebase Admin SDK initialized — FCM ready");
        } catch (Exception e) {
            log.error("Failed to initialize Firebase Admin SDK: {}", e.getMessage());
        }
    }
}
