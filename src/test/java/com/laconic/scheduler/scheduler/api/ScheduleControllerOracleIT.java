package com.laconic.scheduler.scheduler.api;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.laconic.scheduler.scheduler.domain.ScheduleStatus;
import java.time.Instant;
import java.util.UUID;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Tag("oracle")
@SpringBootTest
@AutoConfigureMockMvc
class ScheduleControllerOracleIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private MockWebServer mockWebServer;

    @BeforeEach
    void setUp() throws Exception {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));
    }

    @AfterEach
    void tearDown() throws Exception {
        mockWebServer.shutdown();
    }

    @Test
    void createGetCancel_flow() throws Exception {
        String hookUrl = mockWebServer.url("/api-hook").toString();
        String idem = "api-" + UUID.randomUUID();
        Instant runAt = Instant.now().plusSeconds(3600);

        String body =
                """
                {
                  "idempotencyKey": "%s",
                  "maxAttempts": 3,
                  "scheduleKind": "ONCE_AT",
                  "runAt": "%s",
                  "action": {
                    "type": "WEBHOOK",
                    "payloadJson": "{\\"url\\":\\"%s\\",\\"method\\":\\"POST\\",\\"body\\":{\\"x\\":1}}"
                  }
                }
                """
                        .formatted(idem, runAt.toString(), hookUrl);

        MvcResult create =
                mockMvc.perform(
                                post("/api/v1/schedules")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.status", is(ScheduleStatus.SCHEDULED.name())))
                        .andExpect(jsonPath("$.action.type", is("WEBHOOK")))
                        .andReturn();

        JsonNode created = objectMapper.readTree(create.getResponse().getContentAsString());
        String scheduleId = created.get("id").asString();

        mockMvc.perform(get("/api/v1/schedules/" + scheduleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(scheduleId)));

        // idempotent replay
        mockMvc.perform(
                        post("/api/v1/schedules")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(scheduleId)));

        mockMvc.perform(post("/api/v1/schedules/" + scheduleId + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is(ScheduleStatus.CANCELLED.name())));
    }
}
