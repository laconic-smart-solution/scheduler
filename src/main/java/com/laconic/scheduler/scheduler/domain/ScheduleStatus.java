package com.laconic.scheduler.scheduler.domain;

/**
 * Explicit lifecycle of a {@link Schedule}.
 * Ambiguous states such as a lone {@code ACTIVE} flag are intentionally avoided.
 */
public enum ScheduleStatus {

    /** Persisted but not yet eligible for the poller. */
    CREATED,

    /** Eligible when {@code nextExecutionAt <= now}. */
    SCHEDULED,

    /** Claimed by an instance; execution in progress. */
    RUNNING,

    /** Last execution succeeded. */
    SUCCESS,

    /** Last execution failed; may move to {@link #RETRYING} or {@link #DEAD}. */
    FAILED,

    /** Waiting for a retry at {@code nextExecutionAt}. */
    RETRYING,

    /** Terminal failure / dead-letter. */
    DEAD,

    /** User cancelled; ignored by loader. */
    CANCELLED,

    /** Temporarily excluded from loader. */
    PAUSED
}
