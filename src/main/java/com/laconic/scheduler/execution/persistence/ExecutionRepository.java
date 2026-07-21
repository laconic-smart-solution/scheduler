package com.laconic.scheduler.execution.persistence;

import com.laconic.scheduler.execution.domain.Execution;
import com.laconic.scheduler.execution.domain.ExecutionStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence port for execution history.
 */
public interface ExecutionRepository extends JpaRepository<Execution, String> {

    List<Execution> findByScheduleIdOrderByAttemptNoAsc(String scheduleId);

    Optional<Execution> findByScheduleIdAndAttemptNo(String scheduleId, int attemptNo);

    List<Execution> findByScheduleIdAndStatus(String scheduleId, ExecutionStatus status);
}
