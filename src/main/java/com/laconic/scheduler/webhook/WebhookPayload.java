package com.laconic.scheduler.webhook;

import java.util.Collections;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/**
 * Parsed WEBHOOK action payload.
 *
 * <pre>
 * {
 *   "url": "https://example.com/hook",
 *   "method": "POST",
 *   "headers": { "X-Custom": "1" },
 *   "body": { "hello": "world" }
 * }
 * </pre>
 */
public record WebhookPayload(
        String url, String method, Map<String, String> headers, JsonNode body) {

    public WebhookPayload {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("webhook url is required");
        }
        method = (method == null || method.isBlank()) ? "POST" : method.trim().toUpperCase();
        headers = headers == null ? Collections.emptyMap() : Map.copyOf(headers);
    }
}
