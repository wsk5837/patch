package com.gazellio.platform;

import com.gazellio.platform.dto.ApiDtos.ChangeCreateRequest;
import com.gazellio.platform.dto.ApiDtos.IncidentActionRequest;
import com.gazellio.platform.dto.ApiDtos.SecurityIncidentView;
import com.gazellio.platform.dto.ApiDtos.TaskActionRequest;
import com.gazellio.platform.dto.ApiDtos.ApprovalActionRequest;
import com.gazellio.platform.dto.ApiDtos.BatchScopeRequest;
import com.gazellio.platform.dto.ApiDtos.PatchScheduleRequest;
import com.gazellio.platform.dto.ApiDtos.RoleSaveRequest;
import com.gazellio.platform.dto.ApiDtos.TemplateSaveRequest;
import com.gazellio.platform.dto.ApiDtos.TemplateStepSaveRequest;
import com.gazellio.platform.dto.ApiDtos.UserSaveRequest;
import com.gazellio.platform.model.RemediationTask;
import com.gazellio.platform.model.Asset;
import com.gazellio.platform.model.Finding;
import com.gazellio.platform.model.ApprovalRequest;
import com.gazellio.platform.model.ApprovalStep;
import com.gazellio.platform.repository.RemediationTaskRepository;
import com.gazellio.platform.repository.AssetRepository;
import com.gazellio.platform.repository.FindingRepository;
import com.gazellio.platform.repository.PatchRepository;
import com.gazellio.platform.repository.ApprovalRequestRepository;
import com.gazellio.platform.repository.ApprovalStepRepository;
import com.gazellio.platform.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
    @Autowired AssetRepository assetRepository;
    @Autowired FindingRepository findingRepository;
    @Autowired PatchRepository patchRepository;
    @Autowired ApprovalRequestRepository approvalRequestRepository;
    @Autowired ApprovalStepRepository approvalStepRepository;
    @Autowired AccessControlService accessControl;
    @Autowired com.gazellio.platform.repository.UserAccountRepository userAccounts;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void customerAssetCatalogAndSelectableScopesAreAvailable() {
        Set<String> expected = Set.of("Oracle MySQL", "Apache Tomcat", "Open SSH", "Spring Boot", "PHP",
                "Apache HTTP Server", "Eclipse Jetty", "OpenSSL", "Apache Struts2", "Redis", "Python",
                "MariaDB", "Grafana", "Atlassian Jira", "Samba", "Elasticsearch", "Ruby", "PostgreSQL",
                "Shiro", "MongoDB");
        var rows = assets.list();
        assertEquals(expected, rows.stream().map(item -> item.name()).collect(Collectors.toSet()));
        assertTrue(rows.stream().map(item -> item.assetType()).collect(Collectors.toSet()).size() >= 8);
        assertTrue(rows.stream().noneMatch(item -> "VIRTUAL_MACHINE".equals(item.assetType())));
        var options = assets.scopeOptions();
        assertTrue(options.networkSegments().size() >= 10);
        assertTrue(options.assetTypes().containsAll(Set.of("DATABASE", "MIDDLEWARE", "APPLICATION_RUNTIME")));
    }

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
        assertTrue(java.util.stream.IntStream.rangeClosed(0, 3)
                .mapToObj(offset -> YearMonth.now().plusMonths(offset).toString())
                .anyMatch(month -> !patches.calendar(month).isEmpty()));

        SecurityIncidentView incident = workOrders.incidents().stream()
                .filter(i -> i.remediationTaskId() == null && i.patchCandidates() != null && !i.patchCandidates().isEmpty())
                .findFirst().orElseThrow();
        SecurityIncidentView dispatched = workOrders.startRemediation(incident.id(),
                new IncidentActionRequest(null, null, incident.patchCandidates().getFirst().id(), "集成测试"));
        assertNotNull(dispatched.remediationTaskId());

        RemediationTask task = taskRepository.findById(dispatched.remediationTaskId()).orElseThrow();
        task.setStage(RELEASE_APPROVAL);
        taskRepository.save(task);
        var windowStart=java.time.Instant.now().plus(java.time.Duration.ofHours(1));
        var windowEnd=windowStart.plus(java.time.Duration.ofHours(2));
        var change = workOrders.createChange(incident.id(), new ChangeCreateRequest(
                "NORMAL", "生产环境补丁发布", "已完成风险评估", "按批次执行并验证",
                "失败时回退", windowStart.toString(), windowEnd.toString()));

        assertNotNull(change.approvalId());
        assertEquals(incident.id(), change.incidentId());
        assertEquals(change.id(), taskRepository.findById(task.getId()).orElseThrow().getChangeOrderId());

        var context=org.springframework.security.core.context.SecurityContextHolder.getContext();
        var previousAuthentication=context.getAuthentication();
        context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("ops",""));
        try {
            approvals.reject(change.approvalId(), new ApprovalActionRequest("补充回退验证后重新提交"));
        } finally {
            context.setAuthentication(previousAuthentication);
        }
        var resubmitted = workOrders.resubmitChange(change.id(), new ChangeCreateRequest(
                "NORMAL", "修订后的生产环境补丁发布", "已补充业务风险评估", "按灰度批次执行并验证",
                "已验证快照回退", windowStart.toString(), windowEnd.toString()));
        assertEquals("PENDING_APPROVAL", resubmitted.status());
        assertNotEquals(change.approvalId(), resubmitted.approvalId());
    }

    @Test
    void finalApprovalCommitsEvenWhenPreproductionAutomationCannotStart() {
        String suffix=UUID.randomUUID().toString().substring(0,8);
        var patch=patchRepository.findAll().stream().findFirst().orElseThrow();
        var operator=userAccounts.findByUsername("ops").orElseThrow();
        var approver=userAccounts.findByUsername("approver").orElseThrow();
        var requester=userAccounts.findByUsername("admin").orElseThrow();
        Asset asset=assetRepository.save(Asset.builder().assetCode("APPROVAL-PROD-"+suffix)
                .name("Open SSH").hostname("approval-"+suffix).ipAddress("198.51.100.10")
                .networkSegment("198.51.100.0/24").assetType("SERVER").environment(com.gazellio.platform.model.Enums.EnvironmentType.PROD)
                .businessService("approval-no-preprod-"+suffix).sourceSystem("LOCAL").build());
        Finding finding=findingRepository.save(Finding.builder().assetId(asset.getId()).cveId("CVE-2023-38545")
                .riskScore(9.1).evidence("approval transaction regression fixture").build());
        RemediationTask task=taskRepository.save(RemediationTask.builder().taskNo("RMD-APPROVAL-"+suffix)
                .findingId(finding.getId()).assetId(asset.getId()).patchId(patch.getId()).priority("P1")
                .stage(com.gazellio.platform.model.Enums.TaskStage.RELEASE_APPROVAL)
                .status(com.gazellio.platform.model.Enums.TaskStatus.IN_PROGRESS).changeType(com.gazellio.platform.model.Enums.ChangeType.NORMAL).build());
        ApprovalRequest request=approvalRequestRepository.save(ApprovalRequest.builder().approvalNo("APR-REGRESSION-"+suffix)
                .taskId(task.getId()).changeType(com.gazellio.platform.model.Enums.ChangeType.NORMAL)
                .requestedById(requester.getId()).requestedByName(requester.getDisplayName()).reason("Regression")
                .rollbackPlan("Restore snapshot").build());
        approvalStepRepository.save(ApprovalStep.builder().approvalId(request.getId()).stepOrder(1)
                .roleNameZh("运维负责人").roleNameEn("Operations Lead").approverId(operator.getId())
                .approverName(operator.getDisplayName()).status(com.gazellio.platform.model.Enums.ApprovalStepStatus.PENDING).build());
        approvalStepRepository.save(ApprovalStep.builder().approvalId(request.getId()).stepOrder(2)
                .roleNameZh("发布审批人").roleNameEn("Release Approver").approverId(approver.getId())
                .approverName(approver.getDisplayName()).status(com.gazellio.platform.model.Enums.ApprovalStepStatus.WAITING).build());
        task.setApprovalId(request.getId());taskRepository.save(task);

        var context=org.springframework.security.core.context.SecurityContextHolder.getContext();
        var previousAuthentication=context.getAuthentication();
        try {
            context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("ops",""));
            approvals.approve(request.getId(),new ApprovalActionRequest("Operations approved"));
            context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("approver",""));
            var result=approvals.approve(request.getId(),new ApprovalActionRequest("Release approved"));
            assertEquals("APPROVED",result.status());
            assertEquals("APPROVED",approvals.get(request.getId()).status());
            assertEquals(com.gazellio.platform.model.Enums.TaskStatus.BLOCKED,
                    taskRepository.findById(task.getId()).orElseThrow().getStatus());
        } finally {
            context.setAuthentication(previousAuthentication);
        }
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
        var request = new BatchScopeRequest(patch.id(), List.of("10.70.30.0/24"),
                List.of("TEST"), List.of("SECURITY_COMPONENT"), null, null, true, List.of(),
                10, 5, 5.0, "CIDR integration test", "Sun 01:00-05:00", null);
        var preview = batchPatch.preview(request);
        assertTrue(preview.matchedCount() > 0);
        assertTrue(preview.selectedCount() > 0);
        assertEquals((int) Math.ceil(preview.selectedCount() / 10.0), preview.totalBatches());

        var result = batchPatch.execute(request);
        var run = orchestration.run(result.runId());
        assertEquals(preview.selectedCount(), run.targets().size());
        assertTrue(run.targets().stream().allMatch(target -> target.batchNo() != null));
    }

    @Test
    void rbacUsersUseDatabaseRolesAndInitialPassword(){
        var role=accessControl.createRole(new RoleSaveRequest("AUDITOR_TEST","审计测试角色","Audit Test Role","只读审计角色","Read-only audit role","ALL",true,
                List.of("AUDIT_VIEW","REPORT_VIEW")));
        var user=accessControl.createUser(new UserSaveRequest("audit_tester","审计测试员","audit@example.test","审计部","EMP-TEST",null,"LOCAL",role.id(),true,null));
        assertEquals(Set.of("AUDIT_VIEW","REPORT_VIEW"),Set.copyOf(user.permissions()));
        assertTrue(passwordEncoder.matches("Gazellio@123",userAccounts.findByUsername("audit_tester").orElseThrow().getPasswordHash()));
        accessControl.updateRole(role.id(),new RoleSaveRequest(role.code(),role.nameZh(),role.nameEn(),role.descriptionZh(),role.descriptionEn(),role.dataScope(),true,List.of("AUDIT_VIEW")));
        assertEquals(List.of("AUDIT_VIEW"),accessControl.users().stream().filter(x->x.id().equals(user.id())).findFirst().orElseThrow().permissions());
    }

    @Test
    void patchSchedulesCanBeCreatedAndEdited(){
        var patch=patches.list().getFirst();
        var start=java.time.Instant.now().plus(java.time.Duration.ofDays(2));
        var created=patches.createSchedule(new PatchScheduleRequest("测试排程","Test Schedule",patch.id(),"PROD",start.toString(),start.plusSeconds(7200).toString(),"PLANNED","test"));
        var updated=patches.updateSchedule(created.id(),new PatchScheduleRequest("已批准排程","Approved Schedule",patch.id(),"PROD",start.toString(),start.plusSeconds(10800).toString(),"APPROVED","approved"));
        assertEquals("APPROVED",updated.status());
        assertTrue(patches.calendar(YearMonth.from(start.atZone(java.time.ZoneId.systemDefault())).toString()).stream().anyMatch(event->created.id().equals(event.scheduleId())&&event.editable()));
    }

    @Test
    void orchestrationTemplatesCanBeVersionedAndEdited(){
        var created=orchestration.createTemplate(new TemplateSaveRequest("TEST-EDITOR","编辑器测试","Editor Test","PATCH",true,
                List.of(new TemplateStepSaveRequest("CHECK","前置检查","Pre-check","CHECK",false),new TemplateStepSaveRequest("INSTALL","安装","Install","ACTION",true))));
        var updated=orchestration.updateTemplate(created.id(),new TemplateSaveRequest(created.code(),created.nameZh(),created.nameEn(),created.type(),true,
                List.of(new TemplateStepSaveRequest("CHECK","前置检查","Pre-check","CHECK",false),new TemplateStepSaveRequest("VERIFY","验证","Verify","CHECK",true))));
        assertEquals(created.version()+1,updated.version());
        assertEquals(List.of("CHECK","VERIFY"),updated.steps().stream().map(step->step.code()).toList());
    }
}
