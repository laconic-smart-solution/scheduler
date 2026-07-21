package com.laconic.scheduler.executor;

import com.laconic.scheduler.execution.context.ExecutionContext;
import com.laconic.scheduler.scheduler.domain.ActionType;

/**
 * Strategy for executing an {@link com.laconic.scheduler.scheduler.domain.Action}.
 * Implementations are discovered by {@link ExecutorRegistry}; the engine never switches on type.
 */
public interface TaskExecutor {

    /**
     * @return the action type this executor handles
     */
    ActionType supports();

    /**
     * Perform the side effect. Throw {@link TaskExecutionException} (or any exception)
     * to signal failure to the dispatcher.
     */
    void execute(ExecutionContext context) throws TaskExecutionException;
}
