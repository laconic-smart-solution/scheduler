package com.laconic.scheduler.retry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.laconic.scheduler.scheduler.domain.Schedule;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExponentialBackoffRetryPolicyTest {

    private ExponentialBackoffRetryPolicy policy;

    @BeforeEach
    void setUp() {
        // base 60s, multiplier 2, max 1h
        policy = new ExponentialBackoffRetryPolicy(60_000L, 2.0d, 3_600_000L);
    }

    @Test
    void nextRetryAt_growsExponentiallyWithAttempt() {
        Instant failedAt = Instant.parse("2026-07-21T12:00:00Z");
        Schedule schedule = Schedule.onceAt("a1", failedAt, "k1", 5);
        schedule.markScheduled();
        schedule.markRunning(); // attemptCount = 1

        Optional<Instant> first = policy.nextRetryAt(schedule, failedAt);
        assertEquals(Optional.of(failedAt.plusSeconds(60)), first);

        schedule.markFailed();
        schedule.markRetrying(failedAt.plusSeconds(60));
        schedule.markRunning(); // attemptCount = 2

        Optional<Instant> second = policy.nextRetryAt(schedule, failedAt);
        assertEquals(Optional.of(failedAt.plusSeconds(120)), second);
    }

    @Test
    void nextRetryAt_capsAtMaxDelay() {
        Instant failedAt = Instant.parse("2026-07-21T12:00:00Z");
        ExponentialBackoffRetryPolicy tight =
                new ExponentialBackoffRetryPolicy(60_000L, 2.0d, 90_000L);
        Schedule schedule = Schedule.onceAt("a1", failedAt, "k1", 10);
        schedule.markScheduled();
        schedule.markRunning();
        schedule.markFailed();
        schedule.markRetrying(failedAt.plusSeconds(60));
        schedule.markRunning(); // attempt 2 → raw 120s, capped to 90s

        assertEquals(Optional.of(failedAt.plusSeconds(90)), tight.nextRetryAt(schedule, failedAt));
    }

    @Test
    void nextRetryAt_emptyWhenAttemptsExhausted() {
        Instant failedAt = Instant.parse("2026-07-21T12:00:00Z");
        Schedule schedule = Schedule.onceAt("a1", failedAt, "k1", 1);
        schedule.markScheduled();
        schedule.markRunning(); // attemptCount == maxAttempts

        assertTrue(policy.nextRetryAt(schedule, failedAt).isEmpty());
    }
}
