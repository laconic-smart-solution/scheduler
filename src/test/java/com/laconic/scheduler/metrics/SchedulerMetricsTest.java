package com.laconic.scheduler.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.laconic.scheduler.scheduler.domain.ActionType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SchedulerMetricsTest {

    private SimpleMeterRegistry registry;
    private SchedulerMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new SchedulerMetrics(registry);
    }

    @Test
    void recordTick_updatesCountersAndGauges() {
        metrics.recordTick(2, 1, 15L, 4_000L, 2);

        assertEquals(2.0d, registry.get("scheduler.tick.processed").counter().count());
        assertEquals(1.0d, registry.get("scheduler.reaper.reaped").counter().count());
        assertEquals(4_000.0d, registry.get("scheduler.poll.lag.milliseconds").gauge().value());
        assertEquals(2.0d, registry.get("scheduler.due.backlog").gauge().value());
    }

    @Test
    void recordDispatch_tagsByTypeAndOutcome() {
        metrics.recordDispatchSuccess(ActionType.WEBHOOK, 12L);
        metrics.recordDispatchFailure(ActionType.WEBHOOK, "HTTP_500", 8L);

        assertEquals(
                1.0d,
                registry
                        .get("scheduler.dispatch")
                        .tag("type", "WEBHOOK")
                        .tag("outcome", "success")
                        .counter()
                        .count());
        assertEquals(
                1.0d,
                registry
                        .get("scheduler.dispatch")
                        .tag("type", "WEBHOOK")
                        .tag("outcome", "failure")
                        .counter()
                        .count());
    }
}
