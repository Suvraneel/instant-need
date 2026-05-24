package com.b2b.instantneed.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * Exposes the local uploads directory as a static resource under {@code /uploads/**}.
 * In production this path is replaced by a CDN — the Spring handler is never reached.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${app.storage.upload-dir:./uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String absoluteDir = Paths.get(uploadDir).toAbsolutePath().normalize().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + absoluteDir + "/");
    }
}
