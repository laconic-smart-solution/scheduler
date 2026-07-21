package com.laconic.scheduler.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.laconic.scheduler.scheduler.engine.SchedulePollJob;
import org.junit.jupiter.api.Test;
import org.quartz.JobDetail;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;

class QuartzPollConfigurationTest {

    @Test
    void buildsDurablePollJobAndRepeatingTrigger() {
        QuartzPollConfiguration config = new QuartzPollConfiguration();
        JobDetail job = config.schedulePollJobDetail();
        Trigger trigger = config.schedulePollTrigger(job, 5000L);

        assertEquals(SchedulePollJob.class, job.getJobClass());
        assertTrue(job.isDurable());
        assertEquals(QuartzPollConfiguration.JOB_IDENTITY, job.getKey().getName());
        assertEquals(QuartzPollConfiguration.TRIGGER_IDENTITY, trigger.getKey().getName());
        assertNotNull(trigger);
        assertTrue(trigger instanceof SimpleTrigger);
        assertEquals(5000L, ((SimpleTrigger) trigger).getRepeatInterval());
    }
}
