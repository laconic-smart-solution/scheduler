package com.laconic.scheduler.webhook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.laconic.scheduler.execution.context.ExecutionContext;
import com.laconic.scheduler.executor.TaskExecutionException;
import com.laconic.scheduler.scheduler.domain.ActionType;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class WebhookTaskExecutorTest {

    private MockWebServer server;
    private WebhookTaskExecutor executor;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        ObjectMapper mapper = JsonMapper.builder().build();
        executor =
                new WebhookTaskExecutor(
                        WebClient.builder().build(),
                        new WebhookPayloadParser(mapper),
                        mapper,
                        5_000L);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void postsJsonBodyAndSchedulerHeaders() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true}"));

        String url = server.url("/hook").toString();
        String payload =
                """
                {"url":"%s","method":"POST","headers":{"X-Custom":"c"},"body":{"hello":"world"}}
                """
                        .formatted(url);

        executor.execute(context(payload));

        RecordedRequest request = server.takeRequest(2, TimeUnit.SECONDS);
        assertEquals("POST", request.getMethod());
        assertEquals("/hook", request.getPath());
        assertEquals("s1", request.getHeader(WebhookTaskExecutor.HEADER_SCHEDULE_ID));
        assertEquals("e1", request.getHeader(WebhookTaskExecutor.HEADER_EXECUTION_ID));
        assertEquals("c", request.getHeader("X-Custom"));
        assertTrue(request.getBody().readUtf8().contains("hello"));
    }

    @Test
    void non2xxBecomesTaskExecutionException() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("fail"));
        String url = server.url("/hook").toString();

        TaskExecutionException ex =
                assertThrows(
                        TaskExecutionException.class,
                        () -> executor.execute(context("{\"url\":\"" + url + "\"}")));
        assertEquals("HTTP_500", ex.getErrorCode());
    }

    private static ExecutionContext context(String payloadJson) {
        Instant now = Instant.parse("2026-07-21T10:00:00Z");
        return new ExecutionContext(
                "s1", "e1", "a1", ActionType.WEBHOOK, now, now, 0, payloadJson, null);
    }
}
