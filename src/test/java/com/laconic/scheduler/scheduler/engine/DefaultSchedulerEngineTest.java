package com.laconic.scheduler.scheduler.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.laconic.scheduler.execution.dispatcher.ExecutionDispatcher;
import com.laconic.scheduler.retry.StaleRunningReaper;
import com.laconic.scheduler.scheduler.domain.Schedule;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultSchedulerEngineTest {

    @Mock
    private ScheduleLoader scheduleLoader;

    @Mock
    private ScheduleClaimer scheduleClaimer;

    @Mock
    private ExecutionDispatcher executionDispatcher;

    @Mock
    private StaleRunningReaper staleRunningReaper;

    private DefaultSchedulerEngine engine;

    @BeforeEach
    void setUp() {
        engine =
                new DefaultSchedulerEngine(
                        scheduleLoader,
                        scheduleClaimer,
                        executionDispatcher,
                        Optional.of(staleRunningReaper),
                        10);
    }

    @Test
    void tick_loadsClaimsAndDispatchesEach() {
        Instant now = Instant.parse("2026-07-21T12:00:00Z");
        Schedule locked = Schedule.onceAt("a1", now, "k1", 2);
        locked.markScheduled();
        Schedule claimed = Schedule.onceAt("a1", now, "k2", 2);
        claimed.markScheduled();
        claimed.markRunning();

        when(scheduleLoader.loadDueForUpdate(eq(now), eq(10))).thenReturn(List.of(locked));
        when(scheduleClaimer.claim(locked)).thenReturn(claimed);

        int processed = engine.tick(now, 10);

        assertEquals(1, processed);
        verify(staleRunningReaper).reap(now);
        verify(executionDispatcher).dispatch(claimed);
    }

    @Test
    void tick_returnsZeroWhenNothingDue() {
        when(scheduleLoader.loadDueForUpdate(any(), anyInt())).thenReturn(List.of());
        assertEquals(0, engine.tick(Instant.now(), 5));
        verify(staleRunningReaper).reap(any());
    }
}
