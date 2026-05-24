package com.b2b.instantneed.common.config;

import com.b2b.instantneed.common.storage.LocalStorageService;
import com.b2b.instantneed.common.storage.S3StorageService;
import com.b2b.instantneed.common.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Configuration
public class StorageConfig {

    @Value("${app.storage.type:local}")
    private String storageType;

    @Value("${app.storage.upload-dir:./uploads}")
    private String uploadDir;

    @Value("${app.storage.base-url:http://localhost:8080/uploads}")
    private String baseUrl;

    @Value("${app.storage.s3.bucket:}")
    private String s3Bucket;

    @Value("${app.storage.s3.region:ap-northeast-2}")
    private String s3Region;

    @Bean
    public StorageService storageService() {
        if ("s3".equalsIgnoreCase(storageType)) {
            log.info("[STORAGE] Using S3 backend — bucket={}, region={}", s3Bucket, s3Region);
            S3Client client = S3Client.builder()
                    .region(Region.of(s3Region))
                    .build();
            return new S3StorageService(client, s3Bucket, baseUrl);
        }

        log.info("[STORAGE] Using local filesystem backend — uploadDir={}", uploadDir);
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        return new LocalStorageService(root, baseUrl);
    }
}
