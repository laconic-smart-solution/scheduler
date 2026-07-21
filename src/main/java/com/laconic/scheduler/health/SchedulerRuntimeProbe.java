package com.laconic.scheduler.health;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * In-memory probe of the last engine tick — used by health and metrics.
 */
@Component
public class SchedulerRuntimeProbe {

    public record TickSnapshot(
            Instant at, int processed, int reaped, Optional<String> errorMessage) {}

    private final AtomicReference<TickSnapshot> lastTick = new AtomicReference<>();

    public void recordSuccess(Instant at, int processed, int reaped) {
        lastTick.set(new TickSnapshot(at, processed, reaped, Optional.empty()));
    }

    public void recordFailure(Instant at, String errorMessage) {
        lastTick.set(
                new TickSnapshot(
                        at,
                        0,
                        0,
                        Optional.of(errorMessage != null && !errorMessage.isBlank()
                                ? errorMessage
                                : "unknown")));
    }

    public Optional<TickSnapshot> lastTick() {
        return Optional.ofNullable(lastTick.get());
    }
}
