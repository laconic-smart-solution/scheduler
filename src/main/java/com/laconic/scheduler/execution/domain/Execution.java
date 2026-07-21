package com.laconic.scheduler.execution.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One attempt to run a schedule's action. Append-only history for audit and retries.
 */
@Entity
@Table(name = "SCHEDULER_EXECUTION")
public class Execution {

    @Id
    @Column(name = "ID", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "SCHEDULE_ID", length = 36, nullable = false, updatable = false)
    private String scheduleId;

    @Column(name = "ACTION_ID", length = 36, nullable = false, updatable = false)
    private String actionId;

    @Column(name = "ATTEMPT_NO", nullable = false, updatable = false)
    private int attemptNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 32, nullable = false)
    private ExecutionStatus status;

    @Column(name = "SCHEDULED_TIME", nullable = false, updatable = false)
    private Instant scheduledTime;

    @Column(name = "TRIGGERED_TIME", nullable = false, updatable = false)
    private Instant triggeredTime;

    @Column(name = "STARTED_AT", nullable = false)
    private Instant startedAt;

    @Column(name = "FINISHED_AT")
    private Instant finishedAt;

    @Column(name = "ERROR_CODE", length = 64)
    private String errorCode;

    @Lob
    @Column(name = "DETAIL_JSON")
    private String detailJson;

    protected Execution() {
        // JPA
    }

    public static Execution start(
            String scheduleId,
            String actionId,
            int attemptNo,
            Instant scheduledTime,
            Instant triggeredTime) {
        Execution execution = new Execution();
        execution.id = UUID.randomUUID().toString();
        execution.scheduleId = Objects.requireNonNull(scheduleId, "scheduleId");
        execution.actionId = Objects.requireNonNull(actionId, "actionId");
        execution.attemptNo = attemptNo;
        execution.scheduledTime = Objects.requireNonNull(scheduledTime, "scheduledTime");
        execution.triggeredTime = Objects.requireNonNull(triggeredTime, "triggeredTime");
        execution.startedAt = triggeredTime;
        execution.status = ExecutionStatus.RUNNING;
        return execution;
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (startedAt == null) {
            startedAt = Instant.now();
        }
    }

    public void markSuccess(String detailJson) {
        requireRunning();
        status = ExecutionStatus.SUCCESS;
        finishedAt = Instant.now();
        this.detailJson = detailJson;
        errorCode = null;
    }

    public void markFailed(String errorCode, String detailJson) {
        requireRunning();
        status = ExecutionStatus.FAILED;
        finishedAt = Instant.now();
        this.errorCode = errorCode;
        this.detailJson = detailJson;
    }

    public void markSkipped(String detailJson) {
        requireRunning();
        status = ExecutionStatus.SKIPPED;
        finishedAt = Instant.now();
        this.detailJson = detailJson;
    }

    private void requireRunning() {
        if (status != ExecutionStatus.RUNNING) {
            throw new IllegalStateException(
                    "Execution " + id + " is " + status + ", expected RUNNING");
        }
    }

    public String getId() {
        return id;
    }

    public String getScheduleId() {
        return scheduleId;
    }

    public String getActionId() {
        return actionId;
    }

    public int getAttemptNo() {
        return attemptNo;
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public Instant getScheduledTime() {
        return scheduledTime;
    }

    public Instant getTriggeredTime() {
        return triggeredTime;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getDetailJson() {
        return detailJson;
    }
}
