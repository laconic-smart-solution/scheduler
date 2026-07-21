package com.laconic.scheduler;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@Tag("oracle")
@SpringBootTest
class SchedulerApplicationTests {

    @Test
    void contextLoads() {
        // Verifies Spring + Oracle datasource + Hibernate ddl-auto=update
    }
}
