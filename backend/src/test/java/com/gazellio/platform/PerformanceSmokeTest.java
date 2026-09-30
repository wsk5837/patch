package com.gazellio.platform;

import com.gazellio.platform.dto.ApiDtos.ChangeCreateRequest;
import com.gazellio.platform.dto.ApiDtos.IncidentActionRequest;
import com.gazellio.platform.dto.ApiDtos.SecurityIncidentView;
import com.gazellio.platform.dto.ApiDtos.TaskActionRequest;
import com.gazellio.platform.dto.ApiDtos.ApprovalActionRequest;
import com.gazellio.platform.dto.ApiDtos.BatchScopeRequest;
import com.gazellio.platform.model.RemediationTask;
import com.gazellio.platform.repository.RemediationTaskRepository;
import com.gazellio.platform.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
    @Autowired BatchPatchService batchPatch;
    @Autowired RemediationTaskRepository taskRepository;

    @Test
    void warmListEndpointsStayWithinOneSecond() {
        // First calls initialize query plans; the timed pass represents a warm Render instance.
        assets.list();
        findings.library(null, null, null, null, 0, 30);
        findings.list(null, null, null);
        patches.list();
        patches.calendar(null);
        tasks.list();
        approvals.list();
        orchestration.templates();
        orchestration.runs();
        dashboard.dashboard();
        dashboard.report();
        workOrders.incidents();
        workOrders.changes();

        assertTimeout(TARGET, () -> assets.list());
        assertTimeout(TARGET, () -> findings.library(null, null, null, null, 0, 30));
        assertTimeout(TARGET, () -> findings.list(null, null, null));
        assertTimeout(TARGET, () -> patches.list());
        assertTimeout(TARGET, () -> patches.calendar(null));
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
        assertTrue(findings.library(null, null, null, null, 0, 30).items().stream()
                .allMatch(v -> v.patchAvailable() == !v.patches().isEmpty()));
        assertTrue(findings.library(null, null, null, null, 0, 30).items().stream()
                .anyMatch(v -> v.patchAvailable() && !v.patches().isEmpty()));
        assertTrue(patches.list().stream().allMatch(p -> p.signatureIssuer() != null
                && p.signatureFingerprint() != null && p.testEvidence() != null));

        Set<String> priorities = workOrders.incidents().stream()
                .map(SecurityIncidentView::priority).collect(Collectors.toSet());
        assertTrue(priorities.containsAll(Set.of("P1", "P2", "P3", "P4")), priorities.toString());
        assertFalse(patches.calendar(null).isEmpty());

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

        approvals.reject(change.approvalId(), new ApprovalActionRequest("补充回退验证后重新提交"));
        var resubmitted = workOrders.resubmitChange(change.id(), new ChangeCreateRequest(
                "NORMAL", "修订后的生产环境补丁发布", "已补充业务风险评估", "按灰度批次执行并验证",
                "已验证快照回退", null, null));
        assertEquals("PENDING_APPROVAL", resubmitted.status());
        assertNotEquals(change.approvalId(), resubmitted.approvalId());
    }

    @Test
    void vulnerabilityLibraryUsesServerSidePagination() {
        var first=findings.library(null,null,null,null,0,30);
        var second=findings.library(null,null,null,null,1,30);
        assertEquals(30,first.items().size());
        assertTrue(first.totalElements()>=50);
        assertEquals(1,first.page());
        assertEquals(2,second.page());
        Set<String> firstIds=first.items().stream().map(v->v.cveId()).collect(Collectors.toSet());
        assertTrue(second.items().stream().noneMatch(v->firstIds.contains(v.cveId())));
    }

    @Test
    void patchInstallationAndRetestUseDifferentAutomationTemplates() {
        var templateViews = orchestration.templates();
        var install = templateViews.stream().filter(t -> "PATCH-STANDARD".equals(t.code())).findFirst().orElseThrow();
        var retest = templateViews.stream().filter(t -> "PATCH-RETEST".equals(t.code())).findFirst().orElseThrow();
        Set<String> installCodes = install.steps().stream().map(s -> s.code()).collect(Collectors.toSet());
        Set<String> retestCodes = retest.steps().stream().map(s -> s.code()).collect(Collectors.toSet());

        assertTrue(installCodes.containsAll(Set.of("DOWNLOAD", "INSTALL", "HEALTH", "EVIDENCE")));
        assertFalse(installCodes.contains("RESCAN"));
        assertTrue(retestCodes.containsAll(Set.of("INSTALL_STATE", "VERSION_PROBE", "VULN_PROBE", "EFFECT_CHECK")));
        assertFalse(retestCodes.contains("DOWNLOAD"));
        assertFalse(retestCodes.contains("INSTALL"));

        RemediationTask validationTask = taskRepository.findTop200ByOrderByUpdatedAtDesc().stream()
                .filter(task -> task.getStage() == com.gazellio.platform.model.Enums.TaskStage.APP_VERIFY)
                .findFirst().orElseThrow();
        var updated = tasks.action(validationTask.getId(), "verify-test",
                new TaskActionRequest("PASS", "应用健康检查通过", null, null, null, null));
        assertEquals("TEST_RESCAN", updated.stage());
        assertNull(updated.lastRetestMode());
        var started = tasks.action(validationTask.getId(), "start-auto-retest",
                new TaskActionRequest(null, "使用 Agent 定向扫描", "AUTO", null, null, null));
        assertEquals("AUTO", started.lastRetestMode());
        assertEquals("RUNNING", started.lastRetestResult());
        var retestRun = orchestration.run(started.latestRunId());
        assertNull(retestRun.deploymentId());
        assertEquals("PATCH-RETEST", retestRun.templateCode());
        assertFalse(retestRun.targets().isEmpty());
    }

    @Test
    void manualRetestRecordsEvidenceAndMovesToNextGate() {
        RemediationTask validationTask = taskRepository.findTop200ByOrderByUpdatedAtDesc().stream()
                .filter(task -> task.getStage() == com.gazellio.platform.model.Enums.TaskStage.PREPROD_VERIFY)
                .findFirst().orElseThrow();
        tasks.action(validationTask.getId(), "verify-preprod",
                new TaskActionRequest("PASS", "Application healthy", null, null, null, null));
        var completed = tasks.action(validationTask.getId(), "submit-manual-retest",
                new TaskActionRequest("PASS", "Version and probe evidence reviewed", "MANUAL", null, null, null));
        assertEquals("MANUAL", completed.lastRetestMode());
        assertEquals("PASSED", completed.lastRetestResult());
        assertEquals("PROD_PATCH", completed.stage());
        assertNotNull(completed.lastRetestedAt());
    }

    @Test
    void cidrScopeCreatesRealBatchesAndExecutionTargets() {
        var patch = patches.list().stream()
                .filter(item -> item.product() != null && item.product().toLowerCase().contains("openssh"))
                .findFirst().orElseThrow();
        var request = new BatchScopeRequest(patch.id(), List.of("10.20.10.0/24", "10.30.10.0/24"),
                List.of(), List.of("VIRTUAL_MACHINE"), null, null, true, List.of(),
                10, 5, 5.0, "CIDR integration test", "Sun 01:00-05:00");
        var preview = batchPatch.preview(request);
        assertTrue(preview.matchedCount() > 0);
        assertTrue(preview.selectedCount() > 0);
        assertEquals((int) Math.ceil(preview.selectedCount() / 10.0), preview.totalBatches());

        var result = batchPatch.execute(request);
        var run = orchestration.run(result.runId());
        assertEquals(preview.selectedCount(), run.targets().size());
        assertTrue(run.targets().stream().allMatch(target -> target.batchNo() != null));
    }
}
