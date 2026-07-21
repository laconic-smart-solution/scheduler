package com.laconic.scheduler.retry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.laconic.scheduler.execution.context.ExecutionContext;
import com.laconic.scheduler.execution.domain.Execution;
import com.laconic.scheduler.execution.recorder.ExecutionRecorder;
import com.laconic.scheduler.executor.ExecutorRegistry;
import com.laconic.scheduler.executor.TaskExecutionException;
import com.laconic.scheduler.executor.TaskExecutor;
import com.laconic.scheduler.execution.dispatcher.DefaultExecutionDispatcher;
import com.laconic.scheduler.scheduler.domain.Action;
import com.laconic.scheduler.scheduler.domain.ActionType;
import com.laconic.scheduler.scheduler.domain.Schedule;
import com.laconic.scheduler.scheduler.domain.ScheduleStatus;
import com.laconic.scheduler.scheduler.persistence.ActionRepository;
import com.laconic.scheduler.scheduler.persistence.ScheduleRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultExecutionDispatcherRetryTest {

    @Mock
    private ActionRepository actionRepository;

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private ExecutorRegistry executorRegistry;

    @Mock
    private ExecutionRecorder executionRecorder;

    @Mock
    private TaskExecutor taskExecutor;

    private DefaultExecutionDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        ScheduleFailureApplier applier =
                new ScheduleFailureApplier(new ExponentialBackoffRetryPolicy(60_000L, 2.0d, 3_600_000L));
        dispatcher =
                new DefaultExecutionDispatcher(
                        actionRepository,
                        scheduleRepository,
                        executorRegistry,
                        executionRecorder,
                        applier);
    }

    @Test
    void dispatch_onFailure_movesToRetryingWithBackoff() {
        Instant due = Instant.parse("2026-07-21T12:00:00Z");
        Action action = new Action(ActionType.WEBHOOK, "{\"url\":\"http://x\"}", null);
        Schedule schedule = Schedule.onceAt(action.getId(), due, "retry-1", 3);
        schedule.markScheduled();
        schedule.markRunning();

        Execution execution =
                Execution.start(schedule.getId(), action.getId(), 1, due, due);

        when(actionRepository.findById(action.getId())).thenReturn(Optional.of(action));
        when(executionRecorder.start(any(), any(), any())).thenReturn(execution);
        when(executorRegistry.get(ActionType.WEBHOOK)).thenReturn(taskExecutor);
        org.mockito.Mockito.doThrow(new TaskExecutionException("HTTP_500", "boom"))
                .when(taskExecutor)
                .execute(any(ExecutionContext.class));

        Instant before = Instant.now();
        dispatcher.dispatch(schedule);
        Instant after = Instant.now();

        assertEquals(ScheduleStatus.RETRYING, schedule.getStatus());
        Instant next = schedule.getNextExecutionAt();
        org.junit.jupiter.api.Assertions.assertTrue(
                !next.isBefore(before.plusSeconds(58)) && !next.isAfter(after.plusSeconds(62)),
                "expected ~60s backoff from now, next=" + next);
        verify(scheduleRepository).save(schedule);
        verify(executionRecorder).markFailed(execution, "HTTP_500", "boom");
    }

    @Test
    void dispatch_onFailure_marksDeadWhenAttemptsExhausted() {
        Instant due = Instant.parse("2026-07-21T12:00:00Z");
        Action action = new Action(ActionType.WEBHOOK, "{\"url\":\"http://x\"}", null);
        Schedule schedule = Schedule.onceAt(action.getId(), due, "dead-1", 1);
        schedule.markScheduled();
        schedule.markRunning();

        Execution execution =
                Execution.start(schedule.getId(), action.getId(), 1, due, due);

        when(actionRepository.findById(action.getId())).thenReturn(Optional.of(action));
        when(executionRecorder.start(any(), any(), any())).thenReturn(execution);
        when(executorRegistry.get(ActionType.WEBHOOK)).thenReturn(taskExecutor);
        org.mockito.Mockito.doThrow(new TaskExecutionException("HTTP_500", "boom"))
                .when(taskExecutor)
                .execute(any(ExecutionContext.class));

        dispatcher.dispatch(schedule);

        assertEquals(ScheduleStatus.DEAD, schedule.getStatus());
    }
}
