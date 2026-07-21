package com.laconic.scheduler.scheduler.engine;

import com.laconic.scheduler.execution.dispatcher.ExecutionDispatcher;
import com.laconic.scheduler.scheduler.domain.Schedule;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default engine pipeline.
 * <p>
 * Quartz (Feature 4) will only call {@link #tick()}; it does not own schedule rows.
 */
@Service
public class DefaultSchedulerEngine implements SchedulerEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultSchedulerEngine.class);

    private final ScheduleLoader scheduleLoader;
    private final ScheduleClaimer scheduleClaimer;
    private final ExecutionDispatcher executionDispatcher;
    private final int defaultBatchSize;

    public DefaultSchedulerEngine(
            ScheduleLoader scheduleLoader,
            ScheduleClaimer scheduleClaimer,
            ExecutionDispatcher executionDispatcher,
            @Value("${scheduler.engine.batch-size:50}") int defaultBatchSize) {
        this.scheduleLoader = Objects.requireNonNull(scheduleLoader);
        this.scheduleClaimer = Objects.requireNonNull(scheduleClaimer);
        this.executionDispatcher = Objects.requireNonNull(executionDispatcher);
        if (defaultBatchSize < 1) {
            throw new IllegalArgumentException("scheduler.engine.batch-size must be >= 1");
        }
        this.defaultBatchSize = defaultBatchSize;
    }

    @Override
    public int tick() {
        return tick(Instant.now(), defaultBatchSize);
    }

    @Override
    @Transactional
    public int tick(Instant now, int batchSize) {
        Instant waterline = Objects.requireNonNull(now, "now");
        List<Schedule> due = scheduleLoader.loadDueForUpdate(waterline, batchSize);
        int processed = 0;
        for (Schedule locked : due) {
            Schedule claimed = scheduleClaimer.claim(locked);
            executionDispatcher.dispatch(claimed);
            processed++;
        }
        log.info("SchedulerEngine tick processed={} at={}", processed, waterline);
        return processed;
    }
}
