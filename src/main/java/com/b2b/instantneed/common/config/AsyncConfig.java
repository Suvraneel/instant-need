package com.b2b.instantneed.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables Spring's @Async support so that EmailService methods run
 * on a background thread and never block the HTTP request thread.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
