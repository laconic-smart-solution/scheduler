package com.laconic.scheduler.retry;

import com.laconic.scheduler.scheduler.domain.Schedule;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Shared RUNNING → FAILED → RETRYING|DEAD transition used by the dispatcher and reaper.
 */
@Component
public class ScheduleFailureApplier {

    private final RetryPolicy retryPolicy;

    public ScheduleFailureApplier(RetryPolicy retryPolicy) {
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
    }

    public void applyFailure(Schedule schedule, Instant failedAt) {
        Objects.requireNonNull(schedule, "schedule");
        Objects.requireNonNull(failedAt, "failedAt");
        schedule.markFailed();
        retryPolicy
                .nextRetryAt(schedule, failedAt)
                .ifPresentOrElse(schedule::markRetrying, schedule::markDead);
    }
}
