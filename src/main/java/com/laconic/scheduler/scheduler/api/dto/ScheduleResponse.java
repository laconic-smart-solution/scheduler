package com.laconic.scheduler.scheduler.api.dto;

import com.laconic.scheduler.scheduler.domain.Action;
import com.laconic.scheduler.scheduler.domain.ActionType;
import com.laconic.scheduler.scheduler.domain.Schedule;
import com.laconic.scheduler.scheduler.domain.ScheduleKind;
import com.laconic.scheduler.scheduler.domain.ScheduleStatus;
import java.time.Instant;

/**
 * Schedule + action view returned by the API.
 */
public record ScheduleResponse(
        String id,
        String actionId,
        ScheduleStatus status,
        ScheduleKind scheduleKind,
        Instant runAt,
        String cronExpression,
        String timeZone,
        Instant nextExecutionAt,
        int attemptCount,
        int maxAttempts,
        String idempotencyKey,
        Instant createdAt,
        Instant updatedAt,
        ActionResponse action) {

    public record ActionResponse(
            String id, ActionType type, String payloadJson, String metadataJson) {

        public static ActionResponse from(Action action) {
            return new ActionResponse(
                    action.getId(),
                    action.getType(),
                    action.getPayloadJson(),
                    action.getMetadataJson());
        }
    }

    public static ScheduleResponse from(Schedule schedule, Action action) {
        return new ScheduleResponse(
                schedule.getId(),
                schedule.getActionId(),
                schedule.getStatus(),
                schedule.getKind(),
                schedule.getRunAt(),
                schedule.getCronExpression(),
                schedule.getTimeZone(),
                schedule.getNextExecutionAt(),
                schedule.getAttemptCount(),
                schedule.getMaxAttempts(),
                schedule.getIdempotencyKey(),
                schedule.getCreatedAt(),
                schedule.getUpdatedAt(),
                ActionResponse.from(action));
    }
}
