package com.laconic.scheduler.webhook;

import com.laconic.scheduler.execution.context.ExecutionContext;
import com.laconic.scheduler.executor.TaskExecutionException;
import com.laconic.scheduler.executor.TaskExecutor;
import com.laconic.scheduler.scheduler.domain.ActionType;
import java.time.Duration;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Executes WEBHOOK actions over HTTP using {@link WebClient}.
 */
@Component
public class WebhookTaskExecutor implements TaskExecutor {

    private static final Logger log = LoggerFactory.getLogger(WebhookTaskExecutor.class);

    public static final String HEADER_SCHEDULE_ID = "X-Scheduler-Schedule-Id";
    public static final String HEADER_EXECUTION_ID = "X-Scheduler-Execution-Id";
    public static final String HEADER_ACTION_ID = "X-Scheduler-Action-Id";
    public static final String HEADER_RETRY_COUNT = "X-Scheduler-Retry-Count";

    private final WebClient webhookWebClient;
    private final WebhookPayloadParser payloadParser;
    private final ObjectMapper objectMapper;
    private final Duration responseTimeout;

    public WebhookTaskExecutor(
            WebClient webhookWebClient,
            WebhookPayloadParser payloadParser,
            ObjectMapper objectMapper,
            @Value("${scheduler.webhook.response-timeout-ms:10000}") long responseTimeoutMs) {
        this.webhookWebClient = Objects.requireNonNull(webhookWebClient);
        this.payloadParser = Objects.requireNonNull(payloadParser);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.responseTimeout = Duration.ofMillis(responseTimeoutMs);
    }

    @Override
    public ActionType supports() {
        return ActionType.WEBHOOK;
    }

    @Override
    public void execute(ExecutionContext context) throws TaskExecutionException {
        WebhookPayload payload = payloadParser.parse(context.payloadJson());
        HttpMethod method = resolveMethod(payload.method());

        log.info(
                "Webhook HTTP {} {} scheduleId={} executionId={} retry={}",
                method,
                payload.url(),
                context.scheduleId(),
                context.executionId(),
                context.retryCount());

        try {
            WebClient.RequestBodySpec request =
                    webhookWebClient
                            .method(method)
                            .uri(payload.url())
                            .header(HEADER_SCHEDULE_ID, context.scheduleId())
                            .header(HEADER_EXECUTION_ID, context.executionId())
                            .header(HEADER_ACTION_ID, context.actionId())
                            .header(HEADER_RETRY_COUNT, String.valueOf(context.retryCount()));

            payload.headers().forEach(request::header);

            String responseBody;
            if (hasBody(method) && payload.body() != null && !payload.body().isNull()) {
                String json = writeBody(payload.body());
                responseBody =
                        request.contentType(MediaType.APPLICATION_JSON)
                                .body(BodyInserters.fromValue(json))
                                .retrieve()
                                .bodyToMono(String.class)
                                .block(responseTimeout);
            } else {
                responseBody =
                        request.retrieve().bodyToMono(String.class).block(responseTimeout);
            }

            log.info(
                    "Webhook OK {} {} scheduleId={} responseLength={}",
                    method,
                    payload.url(),
                    context.scheduleId(),
                    responseBody == null ? 0 : responseBody.length());
        } catch (WebClientResponseException ex) {
            throw new TaskExecutionException(
                    "HTTP_" + ex.getStatusCode().value(),
                    "Webhook returned HTTP "
                            + ex.getStatusCode().value()
                            + ": "
                            + truncate(ex.getResponseBodyAsString()),
                    ex);
        } catch (WebClientRequestException ex) {
            throw new TaskExecutionException(
                    "HTTP_REQUEST_FAILED",
                    "Webhook request failed: " + ex.getMessage(),
                    ex);
        } catch (TaskExecutionException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new TaskExecutionException(
                    "HTTP_ERROR", "Webhook execution error: " + ex.getMessage(), ex);
        }
    }

    private static HttpMethod resolveMethod(String method) {
        try {
            return HttpMethod.valueOf(method);
        } catch (IllegalArgumentException ex) {
            throw new TaskExecutionException(
                    "INVALID_METHOD", "Unsupported HTTP method: " + method, ex);
        }
    }

    private static boolean hasBody(HttpMethod method) {
        return method == HttpMethod.POST
                || method == HttpMethod.PUT
                || method == HttpMethod.PATCH;
    }

    private String writeBody(JsonNode body) {
        try {
            if (body.isString()) {
                return body.asString();
            }
            return objectMapper.writeValueAsString(body);
        } catch (Exception ex) {
            throw new TaskExecutionException(
                    "INVALID_BODY", "Cannot serialize webhook body: " + ex.getMessage(), ex);
        }
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 500 ? value : value.substring(0, 500) + "...";
    }
}
