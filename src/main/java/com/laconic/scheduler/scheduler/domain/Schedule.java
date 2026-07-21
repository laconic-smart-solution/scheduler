package com.laconic.scheduler.scheduler.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Defines <strong>WHEN</strong> an {@link Action} should run.
 * Does not know how webhook/email/Kafka execution works.
 */
@Entity
@Table(name = "SCHEDULER_SCHEDULE")
public class Schedule {

    private static final Set<ScheduleStatus> TERMINAL =
            EnumSet.of(ScheduleStatus.DEAD, ScheduleStatus.CANCELLED);

    @Id
    @Column(name = "ID", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "ACTION_ID", length = 36, nullable = false, updatable = false)
    private String actionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 32, nullable = false)
    private ScheduleStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "SCHEDULE_KIND", length = 32, nullable = false, updatable = false)
    private ScheduleKind kind;

    @Column(name = "RUN_AT")
    private Instant runAt;

    @Column(name = "CRON_EXPRESSION", length = 128)
    private String cronExpression;

    @Column(name = "TIME_ZONE", length = 64)
    private String timeZone;

    @Column(name = "NEXT_EXECUTION_AT")
    private Instant nextExecutionAt;

    @Column(name = "ATTEMPT_COUNT", nullable = false)
    private int attemptCount;

    @Column(name = "MAX_ATTEMPTS", nullable = false)
    private int maxAttempts;

    @Column(name = "IDEMPOTENCY_KEY", length = 128, unique = true)
    private String idempotencyKey;

    @Version
    @Column(name = "VERSION", nullable = false)
    private long version;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private Instant updatedAt;

    protected Schedule() {
        // JPA
    }

    public static Schedule onceAt(
            String actionId,
            Instant runAt,
            String idempotencyKey,
            int maxAttempts) {
        Schedule schedule = new Schedule();
        schedule.id = UUID.randomUUID().toString();
        schedule.actionId = Objects.requireNonNull(actionId, "actionId");
        schedule.kind = ScheduleKind.ONCE_AT;
        schedule.runAt = Objects.requireNonNull(runAt, "runAt");
        schedule.nextExecutionAt = runAt;
        schedule.idempotencyKey = idempotencyKey;
        schedule.maxAttempts = requirePositiveMaxAttempts(maxAttempts);
        schedule.attemptCount = 0;
        schedule.status = ScheduleStatus.CREATED;
        return schedule;
    }

    public static Schedule cron(
            String actionId,
            String cronExpression,
            String timeZone,
            Instant firstExecutionAt,
            String idempotencyKey,
            int maxAttempts) {
        Schedule schedule = new Schedule();
        schedule.id = UUID.randomUUID().toString();
        schedule.actionId = Objects.requireNonNull(actionId, "actionId");
        schedule.kind = ScheduleKind.CRON;
        schedule.cronExpression = Objects.requireNonNull(cronExpression, "cronExpression");
        schedule.timeZone = Objects.requireNonNull(timeZone, "timeZone");
        schedule.nextExecutionAt = Objects.requireNonNull(firstExecutionAt, "firstExecutionAt");
        schedule.idempotencyKey = idempotencyKey;
        schedule.maxAttempts = requirePositiveMaxAttempts(maxAttempts);
        schedule.attemptCount = 0;
        schedule.status = ScheduleStatus.CREATED;
        return schedule;
    }

    private static int requirePositiveMaxAttempts(int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        return maxAttempts;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (status == null) {
            status = ScheduleStatus.CREATED;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Makes the schedule eligible for the poller.
     */
    public void markScheduled() {
        transitionTo(ScheduleStatus.SCHEDULED, EnumSet.of(ScheduleStatus.CREATED, ScheduleStatus.PAUSED));
    }

    /**
     * Called after a successful DB claim.
     */
    public void markRunning() {
        transitionTo(
                ScheduleStatus.RUNNING,
                EnumSet.of(ScheduleStatus.SCHEDULED, ScheduleStatus.RETRYING));
        attemptCount++;
    }

    /**
     * Records success. {@link ScheduleKind#ONCE_AT} stays {@link ScheduleStatus#SUCCESS};
     * cron returns to {@link ScheduleStatus#SCHEDULED} with the given next fire time.
     */
    public void markSuccess(Instant nextExecutionForCron) {
        requireStatus(ScheduleStatus.RUNNING);
        if (kind == ScheduleKind.CRON) {
            status = ScheduleStatus.SCHEDULED;
            nextExecutionAt = Objects.requireNonNull(nextExecutionForCron, "nextExecutionForCron");
            attemptCount = 0;
        } else {
            status = ScheduleStatus.SUCCESS;
            nextExecutionAt = null;
        }
    }

    public void markFailed() {
        requireStatus(ScheduleStatus.RUNNING);
        status = ScheduleStatus.FAILED;
    }

    /**
     * Schedules a retry. Throws if attempts are exhausted (caller should {@link #markDead()}).
     */
    public void markRetrying(Instant nextRetryAt) {
        requireStatus(ScheduleStatus.FAILED);
        if (attemptCount >= maxAttempts) {
            throw new IllegalStateException(
                    "Cannot retry schedule " + id + ": attemptCount=" + attemptCount
                            + " maxAttempts=" + maxAttempts);
        }
        status = ScheduleStatus.RETRYING;
        nextExecutionAt = Objects.requireNonNull(nextRetryAt, "nextRetryAt");
    }

    public void markDead() {
        transitionTo(
                ScheduleStatus.DEAD,
                EnumSet.of(ScheduleStatus.FAILED, ScheduleStatus.RUNNING, ScheduleStatus.RETRYING));
        nextExecutionAt = null;
    }

    public void markCancelled() {
        if (TERMINAL.contains(status) && status != ScheduleStatus.CANCELLED) {
            throw new IllegalStateException("Cannot cancel schedule in status " + status);
        }
        status = ScheduleStatus.CANCELLED;
        nextExecutionAt = null;
    }

    public void markPaused() {
        transitionTo(ScheduleStatus.PAUSED, EnumSet.of(ScheduleStatus.SCHEDULED, ScheduleStatus.RETRYING));
    }

    public boolean isDue(Instant now) {
        return (status == ScheduleStatus.SCHEDULED || status == ScheduleStatus.RETRYING)
                && nextExecutionAt != null
                && !nextExecutionAt.isAfter(now);
    }

    public boolean retriesRemaining() {
        return attemptCount < maxAttempts;
    }

    private void transitionTo(ScheduleStatus target, Set<ScheduleStatus> allowedFrom) {
        if (!allowedFrom.contains(status)) {
            throw new IllegalStateException(
                    "Cannot transition schedule " + id + " from " + status + " to " + target);
        }
        status = target;
    }

    private void requireStatus(ScheduleStatus expected) {
        if (status != expected) {
            throw new IllegalStateException(
                    "Schedule " + id + " expected status " + expected + " but was " + status);
        }
    }

    public String getId() {
        return id;
    }

    public String getActionId() {
        return actionId;
    }

    public ScheduleStatus getStatus() {
        return status;
    }

    public ScheduleKind getKind() {
        return kind;
    }

    public Instant getRunAt() {
        return runAt;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public Instant getNextExecutionAt() {
        return nextExecutionAt;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
