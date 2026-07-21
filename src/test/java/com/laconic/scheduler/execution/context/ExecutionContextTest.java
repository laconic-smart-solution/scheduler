package com.laconic.scheduler.execution.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.laconic.scheduler.scheduler.domain.ActionType;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ExecutionContextTest {

    @Test
    void createsImmutableContext() {
        Instant scheduled = Instant.parse("2026-07-21T10:00:00Z");
        Instant triggered = Instant.parse("2026-07-21T10:00:01Z");

        ExecutionContext ctx =
                new ExecutionContext(
                        "s1",
                        "e1",
                        "a1",
                        ActionType.WEBHOOK,
                        scheduled,
                        triggered,
                        0,
                        "{\"url\":\"https://example.com\"}",
                        null);

        assertEquals(ActionType.WEBHOOK, ctx.actionType());
        assertEquals(0, ctx.retryCount());
    }

    @Test
    void rejectsNegativeRetryCount() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ExecutionContext(
                                "s1",
                                "e1",
                                "a1",
                                ActionType.WEBHOOK,
                                Instant.now(),
                                Instant.now(),
                                -1,
                                "{}",
                                null));
    }
}
