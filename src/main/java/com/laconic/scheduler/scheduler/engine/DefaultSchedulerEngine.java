package com.laconic.scheduler.scheduler.engine;

import com.laconic.scheduler.execution.dispatcher.ExecutionDispatcher;
import com.laconic.scheduler.health.SchedulerRuntimeProbe;
import com.laconic.scheduler.metrics.SchedulerMetrics;
import com.laconic.scheduler.retry.StaleRunningReaper;
import com.laconic.scheduler.scheduler.domain.Schedule;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default engine pipeline.
 * <p>
 * Quartz only calls {@link #tick()}; it does not own schedule rows.
 * Each tick optionally reaps stale {@code RUNNING} rows, then load → claim → dispatch.
 */
@Service
public class DefaultSchedulerEngine implements SchedulerEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultSchedulerEngine.class);

    private final ScheduleLoader scheduleLoader;
    private final ScheduleClaimer scheduleClaimer;
    private final ExecutionDispatcher executionDispatcher;
    private final Optional<StaleRunningReaper> staleRunningReaper;
    private final SchedulerRuntimeProbe runtimeProbe;
    private final SchedulerMetrics schedulerMetrics;
    private final int defaultBatchSize;

    public DefaultSchedulerEngine(
            ScheduleLoader scheduleLoader,
            ScheduleClaimer scheduleClaimer,
            ExecutionDispatcher executionDispatcher,
            Optional<StaleRunningReaper> staleRunningReaper,
            SchedulerRuntimeProbe runtimeProbe,
            SchedulerMetrics schedulerMetrics,
            @Value("${scheduler.engine.batch-size:50}") int defaultBatchSize) {
        this.scheduleLoader = Objects.requireNonNull(scheduleLoader);
        this.scheduleClaimer = Objects.requireNonNull(scheduleClaimer);
        this.executionDispatcher = Objects.requireNonNull(executionDispatcher);
        this.staleRunningReaper = Objects.requireNonNullElse(staleRunningReaper, Optional.empty());
        this.runtimeProbe = Objects.requireNonNull(runtimeProbe);
        this.schedulerMetrics = Objects.requireNonNull(schedulerMetrics);
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
        long started = System.currentTimeMillis();
        try {
            int reaped =
                    staleRunningReaper.map(reaper -> reaper.reap(waterline)).orElse(0);

            List<Schedule> due = scheduleLoader.loadDueForUpdate(waterline, batchSize);
            long lagMs = computePollLagMs(due, waterline);
            int processed = 0;
            for (Schedule locked : due) {
                Schedule claimed = scheduleClaimer.claim(locked);
                executionDispatcher.dispatch(claimed);
                processed++;
            }

            long durationMs = System.currentTimeMillis() - started;
            runtimeProbe.recordSuccess(waterline, processed, reaped);
            schedulerMetrics.recordTick(processed, reaped, durationMs, lagMs, due.size());
            log.info(
                    "SchedulerEngine tick processed={} reaped={} at={}",
                    processed,
                    reaped,
                    waterline);
            return processed;
        } catch (RuntimeException ex) {
            runtimeProbe.recordFailure(waterline, ex.getMessage());
            throw ex;
        }
    }

    private static long computePollLagMs(List<Schedule> due, Instant now) {
        long lag = 0L;
        for (Schedule schedule : due) {
            Instant next = schedule.getNextExecutionAt();
            if (next != null && next.isBefore(now)) {
                lag = Math.max(lag, now.toEpochMilli() - next.toEpochMilli());
            }
        }
        return lag;
    }
}
