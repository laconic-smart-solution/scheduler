package com.laconic.scheduler.health;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

class PollHealthIndicatorTest {

    private SchedulerRuntimeProbe probe;
    private PollHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        probe = new SchedulerRuntimeProbe();
        indicator = new PollHealthIndicator(probe, true, 5_000L, 3L);
    }

    @Test
    void health_upWhenRecentSuccessfulTick() {
        probe.recordSuccess(Instant.now().minusSeconds(2), 1, 0);
        assertEquals(Status.UP, indicator.health().getStatus());
    }

    @Test
    void health_downWhenTickStale() {
        probe.recordSuccess(Instant.now().minusSeconds(60), 0, 0);
        assertEquals(Status.DOWN, indicator.health().getStatus());
    }

    @Test
    void health_upWhenPollDisabled() {
        PollHealthIndicator disabled = new PollHealthIndicator(probe, false, 5_000L, 3L);
        assertEquals(Status.UP, disabled.health().getStatus());
    }

    @Test
    void health_downWhenLastTickFailed() {
        probe.recordFailure(Instant.now(), "boom");
        assertEquals(Status.DOWN, indicator.health().getStatus());
    }
}
