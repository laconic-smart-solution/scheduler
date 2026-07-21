package com.laconic.scheduler.executor;

import com.laconic.scheduler.scheduler.domain.ActionType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Resolves {@link TaskExecutor} beans by {@link ActionType}.
 * Adding a new executor = new Spring bean; no changes to this class or the engine.
 */
@Component
public class ExecutorRegistry {

    private final Map<ActionType, TaskExecutor> byType = new EnumMap<>(ActionType.class);

    public ExecutorRegistry(List<TaskExecutor> executors) {
        for (TaskExecutor executor : Objects.requireNonNull(executors, "executors")) {
            ActionType type = executor.supports();
            TaskExecutor previous = byType.put(type, executor);
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate TaskExecutor for " + type + ": "
                                + previous.getClass().getName()
                                + " and "
                                + executor.getClass().getName());
            }
        }
    }

    /**
     * @throws UnknownActionTypeException if no executor is registered
     */
    public TaskExecutor get(ActionType type) {
        TaskExecutor executor = byType.get(Objects.requireNonNull(type, "type"));
        if (executor == null) {
            throw new UnknownActionTypeException(type);
        }
        return executor;
    }

    public boolean supports(ActionType type) {
        return byType.containsKey(type);
    }
}
