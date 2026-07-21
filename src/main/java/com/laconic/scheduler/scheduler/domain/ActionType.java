package com.laconic.scheduler.scheduler.domain;

/**
 * Discriminator for {@link Action} payloads.
 * New types require a new {@code TaskExecutor} bean only — not engine changes.
 */
public enum ActionType {

    WEBHOOK

    // Future: EMAIL, SMS, KAFKA, RABBITMQ, SQS, CUSTOM
}
