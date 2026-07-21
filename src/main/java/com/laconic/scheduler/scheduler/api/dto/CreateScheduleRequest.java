package com.laconic.scheduler.scheduler.api.dto;

import com.laconic.scheduler.scheduler.domain.ActionType;
import com.laconic.scheduler.scheduler.domain.ScheduleKind;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * Request to create an action + schedule.
 */
public record CreateScheduleRequest(
        String idempotencyKey,
        @Min(1) Integer maxAttempts,
        @NotNull ScheduleKind scheduleKind,
        Instant runAt,
        String cronExpression,
        String timeZone,
        Instant firstExecutionAt,
        @NotNull @Valid ActionRequest action) {

    public record ActionRequest(
            @NotNull ActionType type,
            @NotNull String payloadJson,
            String metadataJson) {}
}
