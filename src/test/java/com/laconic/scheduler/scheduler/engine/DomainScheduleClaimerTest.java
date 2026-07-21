package com.laconic.scheduler.scheduler.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.laconic.scheduler.scheduler.domain.Schedule;
import com.laconic.scheduler.scheduler.domain.ScheduleStatus;
import com.laconic.scheduler.scheduler.persistence.ScheduleRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DomainScheduleClaimerTest {

    @Mock
    private ScheduleRepository scheduleRepository;

    private DomainScheduleClaimer claimer;

    @BeforeEach
    void setUp() {
        claimer = new DomainScheduleClaimer(scheduleRepository);
    }

    @Test
    void claim_marksRunningAndSaves() {
        Schedule schedule =
                Schedule.onceAt("action-1", Instant.parse("2026-07-21T10:00:00Z"), "idem", 3);
        schedule.markScheduled();

        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> inv.getArgument(0));

        Schedule claimed = claimer.claim(schedule);

        assertEquals(ScheduleStatus.RUNNING, claimed.getStatus());
        assertEquals(1, claimed.getAttemptCount());
        verify(scheduleRepository).save(schedule);
    }

    @Test
    void claim_rejectsNull() {
        assertThrows(NullPointerException.class, () -> claimer.claim(null));
    }
}
