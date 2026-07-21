package com.laconic.scheduler.config;

import com.laconic.scheduler.scheduler.engine.SchedulePollJob;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the single in-memory Quartz poll trigger.
 * Business schedules remain in Oracle; Quartz only calls {@code SchedulerEngine.tick()}.
 */
@Configuration
@ConditionalOnProperty(name = "scheduler.poll.enabled", havingValue = "true", matchIfMissing = true)
public class QuartzPollConfiguration {

    public static final String JOB_IDENTITY = "schedulePollJob";
    public static final String TRIGGER_IDENTITY = "schedulePollTrigger";

    @Bean
    public JobDetail schedulePollJobDetail() {
        return JobBuilder.newJob(SchedulePollJob.class)
                .withIdentity(JOB_IDENTITY)
                .withDescription("Wakes SchedulerEngine on a fixed interval")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger schedulePollTrigger(
            JobDetail schedulePollJobDetail,
            @Value("${scheduler.poll.interval-ms:5000}") long intervalMs) {
        if (intervalMs < 1000L) {
            throw new IllegalArgumentException("scheduler.poll.interval-ms must be >= 1000");
        }
        return TriggerBuilder.newTrigger()
                .forJob(schedulePollJobDetail)
                .withIdentity(TRIGGER_IDENTITY)
                .withDescription("Fixed-interval poll wake")
                .withSchedule(
                        SimpleScheduleBuilder.simpleSchedule()
                                .withIntervalInMilliseconds(intervalMs)
                                .repeatForever()
                                .withMisfireHandlingInstructionNextWithRemainingCount())
                .startNow()
                .build();
    }
}
