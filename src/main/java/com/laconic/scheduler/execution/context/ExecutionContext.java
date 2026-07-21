package com.laconic.scheduler.execution.context;

import com.laconic.scheduler.scheduler.domain.ActionType;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable bag of facts passed to every {@code TaskExecutor}.
 * Executors must not receive ad-hoc primitive argument lists.
 */
public record ExecutionContext(
        String scheduleId,
        String executionId,
        String actionId,
        ActionType actionType,
        Instant scheduledTime,
        Instant triggeredTime,
        int retryCount,
        String payloadJson,
        String metadataJson) {

    public ExecutionContext {
        Objects.requireNonNull(scheduleId, "scheduleId");
        Objects.requireNonNull(executionId, "executionId");
        Objects.requireNonNull(actionId, "actionId");
        Objects.requireNonNull(actionType, "actionType");
        Objects.requireNonNull(scheduledTime, "scheduledTime");
        Objects.requireNonNull(triggeredTime, "triggeredTime");
        Objects.requireNonNull(payloadJson, "payloadJson");
        if (retryCount < 0) {
            throw new IllegalArgumentException("retryCount must be >= 0");
        }
    }
}
