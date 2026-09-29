package com.gazellio.platform;

import com.gazellio.platform.dto.ApiDtos.ChangeCreateRequest;
import com.gazellio.platform.dto.ApiDtos.IncidentActionRequest;
import com.gazellio.platform.dto.ApiDtos.SecurityIncidentView;
import com.gazellio.platform.model.RemediationTask;
import com.gazellio.platform.repository.RemediationTaskRepository;
import com.gazellio.platform.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;

import static com.gazellio.platform.model.Enums.TaskStage.RELEASE_APPROVAL;
import static org.junit.jupiter.api.Assertions.*;

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
    @Autowired WorkOrderService workOrders;
    @Autowired RemediationTaskRepository taskRepository;

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
        workOrders.incidents();
        workOrders.changes();

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
        assertTimeout(TARGET, () -> workOrders.incidents());
        assertTimeout(TARGET, () -> workOrders.changes());
    }

    @Test
    void vulnerabilityPatchAndWorkOrderLinksFormARealDrillDownChain() {
        assertTrue(findings.library(null, null, null).stream()
                .allMatch(v -> v.patchAvailable() == !v.patches().isEmpty()));

        SecurityIncidentView incident = workOrders.incidents().stream()
                .filter(i -> i.remediationTaskId() == null && i.patchCandidates() != null && !i.patchCandidates().isEmpty())
                .findFirst().orElseThrow();
        SecurityIncidentView dispatched = workOrders.startRemediation(incident.id(),
                new IncidentActionRequest(null, null, incident.patchCandidates().getFirst().id(), "集成测试"));
        assertNotNull(dispatched.remediationTaskId());

        RemediationTask task = taskRepository.findById(dispatched.remediationTaskId()).orElseThrow();
        task.setStage(RELEASE_APPROVAL);
        taskRepository.save(task);
        var change = workOrders.createChange(incident.id(), new ChangeCreateRequest(
                "NORMAL", "生产环境补丁发布", "已完成风险评估", "按批次执行并验证",
                "失败时回退", null, null));

        assertNotNull(change.approvalId());
        assertEquals(incident.id(), change.incidentId());
        assertEquals(change.id(), taskRepository.findById(task.getId()).orElseThrow().getChangeOrderId());
    }
}
