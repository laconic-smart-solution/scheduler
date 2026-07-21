package com.laconic.scheduler.webhook;

import com.laconic.scheduler.executor.TaskExecutionException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Parses {@code Action.payloadJson} into a {@link WebhookPayload}.
 */
@Component
public class WebhookPayloadParser {

    private final ObjectMapper objectMapper;

    public WebhookPayloadParser(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    public WebhookPayload parse(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            throw new TaskExecutionException("EMPTY_PAYLOAD", "Webhook payloadJson is blank");
        }
        try {
            JsonNode root = objectMapper.readTree(payloadJson);
            String url = text(root, "url");
            if (url == null || url.isBlank()) {
                throw new TaskExecutionException("INVALID_PAYLOAD", "Webhook payload requires url");
            }
            String method = text(root, "method");
            Map<String, String> headers = readHeaders(root.get("headers"));
            JsonNode body = root.get("body");
            return new WebhookPayload(url, method, headers, body);
        } catch (TaskExecutionException ex) {
            throw ex;
        } catch (JacksonException | IllegalArgumentException ex) {
            throw new TaskExecutionException(
                    "INVALID_PAYLOAD", "Cannot parse webhook payload: " + ex.getMessage(), ex);
        }
    }

    private static String text(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node == null || node.isNull() ? null : node.asText();
    }

    private static Map<String, String> readHeaders(JsonNode headersNode) {
        if (headersNode == null || headersNode.isNull() || !headersNode.isObject()) {
            return Map.of();
        }
        Map<String, String> headers = new HashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = headersNode.properties().iterator();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if (entry.getValue() != null && !entry.getValue().isNull()) {
                headers.put(entry.getKey(), entry.getValue().asText());
            }
        }
        return headers;
    }
}
