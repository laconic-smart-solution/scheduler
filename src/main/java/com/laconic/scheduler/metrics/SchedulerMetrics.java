package com.laconic.scheduler.metrics;

import com.laconic.scheduler.scheduler.domain.ActionType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * Scheduler-specific Micrometer meters (tick, reaper, per-executor dispatch).
 */
@Component
public class SchedulerMetrics {

    private final MeterRegistry registry;
    private final AtomicLong pollLagMs = new AtomicLong(0);
    private final AtomicLong dueBacklog = new AtomicLong(0);
    private final Counter tickProcessed;
    private final Counter reaperReaped;
    private final Timer tickDuration;

    public SchedulerMetrics(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.tickProcessed =
                Counter.builder("scheduler.tick.processed")
                        .description("Schedules dispatched per engine tick")
                        .register(registry);
        this.reaperReaped =
                Counter.builder("scheduler.reaper.reaped")
                        .description("Stale RUNNING schedules reaped")
                        .register(registry);
        this.tickDuration =
                Timer.builder("scheduler.tick.duration")
                        .description("Engine tick wall time")
                        .register(registry);
        io.micrometer.core.instrument.Gauge.builder(
                        "scheduler.poll.lag.milliseconds", pollLagMs, AtomicLong::get)
                .description("Age of oldest due schedule seen in the last tick (ms)")
                .register(registry);
        io.micrometer.core.instrument.Gauge.builder(
                        "scheduler.due.backlog", dueBacklog, AtomicLong::get)
                .description("Due schedules loaded in the last tick")
                .register(registry);
    }

    public void recordTick(int processed, int reaped, long durationMs, long lagMs, int backlog) {
        if (processed > 0) {
            tickProcessed.increment(processed);
        }
        if (reaped > 0) {
            reaperReaped.increment(reaped);
        }
        tickDuration.record(durationMs, TimeUnit.MILLISECONDS);
        pollLagMs.set(Math.max(0L, lagMs));
        dueBacklog.set(Math.max(0, backlog));
    }

    public void recordDispatchSuccess(ActionType type, long durationMs) {
        dispatchTimer(type, "success").record(durationMs, TimeUnit.MILLISECONDS);
        dispatchCounter(type, "success").increment();
    }

    public void recordDispatchFailure(ActionType type, String errorCode, long durationMs) {
        dispatchTimer(type, "failure").record(durationMs, TimeUnit.MILLISECONDS);
        dispatchCounter(type, "failure")
                .increment();
        Counter.builder("scheduler.dispatch.errors")
                .tag("type", type.name())
                .tag("code", errorCode == null ? "unknown" : errorCode)
                .register(registry)
                .increment();
    }

    private Timer dispatchTimer(ActionType type, String outcome) {
        return Timer.builder("scheduler.dispatch.duration")
                .tag("type", type.name())
                .tag("outcome", outcome)
                .register(registry);
    }

    private Counter dispatchCounter(ActionType type, String outcome) {
        return Counter.builder("scheduler.dispatch")
                .tag("type", type.name())
                .tag("outcome", outcome)
                .register(registry);
    }
}
