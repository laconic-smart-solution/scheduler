package com.laconic.scheduler;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Full context load requires Oracle + Flyway.
 * Domain unit tests cover Feature 1 without a database.
 */
@SpringBootTest
@Disabled("Requires Oracle datasource; enable when local DB is available")
class SchedulerApplicationTests {

    @Test
    void contextLoads() {
        // Intentionally empty — verifies Spring context + Flyway when enabled.
    }
}
