package com.laconic.scheduler.execution.dispatcher;

import com.laconic.scheduler.scheduler.domain.Schedule;

/**
 * Runs one claimed schedule through executor resolution and recording.
 */
public interface ExecutionDispatcher {

    /**
     * @param claimedSchedule schedule already in {@code RUNNING}
     */
    void dispatch(Schedule claimedSchedule);
}
