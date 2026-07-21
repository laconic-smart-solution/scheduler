package com.laconic.scheduler.scheduler.engine;

import com.laconic.scheduler.scheduler.domain.Schedule;
import java.time.Instant;
import java.util.List;

/**
 * Loads schedules that are due for execution.
 * <p>
 * The locking implementation must use database row locks
 * ({@code FOR UPDATE SKIP LOCKED}) so peer instances skip already-claimed rows.
 * Callers must invoke this inside a transaction that also runs {@link ScheduleClaimer}.
 */
public interface ScheduleLoader {

    /**
     * Lock and return up to {@code batchSize} due schedules still in
     * {@code SCHEDULED} or {@code RETRYING}. Does not change status to {@code RUNNING}.
     *
     * @param now       claim waterline (typically Instant.now())
     * @param batchSize max rows to lock this tick
     * @return locked, managed schedules (may be empty)
     */
    List<Schedule> loadDueForUpdate(Instant now, int batchSize);
}
