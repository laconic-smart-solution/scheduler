package com.laconic.scheduler.execution.domain;

/**
 * Outcome of a single execution attempt.
 */
public enum ExecutionStatus {

    RUNNING,
    SUCCESS,
    FAILED,
    SKIPPED
}
