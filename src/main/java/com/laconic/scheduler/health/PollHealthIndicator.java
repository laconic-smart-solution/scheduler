package com.laconic.scheduler.health;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.stereotype.Component;

/**
 * Reports whether the Quartz poll → engine tick path is still alive.
 * <p>
 * When polling is disabled (tests / manual tick), status stays UP with a detail note.
 */
@Component
public class PollHealthIndicator extends AbstractHealthIndicator {

    private final SchedulerRuntimeProbe probe;
    private final boolean pollEnabled;
    private final long intervalMs;
    private final long staleMultiplier;

    public PollHealthIndicator(
            SchedulerRuntimeProbe probe,
            @Value("${scheduler.poll.enabled:true}") boolean pollEnabled,
            @Value("${scheduler.poll.interval-ms:5000}") long intervalMs,
            @Value("${scheduler.health.poll-stale-multiplier:3}") long staleMultiplier) {
        this.probe = Objects.requireNonNull(probe, "probe");
        this.pollEnabled = pollEnabled;
        this.intervalMs = intervalMs;
        if (staleMultiplier < 1L) {
            throw new IllegalArgumentException("scheduler.health.poll-stale-multiplier must be >= 1");
        }
        this.staleMultiplier = staleMultiplier;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        if (!pollEnabled) {
            builder.up().withDetail("pollEnabled", false).withDetail("note", "background poll disabled");
            probe.lastTick()
                    .ifPresent(
                            snap ->
                                    builder.withDetail("lastTickAt", snap.at().toString())
                                            .withDetail("lastProcessed", snap.processed()));
            return;
        }

        var last = probe.lastTick();
        if (last.isEmpty()) {
            builder.up()
                    .withDetail("pollEnabled", true)
                    .withDetail("note", "awaiting first tick");
            return;
        }

        var snap = last.get();
        builder.withDetail("pollEnabled", true)
                .withDetail("lastTickAt", snap.at().toString())
                .withDetail("lastProcessed", snap.processed())
                .withDetail("lastReaped", snap.reaped());

        if (snap.errorMessage().isPresent()) {
            builder.down().withDetail("lastError", snap.errorMessage().get());
            return;
        }

        long ageMs = Duration.between(snap.at(), Instant.now()).toMillis();
        long thresholdMs = intervalMs * staleMultiplier;
        builder.withDetail("ageMs", ageMs).withDetail("staleThresholdMs", thresholdMs);
        if (ageMs > thresholdMs) {
            builder.down().withDetail("reason", "no successful tick within threshold");
        } else {
            builder.up();
        }
    }
}
