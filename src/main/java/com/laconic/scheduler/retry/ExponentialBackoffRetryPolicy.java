package com.laconic.scheduler.retry;

import com.laconic.scheduler.scheduler.domain.Schedule;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Exponential backoff: {@code min(maxDelay, baseDelay * multiplier^(attempt-1))}.
 */
@Component
public class ExponentialBackoffRetryPolicy implements RetryPolicy {

    private final long baseDelayMs;
    private final double multiplier;
    private final long maxDelayMs;

    public ExponentialBackoffRetryPolicy(
            @Value("${scheduler.retry.base-delay-ms:60000}") long baseDelayMs,
            @Value("${scheduler.retry.multiplier:2.0}") double multiplier,
            @Value("${scheduler.retry.max-delay-ms:3600000}") long maxDelayMs) {
        if (baseDelayMs < 1L) {
            throw new IllegalArgumentException("scheduler.retry.base-delay-ms must be >= 1");
        }
        if (multiplier < 1.0d) {
            throw new IllegalArgumentException("scheduler.retry.multiplier must be >= 1.0");
        }
        if (maxDelayMs < baseDelayMs) {
            throw new IllegalArgumentException(
                    "scheduler.retry.max-delay-ms must be >= base-delay-ms");
        }
        this.baseDelayMs = baseDelayMs;
        this.multiplier = multiplier;
        this.maxDelayMs = maxDelayMs;
    }

    @Override
    public Optional<Instant> nextRetryAt(Schedule schedule, Instant failedAt) {
        Objects.requireNonNull(schedule, "schedule");
        Objects.requireNonNull(failedAt, "failedAt");
        if (!schedule.retriesRemaining()) {
            return Optional.empty();
        }
        int attempt = Math.max(1, schedule.getAttemptCount());
        double raw = baseDelayMs * Math.pow(multiplier, attempt - 1);
        long delayMs = Math.min(maxDelayMs, Math.round(raw));
        return Optional.of(failedAt.plusMillis(delayMs));
    }
}
