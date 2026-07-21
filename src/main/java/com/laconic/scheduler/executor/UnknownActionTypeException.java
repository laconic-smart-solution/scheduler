package com.laconic.scheduler.executor;

import com.laconic.scheduler.scheduler.domain.ActionType;

/**
 * Raised when {@link ExecutorRegistry} has no bean for an action type.
 */
public class UnknownActionTypeException extends RuntimeException {

    private final ActionType actionType;

    public UnknownActionTypeException(ActionType actionType) {
        super("No TaskExecutor registered for action type: " + actionType);
        this.actionType = actionType;
    }

    public ActionType getActionType() {
        return actionType;
    }
}
