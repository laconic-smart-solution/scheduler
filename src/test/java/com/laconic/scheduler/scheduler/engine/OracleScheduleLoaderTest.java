package com.laconic.scheduler.scheduler.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.laconic.scheduler.scheduler.domain.Schedule;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OracleScheduleLoaderTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query nativeQuery;

    private OracleScheduleLoader loader;

    @BeforeEach
    void setUp() {
        loader = new OracleScheduleLoader(entityManager);
    }

    @Test
    void loadDueForUpdate_mapsLockedIdsToEntities() {
        Instant now = Instant.parse("2026-07-21T10:00:00Z");
        Schedule schedule = Schedule.onceAt("a1", now, "k1", 2);
        schedule.markScheduled();

        when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
        when(nativeQuery.setParameter(eq("now"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(nativeQuery);
        when(nativeQuery.setParameter(eq("limit"), eq(10))).thenReturn(nativeQuery);
        when(nativeQuery.getResultList()).thenReturn(List.of("sched-1"));
        when(entityManager.find(Schedule.class, "sched-1")).thenReturn(schedule);

        List<Schedule> locked = loader.loadDueForUpdate(now, 10);

        assertEquals(1, locked.size());
        assertEquals(schedule, locked.getFirst());
        verify(entityManager).createNativeQuery(anyString());
    }

    @Test
    void loadDueForUpdate_rejectsInvalidBatchSize() {
        assertThrows(
                IllegalArgumentException.class,
                () -> loader.loadDueForUpdate(Instant.now(), 0));
    }

    @Test
    void loadDueForUpdate_returnsEmptyWhenNoRows() {
        when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
        when(nativeQuery.setParameter(eq("now"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(nativeQuery);
        when(nativeQuery.setParameter(eq("limit"), eq(5))).thenReturn(nativeQuery);
        when(nativeQuery.getResultList()).thenReturn(List.of());

        List<Schedule> locked = loader.loadDueForUpdate(Instant.now(), 5);

        assertTrue(locked.isEmpty());
    }
}
