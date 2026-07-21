package com.laconic.scheduler.execution.recorder;

import com.laconic.scheduler.execution.domain.Execution;
import com.laconic.scheduler.execution.persistence.ExecutionRepository;
import com.laconic.scheduler.scheduler.domain.Action;
import com.laconic.scheduler.scheduler.domain.Schedule;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaExecutionRecorder implements ExecutionRecorder {

    private final ExecutionRepository executionRepository;

    public JpaExecutionRecorder(ExecutionRepository executionRepository) {
        this.executionRepository = Objects.requireNonNull(executionRepository);
    }

    @Override
    @Transactional
    public Execution start(Schedule schedule, Action action, Instant triggeredTime) {
        Instant scheduledTime =
                schedule.getNextExecutionAt() != null
                        ? schedule.getNextExecutionAt()
                        : triggeredTime;
        // After claim, attemptCount already includes this attempt.
        Execution execution =
                Execution.start(
                        schedule.getId(),
                        action.getId(),
                        schedule.getAttemptCount(),
                        scheduledTime,
                        triggeredTime);
        return executionRepository.save(execution);
    }

    @Override
    @Transactional
    public void markSuccess(Execution execution, String detailJson) {
        execution.markSuccess(detailJson);
        executionRepository.save(execution);
    }

    @Override
    @Transactional
    public void markFailed(Execution execution, String errorCode, String detailJson) {
        execution.markFailed(errorCode, detailJson);
        executionRepository.save(execution);
    }
}
