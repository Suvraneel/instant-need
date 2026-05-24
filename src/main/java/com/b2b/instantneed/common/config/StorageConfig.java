package com.b2b.instantneed.common.config;

import com.b2b.instantneed.common.storage.LocalStorageService;
import com.b2b.instantneed.common.storage.StorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class StorageConfig {

    @Value("${app.storage.upload-dir:./uploads}")
    private String uploadDir;

    @Value("${app.storage.base-url:http://localhost:8080/uploads}")
    private String baseUrl;

    @Bean
    public StorageService storageService() {
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        return new LocalStorageService(root, baseUrl);
    }
}
