package com.laconic.scheduler.retry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.laconic.scheduler.execution.domain.Execution;
import com.laconic.scheduler.execution.domain.ExecutionStatus;
import com.laconic.scheduler.execution.persistence.ExecutionRepository;
import com.laconic.scheduler.scheduler.domain.Schedule;
import com.laconic.scheduler.scheduler.domain.ScheduleStatus;
import com.laconic.scheduler.scheduler.persistence.ScheduleRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class StaleRunningReaperTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private ExecutionRepository executionRepository;

    @Mock
    private Query nativeQuery;

    private StaleRunningReaper reaper;

    @BeforeEach
    void setUp() {
        ScheduleFailureApplier applier =
                new ScheduleFailureApplier(new ExponentialBackoffRetryPolicy(60_000L, 2.0d, 3_600_000L));
        reaper =
                new StaleRunningReaper(
                        entityManager,
                        scheduleRepository,
                        executionRepository,
                        applier,
                        300_000L,
                        50);
    }

    @Test
    void reap_marksOpenExecutionFailedAndSchedulesRetry() {
        Instant now = Instant.parse("2026-07-21T12:10:00Z");
        Instant staleBefore = now.minusMillis(300_000L);

        Schedule schedule = Schedule.onceAt("a1", now.minusSeconds(600), "stale-1", 3);
        schedule.markScheduled();
        schedule.markRunning();

        when(scheduleRepository.findStaleRunningUnlocked(
                        eq(ScheduleStatus.RUNNING), eq(staleBefore), any(Pageable.class)))
                .thenReturn(List.of(schedule));
        when(entityManager.createNativeQuery(any(String.class))).thenReturn(nativeQuery);
        when(nativeQuery.setParameter(eq("ids"), any())).thenReturn(nativeQuery);
        when(nativeQuery.getResultList()).thenReturn(List.of(schedule.getId()));
        when(entityManager.find(Schedule.class, schedule.getId())).thenReturn(schedule);

        Instant triggered = now.minusSeconds(400);
        Execution open =
                Execution.start(schedule.getId(), "a1", 1, triggered, triggered);
        when(executionRepository.findByScheduleIdAndStatus(
                        schedule.getId(), ExecutionStatus.RUNNING))
                .thenReturn(List.of(open));

        int reaped = reaper.reap(now);

        assertEquals(1, reaped);
        assertEquals(ExecutionStatus.FAILED, open.getStatus());
        assertEquals(StaleRunningReaper.ERROR_CODE, open.getErrorCode());
        assertEquals(ScheduleStatus.RETRYING, schedule.getStatus());
        assertEquals(now.plusSeconds(60), schedule.getNextExecutionAt());
        verify(scheduleRepository).save(schedule);
        verify(executionRepository).save(open);
    }

    @Test
    void reap_marksDeadWhenAttemptsExhausted() {
        Instant now = Instant.parse("2026-07-21T12:10:00Z");
        Instant staleBefore = now.minusMillis(300_000L);

        Schedule schedule = Schedule.onceAt("a1", now.minusSeconds(600), "stale-dead", 1);
        schedule.markScheduled();
        schedule.markRunning();

        when(scheduleRepository.findStaleRunningUnlocked(
                        eq(ScheduleStatus.RUNNING), eq(staleBefore), any(Pageable.class)))
                .thenReturn(List.of(schedule));
        when(entityManager.createNativeQuery(any(String.class))).thenReturn(nativeQuery);
        when(nativeQuery.setParameter(eq("ids"), any())).thenReturn(nativeQuery);
        when(nativeQuery.getResultList()).thenReturn(List.of(schedule.getId()));
        when(entityManager.find(Schedule.class, schedule.getId())).thenReturn(schedule);
        when(executionRepository.findByScheduleIdAndStatus(
                        schedule.getId(), ExecutionStatus.RUNNING))
                .thenReturn(List.of());

        int reaped = reaper.reap(now);

        assertEquals(1, reaped);
        assertEquals(ScheduleStatus.DEAD, schedule.getStatus());
        assertNull(schedule.getNextExecutionAt());
    }
}
