package com.laconic.scheduler.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

/**
 * Ensures Quartz jobs (e.g. {@link com.laconic.scheduler.scheduler.engine.SchedulePollJob})
 * receive Spring constructor injection.
 */
@Configuration
@ConditionalOnProperty(name = "scheduler.poll.enabled", havingValue = "true", matchIfMissing = true)
public class QuartzSpringJobFactoryConfiguration {

    @Bean
    public SpringBeanJobFactory springBeanJobFactory(
            org.springframework.context.ApplicationContext applicationContext) {
        AutowiringSpringBeanJobFactory jobFactory = new AutowiringSpringBeanJobFactory();
        jobFactory.setApplicationContext(applicationContext);
        return jobFactory;
    }

    /**
     * JobFactory that autowires job instances from the Spring context.
     */
    static final class AutowiringSpringBeanJobFactory extends SpringBeanJobFactory
            implements org.springframework.context.ApplicationContextAware {

        private org.springframework.beans.factory.config.AutowireCapableBeanFactory beanFactory;

        @Override
        public void setApplicationContext(
                org.springframework.context.ApplicationContext applicationContext) {
            this.beanFactory = applicationContext.getAutowireCapableBeanFactory();
        }

        @Override
        protected Object createJobInstance(org.quartz.spi.TriggerFiredBundle bundle)
                throws Exception {
            Object job = super.createJobInstance(bundle);
            beanFactory.autowireBean(job);
            return job;
        }
    }
}
