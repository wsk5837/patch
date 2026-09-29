package com.gazellio.platform;

import com.gazellio.platform.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTimeout;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:gazellio-performance;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.seed-demo-data=true",
        "app.cisa-kev-sync-on-startup=false",
        "spring.task.scheduling.enabled=false"
})
class PerformanceSmokeTest {
    private static final Duration TARGET = Duration.ofSeconds(1);

    @Autowired AssetService assets;
    @Autowired FindingService findings;
    @Autowired PatchService patches;
    @Autowired TaskService tasks;
    @Autowired ApprovalService approvals;
    @Autowired OrchestrationService orchestration;
    @Autowired DashboardReportService dashboard;

    @Test
    void warmListEndpointsStayWithinOneSecond() {
        // First calls initialize query plans; the timed pass represents a warm Render instance.
        assets.list();
        findings.library(null, null, null);
        findings.list(null, null, null);
        patches.list();
        tasks.list();
        approvals.list();
        orchestration.templates();
        orchestration.runs();
        dashboard.dashboard();
        dashboard.report();

        assertTimeout(TARGET, () -> assets.list());
        assertTimeout(TARGET, () -> findings.library(null, null, null));
        assertTimeout(TARGET, () -> findings.list(null, null, null));
        assertTimeout(TARGET, () -> patches.list());
        assertTimeout(TARGET, () -> tasks.list());
        assertTimeout(TARGET, () -> approvals.list());
        assertTimeout(TARGET, () -> orchestration.templates());
        assertTimeout(TARGET, () -> orchestration.runs());
        assertTimeout(TARGET, () -> dashboard.dashboard());
        assertTimeout(TARGET, () -> dashboard.report());
    }
}
