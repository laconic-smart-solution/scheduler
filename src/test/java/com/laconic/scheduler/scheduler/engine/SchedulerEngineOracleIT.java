package com.laconic.scheduler.scheduler.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.laconic.scheduler.execution.domain.ExecutionStatus;
import com.laconic.scheduler.execution.persistence.ExecutionRepository;
import com.laconic.scheduler.scheduler.domain.Action;
import com.laconic.scheduler.scheduler.domain.ActionType;
import com.laconic.scheduler.scheduler.domain.Schedule;
import com.laconic.scheduler.scheduler.domain.ScheduleStatus;
import com.laconic.scheduler.scheduler.persistence.ActionRepository;
import com.laconic.scheduler.scheduler.persistence.ScheduleRepository;
import com.laconic.scheduler.webhook.WebhookTaskExecutor;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Integration test against Oracle {@code LCN_SCHEDULER} with a real HTTP webhook (MockWebServer).
 */
@Tag("oracle")
@SpringBootTest
class SchedulerEngineOracleIT {

    @Autowired
    private SchedulerEngine schedulerEngine;

    @Autowired
    private ActionRepository actionRepository;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private ExecutionRepository executionRepository;

    private MockWebServer mockWebServer;

    @BeforeEach
    void setUp() throws IOException {
        assumeTrue(actionRepository != null, "Spring context / Oracle datasource not available");
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (mockWebServer != null) {
            mockWebServer.shutdown();
        }
    }

    @Test
    void tick_claimsDueWebhookAndMarksSuccess() throws Exception {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true}"));

        String hookUrl = mockWebServer.url("/scheduler-hook").toString();
        String key = "it-" + UUID.randomUUID();
        Action action =
                new Action(
                        ActionType.WEBHOOK,
                        "{\"url\":\""
                                + hookUrl
                                + "\",\"method\":\"POST\",\"body\":{\"source\":\"oracle-it\"}}",
                        null);
        actionRepository.save(action);

        Instant dueAt = Instant.now().minusSeconds(30);
        Schedule schedule = Schedule.onceAt(action.getId(), dueAt, key, 3);
        schedule.markScheduled();
        scheduleRepository.saveAndFlush(schedule);

        int processed = schedulerEngine.tick(Instant.now(), 20);

        assertTrue(processed >= 1, "expected at least one schedule processed");

        Schedule after = scheduleRepository.findById(schedule.getId()).orElseThrow();
        assertEquals(ScheduleStatus.SUCCESS, after.getStatus());

        var executions = executionRepository.findByScheduleIdOrderByAttemptNoAsc(schedule.getId());
        assertEquals(1, executions.size());
        assertEquals(ExecutionStatus.SUCCESS, executions.getFirst().getStatus());

        RecordedRequest request = mockWebServer.takeRequest(3, TimeUnit.SECONDS);
        assertEquals("POST", request.getMethod());
        assertEquals("/scheduler-hook", request.getPath());
        assertEquals(
                schedule.getId(), request.getHeader(WebhookTaskExecutor.HEADER_SCHEDULE_ID));
    }
}
