package com.laconic.scheduler.scheduler.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.laconic.scheduler.scheduler.domain.Schedule;
import com.laconic.scheduler.scheduler.persistence.ScheduleRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class OracleScheduleLoaderTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private Query lockQuery;

    private OracleScheduleLoader loader;

    @BeforeEach
    void setUp() {
        loader = new OracleScheduleLoader(entityManager, scheduleRepository);
    }

    @Test
    void loadDueForUpdate_selectsThenLocks() {
        Instant now = Instant.parse("2026-07-21T10:00:00Z");
        Schedule schedule = Schedule.onceAt("a1", now, "k1", 2);
        schedule.markScheduled();

        when(scheduleRepository.findDueUnlocked(any(), eq(now), any(Pageable.class)))
                .thenReturn(List.of(schedule));
        when(entityManager.createNativeQuery(anyString())).thenReturn(lockQuery);
        when(lockQuery.setParameter(eq("ids"), eq(List.of(schedule.getId()))))
                .thenReturn(lockQuery);
        when(lockQuery.getResultList()).thenReturn(List.of(schedule.getId()));
        when(entityManager.find(Schedule.class, schedule.getId())).thenReturn(schedule);

        List<Schedule> locked = loader.loadDueForUpdate(now, 10);

        assertEquals(1, locked.size());
        assertEquals(schedule, locked.getFirst());
        verify(entityManager).find(Schedule.class, schedule.getId());
    }

    @Test
    void loadDueForUpdate_rejectsInvalidBatchSize() {
        assertThrows(
                IllegalArgumentException.class,
                () -> loader.loadDueForUpdate(Instant.now(), 0));
    }

    @Test
    void loadDueForUpdate_returnsEmptyWhenNoCandidates() {
        when(scheduleRepository.findDueUnlocked(any(), any(), any(Pageable.class)))
                .thenReturn(List.of());

        assertTrue(loader.loadDueForUpdate(Instant.now(), 5).isEmpty());
    }
}
