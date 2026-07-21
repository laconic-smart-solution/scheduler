package com.laconic.scheduler.scheduler.engine;

import com.laconic.scheduler.scheduler.domain.Schedule;
import com.laconic.scheduler.scheduler.persistence.ScheduleRepository;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Claims a locked due schedule by moving it to {@code RUNNING} via domain rules.
 */
@Component
public class DomainScheduleClaimer implements ScheduleClaimer {

    private static final Logger log = LoggerFactory.getLogger(DomainScheduleClaimer.class);

    private final ScheduleRepository scheduleRepository;

    public DomainScheduleClaimer(ScheduleRepository scheduleRepository) {
        this.scheduleRepository = Objects.requireNonNull(scheduleRepository);
    }

    @Override
    @Transactional
    public Schedule claim(Schedule lockedSchedule) {
        Schedule schedule = Objects.requireNonNull(lockedSchedule, "lockedSchedule");
        schedule.markRunning();
        Schedule saved = scheduleRepository.save(schedule);
        log.debug(
                "Claimed schedule {} attempt {} version {}",
                saved.getId(),
                saved.getAttemptCount(),
                saved.getVersion());
        return saved;
    }
}
