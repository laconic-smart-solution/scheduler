package com.laconic.scheduler.execution.dispatcher;

import com.laconic.scheduler.execution.context.ExecutionContext;
import com.laconic.scheduler.execution.domain.Execution;
import com.laconic.scheduler.execution.recorder.ExecutionRecorder;
import com.laconic.scheduler.executor.ExecutorRegistry;
import com.laconic.scheduler.executor.TaskExecutionException;
import com.laconic.scheduler.executor.TaskExecutor;
import com.laconic.scheduler.metrics.SchedulerMetrics;
import com.laconic.scheduler.retry.ScheduleFailureApplier;
import com.laconic.scheduler.scheduler.domain.Action;
import com.laconic.scheduler.scheduler.domain.Schedule;
import com.laconic.scheduler.scheduler.domain.ScheduleKind;
import com.laconic.scheduler.scheduler.persistence.ActionRepository;
import com.laconic.scheduler.scheduler.persistence.ScheduleRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds {@link ExecutionContext}, invokes the registered executor, records outcome,
 * and advances schedule lifecycle (success / retry / dead).
 */
@Component
public class DefaultExecutionDispatcher implements ExecutionDispatcher {

    private static final Logger log = LoggerFactory.getLogger(DefaultExecutionDispatcher.class);

    /** Placeholder until cron next-fire calculation lands. */
    private static final Duration CRON_PLACEHOLDER_DELAY = Duration.ofMinutes(1);

    private final ActionRepository actionRepository;
    private final ScheduleRepository scheduleRepository;
    private final ExecutorRegistry executorRegistry;
    private final ExecutionRecorder executionRecorder;
    private final ScheduleFailureApplier failureApplier;
    private final SchedulerMetrics schedulerMetrics;

    public DefaultExecutionDispatcher(
            ActionRepository actionRepository,
            ScheduleRepository scheduleRepository,
            ExecutorRegistry executorRegistry,
            ExecutionRecorder executionRecorder,
            ScheduleFailureApplier failureApplier,
            SchedulerMetrics schedulerMetrics) {
        this.actionRepository = Objects.requireNonNull(actionRepository);
        this.scheduleRepository = Objects.requireNonNull(scheduleRepository);
        this.executorRegistry = Objects.requireNonNull(executorRegistry);
        this.executionRecorder = Objects.requireNonNull(executionRecorder);
        this.failureApplier = Objects.requireNonNull(failureApplier);
        this.schedulerMetrics = Objects.requireNonNull(schedulerMetrics);
    }

    @Override
    @Transactional
    public void dispatch(Schedule claimedSchedule) {
        Schedule schedule = Objects.requireNonNull(claimedSchedule, "claimedSchedule");
        Action action =
                actionRepository
                        .findById(schedule.getActionId())
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Action not found: " + schedule.getActionId()));

        Instant triggeredTime = Instant.now();
        long started = System.currentTimeMillis();
        Execution execution = executionRecorder.start(schedule, action, triggeredTime);

        ExecutionContext context =
                new ExecutionContext(
                        schedule.getId(),
                        execution.getId(),
                        action.getId(),
                        action.getType(),
                        execution.getScheduledTime(),
                        triggeredTime,
                        Math.max(0, schedule.getAttemptCount() - 1),
                        action.getPayloadJson(),
                        action.getMetadataJson());

        try {
            TaskExecutor executor = executorRegistry.get(action.getType());
            executor.execute(context);
            executionRecorder.markSuccess(execution, "{\"result\":\"ok\"}");
            applySuccess(schedule);
            schedulerMetrics.recordDispatchSuccess(
                    action.getType(), System.currentTimeMillis() - started);
            log.info(
                    "Dispatch SUCCESS scheduleId={} executionId={} type={}",
                    schedule.getId(),
                    execution.getId(),
                    action.getType());
        } catch (Exception ex) {
            String errorCode =
                    ex instanceof TaskExecutionException tee
                            ? tee.getErrorCode()
                            : ex.getClass().getSimpleName();
            executionRecorder.markFailed(execution, errorCode, ex.getMessage());
            failureApplier.applyFailure(schedule, triggeredTime);
            schedulerMetrics.recordDispatchFailure(
                    action.getType(), errorCode, System.currentTimeMillis() - started);
            log.warn(
                    "Dispatch FAILED scheduleId={} executionId={} code={}: {}",
                    schedule.getId(),
                    execution.getId(),
                    errorCode,
                    ex.getMessage());
        }

        scheduleRepository.save(schedule);
    }

    private void applySuccess(Schedule schedule) {
        if (schedule.getKind() == ScheduleKind.CRON) {
            Instant next = Instant.now().plus(CRON_PLACEHOLDER_DELAY);
            schedule.markSuccess(next);
        } else {
            schedule.markSuccess(null);
        }
    }
}
