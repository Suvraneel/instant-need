package com.b2b.instantneed.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * Exposes only public catalog images from local storage. Invoices are served
 * through authenticated API endpoints after an ownership check.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${app.storage.upload-dir:./uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String absoluteDir = Paths.get(uploadDir).toAbsolutePath().normalize().toString();
        registry.addResourceHandler("/uploads/products/**")
                .addResourceLocations("file:" + absoluteDir + "/products/");
        registry.addResourceHandler("/uploads/categories/**")
                .addResourceLocations("file:" + absoluteDir + "/categories/");
    }
}
