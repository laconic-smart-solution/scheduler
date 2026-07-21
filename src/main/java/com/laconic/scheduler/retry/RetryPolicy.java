package com.laconic.scheduler.retry;

import com.laconic.scheduler.scheduler.domain.Schedule;
import java.time.Instant;
import java.util.Optional;

/**
 * Decides whether a failed schedule should retry and when.
 */
public interface RetryPolicy {

    /**
     * @return next fire time if retries remain; empty means the schedule should become {@code DEAD}
     */
    Optional<Instant> nextRetryAt(Schedule schedule, Instant failedAt);
}
