package com.laconic.scheduler.scheduler.domain;

/**
 * How the schedule determines the next fire time.
 */
public enum ScheduleKind {

    /** Single execution at {@link Schedule#getRunAt()}. */
    ONCE_AT,

    /** Recurring execution driven by a cron expression. */
    CRON
}
