package com.laconic.scheduler.scheduler.service;

import com.laconic.scheduler.exception.ConflictException;
import com.laconic.scheduler.exception.ResourceNotFoundException;
import com.laconic.scheduler.scheduler.api.dto.CreateScheduleRequest;
import com.laconic.scheduler.scheduler.api.dto.ScheduleResponse;
import com.laconic.scheduler.scheduler.domain.Action;
import com.laconic.scheduler.scheduler.domain.ActionType;
import com.laconic.scheduler.scheduler.domain.Schedule;
import com.laconic.scheduler.scheduler.domain.ScheduleKind;
import com.laconic.scheduler.scheduler.persistence.ActionRepository;
import com.laconic.scheduler.scheduler.persistence.ScheduleRepository;
import com.laconic.scheduler.webhook.WebhookPayloadParser;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for schedule CRUD / cancel (WHEN + WHAT creation).
 */
@Service
public class ScheduleApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleApplicationService.class);
    private static final int DEFAULT_MAX_ATTEMPTS = 3;

    private final ActionRepository actionRepository;
    private final ScheduleRepository scheduleRepository;
    private final WebhookPayloadParser webhookPayloadParser;

    public ScheduleApplicationService(
            ActionRepository actionRepository,
            ScheduleRepository scheduleRepository,
            WebhookPayloadParser webhookPayloadParser) {
        this.actionRepository = Objects.requireNonNull(actionRepository);
        this.scheduleRepository = Objects.requireNonNull(scheduleRepository);
        this.webhookPayloadParser = Objects.requireNonNull(webhookPayloadParser);
    }

    /**
     * Creates action + schedule, or returns the existing schedule when {@code idempotencyKey} matches.
     *
     * @return result including whether this call created a new row
     */
    @Transactional
    public CreateResult create(CreateScheduleRequest request) {
        Objects.requireNonNull(request, "request");

        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            Optional<Schedule> existing =
                    scheduleRepository.findByIdempotencyKey(request.idempotencyKey().trim());
            if (existing.isPresent()) {
                Schedule schedule = existing.get();
                Action action = requireAction(schedule.getActionId());
                log.info(
                        "Idempotent create hit key={} scheduleId={}",
                        request.idempotencyKey(),
                        schedule.getId());
                return new CreateResult(ScheduleResponse.from(schedule, action), false);
            }
        }

        validateActionPayload(request.action().type(), request.action().payloadJson());

        Action action =
                new Action(
                        request.action().type(),
                        request.action().payloadJson(),
                        request.action().metadataJson());
        actionRepository.save(action);

        int maxAttempts =
                request.maxAttempts() == null ? DEFAULT_MAX_ATTEMPTS : request.maxAttempts();
        String idempotencyKey =
                request.idempotencyKey() == null || request.idempotencyKey().isBlank()
                        ? null
                        : request.idempotencyKey().trim();

        Schedule schedule = buildSchedule(request, action.getId(), idempotencyKey, maxAttempts);
        schedule.markScheduled();
        scheduleRepository.save(schedule);

        log.info(
                "Created scheduleId={} actionId={} kind={}",
                schedule.getId(),
                action.getId(),
                schedule.getKind());
        return new CreateResult(ScheduleResponse.from(schedule, action), true);
    }

    @Transactional(readOnly = true)
    public ScheduleResponse get(String scheduleId) {
        Schedule schedule = requireSchedule(scheduleId);
        Action action = requireAction(schedule.getActionId());
        return ScheduleResponse.from(schedule, action);
    }

    @Transactional
    public ScheduleResponse cancel(String scheduleId) {
        Schedule schedule = requireSchedule(scheduleId);
        try {
            schedule.markCancelled();
        } catch (IllegalStateException ex) {
            throw new ConflictException(ex.getMessage());
        }
        scheduleRepository.save(schedule);
        Action action = requireAction(schedule.getActionId());
        log.info("Cancelled scheduleId={}", scheduleId);
        return ScheduleResponse.from(schedule, action);
    }

    private Schedule buildSchedule(
            CreateScheduleRequest request,
            String actionId,
            String idempotencyKey,
            int maxAttempts) {
        if (request.scheduleKind() == ScheduleKind.ONCE_AT) {
            if (request.runAt() == null) {
                throw new IllegalArgumentException("runAt is required for ONCE_AT schedules");
            }
            return Schedule.onceAt(actionId, request.runAt(), idempotencyKey, maxAttempts);
        }
        if (request.scheduleKind() == ScheduleKind.CRON) {
            if (request.cronExpression() == null || request.cronExpression().isBlank()) {
                throw new IllegalArgumentException("cronExpression is required for CRON schedules");
            }
            String zone =
                    request.timeZone() == null || request.timeZone().isBlank()
                            ? "UTC"
                            : request.timeZone().trim();
            Instant first =
                    request.firstExecutionAt() != null
                            ? request.firstExecutionAt()
                            : Instant.now();
            return Schedule.cron(
                    actionId,
                    request.cronExpression().trim(),
                    zone,
                    first,
                    idempotencyKey,
                    maxAttempts);
        }
        throw new IllegalArgumentException("Unsupported scheduleKind: " + request.scheduleKind());
    }

    private void validateActionPayload(ActionType type, String payloadJson) {
        if (type == ActionType.WEBHOOK) {
            webhookPayloadParser.parse(payloadJson);
        }
    }

    private Schedule requireSchedule(String scheduleId) {
        return scheduleRepository
                .findById(scheduleId)
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "Schedule not found: " + scheduleId));
    }

    private Action requireAction(String actionId) {
        return actionRepository
                .findById(actionId)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Action not found: " + actionId));
    }

    public record CreateResult(ScheduleResponse body, boolean created) {}
}
