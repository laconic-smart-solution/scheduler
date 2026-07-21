package com.laconic.scheduler.webhook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.laconic.scheduler.executor.TaskExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class WebhookPayloadParserTest {

    private WebhookPayloadParser parser;

    @BeforeEach
    void setUp() {
        parser = new WebhookPayloadParser(JsonMapper.builder().build());
    }

    @Test
    void parsesMinimalPayload() {
        WebhookPayload payload = parser.parse("{\"url\":\"https://example.com/h\"}");
        assertEquals("https://example.com/h", payload.url());
        assertEquals("POST", payload.method());
        assertTrue(payload.headers().isEmpty());
    }

    @Test
    void parsesFullPayload() {
        WebhookPayload payload =
                parser.parse(
                        """
                        {
                          "url": "https://example.com/h",
                          "method": "put",
                          "headers": { "X-A": "1" },
                          "body": { "k": "v" }
                        }
                        """);
        assertEquals("PUT", payload.method());
        assertEquals("1", payload.headers().get("X-A"));
        assertEquals("v", payload.body().get("k").asString());
    }

    @Test
    void rejectsMissingUrl() {
        TaskExecutionException ex =
                assertThrows(TaskExecutionException.class, () -> parser.parse("{\"method\":\"POST\"}"));
        assertEquals("INVALID_PAYLOAD", ex.getErrorCode());
    }

    @Test
    void rejectsBlank() {
        TaskExecutionException ex =
                assertThrows(TaskExecutionException.class, () -> parser.parse("  "));
        assertEquals("EMPTY_PAYLOAD", ex.getErrorCode());
    }
}
