package com.laconic.scheduler.webhook;

import com.laconic.scheduler.execution.context.ExecutionContext;
import com.laconic.scheduler.executor.TaskExecutor;
import com.laconic.scheduler.executor.TaskExecutionException;
import com.laconic.scheduler.scheduler.domain.ActionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Placeholder webhook executor for Feature 3 pipeline verification.
 * Feature 6 will replace the body with WebClient HTTP calls.
 */
@Component
public class WebhookTaskExecutor implements TaskExecutor {

    private static final Logger log = LoggerFactory.getLogger(WebhookTaskExecutor.class);

    @Override
    public ActionType supports() {
        return ActionType.WEBHOOK;
    }

    @Override
    public void execute(ExecutionContext context) throws TaskExecutionException {
        if (context.payloadJson() == null || context.payloadJson().isBlank()) {
            throw new TaskExecutionException("EMPTY_PAYLOAD", "Webhook payloadJson is blank");
        }
        log.info(
                "Webhook execute scheduleId={} executionId={} payloadLength={}",
                context.scheduleId(),
                context.executionId(),
                context.payloadJson().length());
        // Real HTTP call arrives in Feature 6.
    }
}
