package com.laconic.scheduler.execution.recorder;

import com.laconic.scheduler.execution.domain.Execution;
import com.laconic.scheduler.scheduler.domain.Action;
import com.laconic.scheduler.scheduler.domain.Schedule;
import java.time.Instant;

/**
 * Persists execution attempts and their outcomes.
 */
public interface ExecutionRecorder {

    Execution start(Schedule schedule, Action action, Instant triggeredTime);

    void markSuccess(Execution execution, String detailJson);

    void markFailed(Execution execution, String errorCode, String detailJson);
}
