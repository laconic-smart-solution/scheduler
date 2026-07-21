package com.laconic.scheduler.scheduler.engine;

import java.time.Instant;

/**
 * Orchestrates one poll cycle: load due → claim → dispatch → record.
 */
public interface SchedulerEngine {

    /**
     * @return number of schedules processed in this tick
     */
    int tick();

    /**
     * @param now       waterline for due query
     * @param batchSize max schedules to claim
     * @return number processed
     */
    int tick(Instant now, int batchSize);
}
