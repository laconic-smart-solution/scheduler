package com.laconic.scheduler.scheduler.engine;

import com.laconic.scheduler.scheduler.domain.Schedule;
import com.laconic.scheduler.scheduler.domain.ScheduleStatus;
import com.laconic.scheduler.scheduler.persistence.ScheduleRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Oracle-backed loader using {@code FOR UPDATE SKIP LOCKED}.
 * <p>
 * Candidate selection uses JPQL (correct Instant binding). Locking uses native SQL
 * because Oracle rejects {@code FETCH FIRST ... FOR UPDATE} (ORA-02014).
 */
@Component
public class OracleScheduleLoader implements ScheduleLoader {

    private static final Logger log = LoggerFactory.getLogger(OracleScheduleLoader.class);

    private static final String LOCK_BY_IDS_SQL =
            """
            SELECT ID
              FROM SCHEDULER_SCHEDULE
             WHERE ID IN (:ids)
             FOR UPDATE SKIP LOCKED
            """;

    private final EntityManager entityManager;
    private final ScheduleRepository scheduleRepository;

    public OracleScheduleLoader(
            EntityManager entityManager, ScheduleRepository scheduleRepository) {
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager");
        this.scheduleRepository = Objects.requireNonNull(scheduleRepository, "scheduleRepository");
    }

    @Override
    @Transactional
    public List<Schedule> loadDueForUpdate(Instant now, int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be >= 1");
        }
        Instant waterline = Objects.requireNonNull(now, "now");

        List<Schedule> candidates =
                scheduleRepository.findDueUnlocked(
                        EnumSet.of(ScheduleStatus.SCHEDULED, ScheduleStatus.RETRYING),
                        waterline,
                        PageRequest.of(0, batchSize));

        if (candidates.isEmpty()) {
            return List.of();
        }

        List<String> ids = candidates.stream().map(Schedule::getId).toList();

        @SuppressWarnings("unchecked")
        List<Object> lockedIds =
                entityManager
                        .createNativeQuery(LOCK_BY_IDS_SQL)
                        .setParameter("ids", ids)
                        .getResultList();

        List<Schedule> locked = new ArrayList<>(lockedIds.size());
        for (Object rawId : lockedIds) {
            String id = String.valueOf(rawId);
            Schedule schedule = entityManager.find(Schedule.class, id);
            if (schedule != null) {
                locked.add(schedule);
            } else {
                log.warn("Locked schedule id {} not found as JPA entity", id);
            }
        }

        log.debug(
                "Due candidates={} locked={} at {}",
                ids.size(),
                locked.size(),
                waterline);
        return locked;
    }
}
