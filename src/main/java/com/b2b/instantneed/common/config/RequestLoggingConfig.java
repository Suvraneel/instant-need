package com.b2b.instantneed.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

/**
 * Configures Spring's built-in CommonsRequestLoggingFilter so every inbound
 * HTTP request is logged at DEBUG level, including:
 *   - HTTP method + URI
 *   - Query string
 *   - Client IP
 *   - Payload body (up to 2 KB — enough to see what was sent without flooding)
 *
 * Visibility is controlled in log4j2.xml via the
 * org.springframework.web.filter.CommonsRequestLoggingFilter logger.
 */
@Configuration
public class RequestLoggingConfig {

    @Bean
    public CommonsRequestLoggingFilter requestLoggingFilter() {
        CommonsRequestLoggingFilter filter = new CommonsRequestLoggingFilter();
        filter.setIncludeQueryString(true);
        filter.setIncludeClientInfo(true);
        filter.setIncludeHeaders(false);       // keep logs clean — enable if debugging auth issues
        filter.setIncludePayload(true);
        filter.setMaxPayloadLength(2048);
        filter.setAfterMessagePrefix("REQUEST  : ");
        filter.setBeforeMessagePrefix("BEFORE   : ");
        return filter;
    }
}
