package com.laconic.scheduler.scheduler.api;

import com.laconic.scheduler.scheduler.api.dto.CreateScheduleRequest;
import com.laconic.scheduler.scheduler.api.dto.ScheduleResponse;
import com.laconic.scheduler.scheduler.service.ScheduleApplicationService;
import com.laconic.scheduler.scheduler.service.ScheduleApplicationService.CreateResult;
import jakarta.validation.Valid;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP API for scheduling actions (WHEN + WHAT).
 */
@RestController
@RequestMapping("/api/v1/schedules")
public class ScheduleController {

    private final ScheduleApplicationService scheduleApplicationService;

    public ScheduleController(ScheduleApplicationService scheduleApplicationService) {
        this.scheduleApplicationService = Objects.requireNonNull(scheduleApplicationService);
    }

    /**
     * Create a schedule (and its action). Idempotent when {@code idempotencyKey} is supplied.
     */
    @PostMapping
    public ResponseEntity<ScheduleResponse> create(@Valid @RequestBody CreateScheduleRequest request) {
        CreateResult result = scheduleApplicationService.create(request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.body());
    }

    /** Fetch one schedule by id. */
    @GetMapping("/{scheduleId}")
    public ScheduleResponse get(@PathVariable String scheduleId) {
        return scheduleApplicationService.get(scheduleId);
    }

    /** Cancel a schedule so the poller ignores it. */
    @PostMapping("/{scheduleId}/cancel")
    public ScheduleResponse cancel(@PathVariable String scheduleId) {
        return scheduleApplicationService.cancel(scheduleId);
    }
}
