package com.laconic.scheduler.scheduler.persistence;

import com.laconic.scheduler.scheduler.domain.Schedule;
import com.laconic.scheduler.scheduler.domain.ScheduleStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * CRUD + non-locking lookups for {@link Schedule}.
 * Due-row locking lives in {@link com.laconic.scheduler.scheduler.engine.ScheduleLoader}.
 */
public interface ScheduleRepository extends JpaRepository<Schedule, String> {

    Optional<Schedule> findByIdempotencyKey(String idempotencyKey);

    List<Schedule> findByStatusIn(Collection<ScheduleStatus> statuses);

    /**
     * Peek due schedules without locking (also used as claim candidates before SKIP LOCKED).
     */
    @Query(
            """
            select s from Schedule s
             where s.status in :statuses
               and s.nextExecutionAt is not null
               and s.nextExecutionAt <= :now
             order by s.nextExecutionAt asc
            """)
    List<Schedule> findDueUnlocked(
            @Param("statuses") Collection<ScheduleStatus> statuses,
            @Param("now") Instant now,
            Pageable pageable);
}
