package com.laconic.scheduler.scheduler.engine;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;

/**
 * Single Quartz job that wakes the {@link SchedulerEngine}.
 * <p>
 * Quartz is only a timer — it does not store business schedules.
 * {@link DisallowConcurrentExecution} prevents overlapping ticks on one JVM.
 * <p>
 * Uses setter injection because Quartz instantiates the job via a no-arg constructor;
 * {@link org.springframework.scheduling.quartz.SpringBeanJobFactory} then autowires it.
 */
@DisallowConcurrentExecution
public class SchedulePollJob extends QuartzJobBean {

    private static final Logger log = LoggerFactory.getLogger(SchedulePollJob.class);

    private SchedulerEngine schedulerEngine;

    public SchedulePollJob() {
        // Quartz
    }

    @Autowired
    public void setSchedulerEngine(SchedulerEngine schedulerEngine) {
        this.schedulerEngine = schedulerEngine;
    }

    @Override
    protected void executeInternal(JobExecutionContext context) {
        if (schedulerEngine == null) {
            log.error("SchedulePollJob missing SchedulerEngine — SpringBeanJobFactory not wired?");
            return;
        }
        try {
            int processed = schedulerEngine.tick();
            log.debug(
                    "SchedulePollJob fired fireTime={} processed={}",
                    context.getFireTime(),
                    processed);
        } catch (Exception ex) {
            log.error("SchedulePollJob tick failed: {}", ex.getMessage(), ex);
        }
    }
}
