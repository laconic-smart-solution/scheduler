package com.laconic.scheduler.executor;

/**
 * Checked-style failure for executor side effects (wrapped as unchecked for Spring friendliness).
 */
public class TaskExecutionException extends RuntimeException {

    private final String errorCode;

    public TaskExecutionException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public TaskExecutionException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
