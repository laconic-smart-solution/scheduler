package com.laconic.scheduler.scheduler.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.laconic.scheduler.exception.ResourceNotFoundException;
import com.laconic.scheduler.scheduler.api.dto.CreateScheduleRequest;
import com.laconic.scheduler.scheduler.api.dto.CreateScheduleRequest.ActionRequest;
import com.laconic.scheduler.scheduler.domain.Action;
import com.laconic.scheduler.scheduler.domain.ActionType;
import com.laconic.scheduler.scheduler.domain.Schedule;
import com.laconic.scheduler.scheduler.domain.ScheduleKind;
import com.laconic.scheduler.scheduler.domain.ScheduleStatus;
import com.laconic.scheduler.scheduler.persistence.ActionRepository;
import com.laconic.scheduler.scheduler.persistence.ScheduleRepository;
import com.laconic.scheduler.webhook.WebhookPayload;
import com.laconic.scheduler.webhook.WebhookPayloadParser;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduleApplicationServiceTest {

    @Mock
    private ActionRepository actionRepository;

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private WebhookPayloadParser webhookPayloadParser;

    private ScheduleApplicationService service;

    @BeforeEach
    void setUp() {
        service =
                new ScheduleApplicationService(
                        actionRepository, scheduleRepository, webhookPayloadParser);
    }

    @Test
    void createOnceAt_persistsScheduled() {
        String payload = "{\"url\":\"https://example.com\"}";
        when(webhookPayloadParser.parse(payload))
                .thenReturn(new WebhookPayload("https://example.com", "POST", null, null));
        when(actionRepository.save(any(Action.class))).thenAnswer(inv -> inv.getArgument(0));
        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateScheduleRequest request =
                new CreateScheduleRequest(
                        "key-1",
                        3,
                        ScheduleKind.ONCE_AT,
                        Instant.parse("2026-07-21T12:00:00Z"),
                        null,
                        null,
                        null,
                        new ActionRequest(ActionType.WEBHOOK, payload, null));

        var result = service.create(request);

        assertTrue(result.created());
        assertEquals(ScheduleStatus.SCHEDULED, result.body().status());
        assertEquals(ScheduleKind.ONCE_AT, result.body().scheduleKind());

        ArgumentCaptor<Schedule> captor = ArgumentCaptor.forClass(Schedule.class);
        verify(scheduleRepository).save(captor.capture());
        assertEquals(ScheduleStatus.SCHEDULED, captor.getValue().getStatus());
    }

    @Test
    void create_idempotentReturnsExisting() {
        Action action = new Action(ActionType.WEBHOOK, "{\"url\":\"https://x\"}", null);
        Schedule existing =
                Schedule.onceAt(
                        action.getId(), Instant.parse("2026-07-21T12:00:00Z"), "key-1", 3);
        existing.markScheduled();

        when(scheduleRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));
        when(actionRepository.findById(action.getId())).thenReturn(Optional.of(action));

        CreateScheduleRequest request =
                new CreateScheduleRequest(
                        "key-1",
                        3,
                        ScheduleKind.ONCE_AT,
                        Instant.parse("2026-07-21T12:00:00Z"),
                        null,
                        null,
                        null,
                        new ActionRequest(ActionType.WEBHOOK, "{\"url\":\"https://x\"}", null));

        var result = service.create(request);

        assertFalse(result.created());
        assertEquals(existing.getId(), result.body().id());
    }

    @Test
    void get_missingThrows() {
        when(scheduleRepository.findById("missing")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.get("missing"));
    }
}
