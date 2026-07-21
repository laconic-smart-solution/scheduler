package com.laconic.scheduler.execution.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ExecutionTest {

    @Test
    void startAndSucceed() {
        Instant t = Instant.parse("2026-07-21T10:00:00Z");
        Execution execution = Execution.start("s1", "a1", 1, t, t);
        assertEquals(ExecutionStatus.RUNNING, execution.getStatus());

        execution.markSuccess("{\"ok\":true}");
        assertEquals(ExecutionStatus.SUCCESS, execution.getStatus());
    }

    @Test
    void cannotSucceedTwice() {
        Instant t = Instant.now();
        Execution execution = Execution.start("s1", "a1", 1, t, t);
        execution.markFailed("TIMEOUT", "{}");
        assertThrows(IllegalStateException.class, () -> execution.markSuccess("{}"));
    }
}
