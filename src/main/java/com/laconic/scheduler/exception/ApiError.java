package com.laconic.scheduler.exception;

import java.time.Instant;
import java.util.Map;

/**
 * Standard API error body.
 */
public record ApiError(
        Instant timestamp, int status, String error, String message, String path, Map<String, String> details) {

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path, Map.of());
    }

    public static ApiError of(
            int status, String error, String message, String path, Map<String, String> details) {
        return new ApiError(Instant.now(), status, error, message, path, details);
    }
}
