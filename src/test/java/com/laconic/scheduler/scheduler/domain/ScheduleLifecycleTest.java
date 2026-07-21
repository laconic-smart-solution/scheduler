package com.laconic.scheduler.scheduler.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ScheduleLifecycleTest {

    private static final Instant T0 = Instant.parse("2026-07-21T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-07-21T10:05:00Z");

    @Test
    void onceAtHappyPath_createdToScheduledToRunningToSuccess() {
        Schedule schedule = Schedule.onceAt("action-1", T0, "idem-1", 3);

        assertEquals(ScheduleStatus.CREATED, schedule.getStatus());
        schedule.markScheduled();
        assertEquals(ScheduleStatus.SCHEDULED, schedule.getStatus());
        assertTrue(schedule.isDue(T0));

        schedule.markRunning();
        assertEquals(ScheduleStatus.RUNNING, schedule.getStatus());
        assertEquals(1, schedule.getAttemptCount());

        schedule.markSuccess(null);
        assertEquals(ScheduleStatus.SUCCESS, schedule.getStatus());
        assertFalse(schedule.isDue(T1));
    }

    @Test
    void cronSuccess_returnsToScheduledWithNextFire() {
        Schedule schedule = Schedule.cron("action-1", "0 */5 * * * *", "UTC", T0, "idem-cron", 3);
        schedule.markScheduled();
        schedule.markRunning();
        schedule.markSuccess(T1);

        assertEquals(ScheduleStatus.SCHEDULED, schedule.getStatus());
        assertEquals(T1, schedule.getNextExecutionAt());
        assertEquals(0, schedule.getAttemptCount());
    }

    @Test
    void failedThenRetryingThenDeadWhenExhausted() {
        Schedule schedule = Schedule.onceAt("action-1", T0, "idem-2", 1);
        schedule.markScheduled();
        schedule.markRunning();
        schedule.markFailed();

        assertThrows(IllegalStateException.class, () -> schedule.markRetrying(T1));
        schedule.markDead();
        assertEquals(ScheduleStatus.DEAD, schedule.getStatus());
    }

    @Test
    void retryAllowedWhenAttemptsRemain() {
        Schedule schedule = Schedule.onceAt("action-1", T0, "idem-3", 3);
        schedule.markScheduled();
        schedule.markRunning();
        schedule.markFailed();
        schedule.markRetrying(T1);

        assertEquals(ScheduleStatus.RETRYING, schedule.getStatus());
        assertEquals(T1, schedule.getNextExecutionAt());
        assertTrue(schedule.isDue(T1));
    }

    @Test
    void illegalTransition_throws() {
        Schedule schedule = Schedule.onceAt("action-1", T0, "idem-4", 3);
        assertThrows(IllegalStateException.class, schedule::markRunning);
    }
}
