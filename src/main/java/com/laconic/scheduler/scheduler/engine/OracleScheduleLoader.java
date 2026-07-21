package com.laconic.scheduler.scheduler.engine;

import com.laconic.scheduler.scheduler.domain.Schedule;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Oracle-backed loader using {@code FOR UPDATE SKIP LOCKED}.
 * <p>
 * Why this design: Quartz only wakes the poller; Oracle decides which schedules
 * are due. {@code SKIP LOCKED} lets other instances skip rows already claimed
 * in an open transaction — required for horizontal scale without duplicate fires.
 */
@Component
public class OracleScheduleLoader implements ScheduleLoader {

    private static final Logger log = LoggerFactory.getLogger(OracleScheduleLoader.class);

    private static final String LOCK_DUE_SQL =
            """
            SELECT ID
              FROM SCHEDULER_SCHEDULE
             WHERE STATUS IN ('SCHEDULED', 'RETRYING')
               AND NEXT_EXECUTION_AT IS NOT NULL
               AND NEXT_EXECUTION_AT <= :now
             ORDER BY NEXT_EXECUTION_AT
             FETCH FIRST :limit ROWS ONLY
             FOR UPDATE SKIP LOCKED
            """;

    private final EntityManager entityManager;

    public OracleScheduleLoader(EntityManager entityManager) {
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager");
    }

    @Override
    @Transactional
    public List<Schedule> loadDueForUpdate(Instant now, int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be >= 1");
        }
        Instant waterline = Objects.requireNonNull(now, "now");

        @SuppressWarnings("unchecked")
        List<Object> ids =
                entityManager
                        .createNativeQuery(LOCK_DUE_SQL)
                        .setParameter("now", Timestamp.from(waterline))
                        .setParameter("limit", batchSize)
                        .getResultList();

        List<Schedule> locked = new ArrayList<>(ids.size());
        for (Object rawId : ids) {
            String id = String.valueOf(rawId);
            Schedule schedule = entityManager.find(Schedule.class, id);
            if (schedule != null) {
                locked.add(schedule);
            } else {
                log.warn("Locked schedule id {} not found as JPA entity", id);
            }
        }

        log.debug("Locked {} due schedule(s) at {}", locked.size(), waterline);
        return locked;
    }
}
