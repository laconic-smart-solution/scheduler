package com.laconic.scheduler.retry;

import com.laconic.scheduler.execution.domain.Execution;
import com.laconic.scheduler.execution.domain.ExecutionStatus;
import com.laconic.scheduler.execution.persistence.ExecutionRepository;
import com.laconic.scheduler.scheduler.domain.Schedule;
import com.laconic.scheduler.scheduler.domain.ScheduleStatus;
import com.laconic.scheduler.scheduler.persistence.ScheduleRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recovers schedules stuck in {@link ScheduleStatus#RUNNING} past the lease window
 * (e.g. JVM crash mid-dispatch). Mirrors due-row locking with {@code FOR UPDATE SKIP LOCKED}.
 */
@Service
@ConditionalOnProperty(name = "scheduler.reaper.enabled", havingValue = "true", matchIfMissing = true)
public class StaleRunningReaper {

    public static final String ERROR_CODE = "STALE_RUNNING_REAPED";

    private static final Logger log = LoggerFactory.getLogger(StaleRunningReaper.class);

    private static final String LOCK_BY_IDS_SQL =
            """
            SELECT ID
              FROM SCHEDULER_SCHEDULE
             WHERE ID IN (:ids)
             FOR UPDATE SKIP LOCKED
            """;

    private final EntityManager entityManager;
    private final ScheduleRepository scheduleRepository;
    private final ExecutionRepository executionRepository;
    private final ScheduleFailureApplier failureApplier;
    private final long staleAfterMs;
    private final int batchSize;

    public StaleRunningReaper(
            EntityManager entityManager,
            ScheduleRepository scheduleRepository,
            ExecutionRepository executionRepository,
            ScheduleFailureApplier failureApplier,
            @Value("${scheduler.reaper.stale-after-ms:300000}") long staleAfterMs,
            @Value("${scheduler.reaper.batch-size:50}") int batchSize) {
        this.entityManager = Objects.requireNonNull(entityManager);
        this.scheduleRepository = Objects.requireNonNull(scheduleRepository);
        this.executionRepository = Objects.requireNonNull(executionRepository);
        this.failureApplier = Objects.requireNonNull(failureApplier);
        if (staleAfterMs < 1000L) {
            throw new IllegalArgumentException("scheduler.reaper.stale-after-ms must be >= 1000");
        }
        if (batchSize < 1) {
            throw new IllegalArgumentException("scheduler.reaper.batch-size must be >= 1");
        }
        this.staleAfterMs = staleAfterMs;
        this.batchSize = batchSize;
    }

    @Transactional
    public int reap(Instant now) {
        Instant waterline = Objects.requireNonNull(now, "now");
        Instant staleBefore = waterline.minusMillis(staleAfterMs);

        List<Schedule> candidates =
                scheduleRepository.findStaleRunningUnlocked(
                        ScheduleStatus.RUNNING, staleBefore, PageRequest.of(0, batchSize));
        if (candidates.isEmpty()) {
            return 0;
        }

        List<String> ids = candidates.stream().map(Schedule::getId).toList();
        @SuppressWarnings("unchecked")
        List<Object> lockedIds =
                entityManager
                        .createNativeQuery(LOCK_BY_IDS_SQL)
                        .setParameter("ids", ids)
                        .getResultList();

        int reaped = 0;
        for (Object rawId : lockedIds) {
            String id = String.valueOf(rawId);
            Schedule schedule = entityManager.find(Schedule.class, id);
            if (schedule == null || schedule.getStatus() != ScheduleStatus.RUNNING) {
                continue;
            }
            closeOpenExecutions(schedule);
            failureApplier.applyFailure(schedule, waterline);
            scheduleRepository.save(schedule);
            reaped++;
            log.warn(
                    "Reaped stale RUNNING scheduleId={} status={} attempt={}/{}",
                    schedule.getId(),
                    schedule.getStatus(),
                    schedule.getAttemptCount(),
                    schedule.getMaxAttempts());
        }

        if (reaped > 0) {
            log.info("StaleRunningReaper reaped={} staleBefore={}", reaped, staleBefore);
        }
        return reaped;
    }

    private void closeOpenExecutions(Schedule schedule) {
        List<Execution> open =
                executionRepository.findByScheduleIdAndStatus(
                        schedule.getId(), ExecutionStatus.RUNNING);
        for (Execution execution : open) {
            execution.markFailed(
                    ERROR_CODE, "{\"reason\":\"stale RUNNING past lease; reaped\"}");
            executionRepository.save(execution);
        }
    }
}
