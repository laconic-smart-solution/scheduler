package com.laconic.scheduler.scheduler.engine;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.quartz.JobExecutionContext;

class SchedulePollJobTest {

    @Test
    void executeInternal_delegatesToEngine() throws Exception {
        SchedulerEngine engine = mock(SchedulerEngine.class);
        when(engine.tick()).thenReturn(2);
        JobExecutionContext ctx = mock(JobExecutionContext.class);

        SchedulePollJob job = new SchedulePollJob();
        job.setSchedulerEngine(engine);
        job.executeInternal(ctx);

        verify(engine).tick();
    }

    @Test
    void executeInternal_withoutEngine_doesNotThrow() throws Exception {
        SchedulePollJob job = new SchedulePollJob();
        job.executeInternal(mock(JobExecutionContext.class));
        // no engine wired — logs error, does not throw
    }

    @Test
    void executeInternal_swallowsEngineFailure() throws Exception {
        SchedulerEngine engine = mock(SchedulerEngine.class);
        when(engine.tick()).thenThrow(new RuntimeException("boom"));

        SchedulePollJob job = new SchedulePollJob();
        job.setSchedulerEngine(engine);
        job.executeInternal(mock(JobExecutionContext.class));

        verify(engine).tick();
    }
}
