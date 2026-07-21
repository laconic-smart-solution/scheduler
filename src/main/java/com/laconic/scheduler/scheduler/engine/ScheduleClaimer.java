package com.laconic.scheduler.scheduler.engine;

import com.laconic.scheduler.scheduler.domain.Schedule;

/**
 * Transitions a previously locked due schedule to {@code RUNNING}.
 * <p>
 * Must run in the same transaction as {@link ScheduleLoader#loadDueForUpdate}
 * so the row lock is held until commit.
 */
public interface ScheduleClaimer {

    /**
     * Marks the schedule running and persists the optimistic-lock version bump.
     *
     * @param lockedSchedule schedule already locked by the loader
     * @return the claimed schedule
     */
    Schedule claim(Schedule lockedSchedule);
}
