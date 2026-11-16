package com.civilmate.api.auth.dto;

import java.time.Instant;

public record ApiEnvelope<T>(boolean success, String message, T data, Instant timestamp) {
    public static <T> ApiEnvelope<T> success(String message, T data) {
        return new ApiEnvelope<>(true, message, data, Instant.now());
    }
}
