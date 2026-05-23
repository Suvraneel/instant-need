package com.b2b.instantneed.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

// Matches PRD §17.8 error model: { "error": { "code": "...", "message": "...", "details": {...} } }
public record ErrorResponse(ErrorDetail error) {

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(new ErrorDetail(code, message, null));
    }

    public static ErrorResponse of(String code, String message, Map<String, Object> details) {
        return new ErrorResponse(new ErrorDetail(code, message, details));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorDetail(String code, String message, Map<String, Object> details) {}
}
