package com.gazellio.platform.service;

import com.gazellio.platform.dto.ApiDtos.*;
import com.gazellio.platform.model.*;
import com.gazellio.platform.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.*;

import static com.gazellio.platform.model.Enums.*;

@Service
@RequiredArgsConstructor
public class WorkOrderService {
    private final SecurityIncidentRepository incidents;
    private final ChangeWorkOrderRepository changes;
    private final FindingRepository findings;
    private final VulnerabilityDefinitionRepository vulnerabilities;
    private final AssetRepository assets;
    private final PatchCveRepository patchCves;
    private final PatchRepository patches;
    private final RemediationTaskRepository tasks;
    private final ApprovalRequestRepository approvalRequests;
    private final ApprovalService approvalService;
    private final ViewService view;
    private final AuditService audit;
    private final CurrentUserService currentUser;

    public List<SecurityIncidentView> incidents() {
        return view.incidentViews(incidents.findTop200ByOrderByUpdatedAtDesc());
    }

    public SecurityIncidentView incident(Long id) { return view.incident(requireIncident(id)); }

    public List<ChangeWorkOrderView> changes() {
        return view.changeViews(changes.findTop200ByOrderByUpdatedAtDesc());
    }

    public ChangeWorkOrderView change(Long id) {
        return view.change(changes.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    @Transactional
    public SecurityIncident ensureForFinding(Long findingId) {
        Finding finding = findings.findById(findingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Finding not found"));
        return ensureForFinding(finding);
    }

    @Transactional
    public SecurityIncident ensureForFinding(Finding finding) {
        SecurityIncident existing = incidents.findByFindingId(finding.getId()).orElse(null);
        if (existing != null) return existing;
        Asset asset = assets.findById(finding.getAssetId()).orElseThrow();
        VulnerabilityDefinition vulnerability = vulnerabilities.findById(finding.getCveId()).orElseThrow();
        String priority = priority(vulnerability, asset);
        long days = switch (priority) { case "P1" -> 3; case "P2" -> 7; case "P3" -> 30; default -> 90; };
        long stamp = System.currentTimeMillis();
        SecurityIncident incident = incidents.save(SecurityIncident.builder()
                .incidentNo("SEC-" + stamp)
                .externalTicketNo("AITSM-SEC-" + stamp)
                .findingId(finding.getId())
                .assetId(finding.getAssetId())
                .priority(priority)
                .status(asset.getOwnerId() == null ? IncidentStatus.OPEN : IncidentStatus.ASSIGNED)
                .ownerId(asset.getOwnerId())
                .ownerName(asset.getOwnerName())
                .dueAt(Instant.now().plus(Duration.ofDays(days)))
                .build());
        finding.setSecurityIncidentId(incident.getId());
        if (finding.getStatus() == FindingStatus.NEW || finding.getStatus() == FindingStatus.REOPENED) {
            finding.setStatus(FindingStatus.CONFIRMED);
        }
        findings.save(finding);
        audit.log("SECURITY_INCIDENT", incident.getId(), "CREATE",
                "漏洞实例已同步生成安全事件工单 " + incident.getIncidentNo(),
                "Security incident created from finding " + incident.getIncidentNo(), actor());
        return incident;
    }

    @Transactional
    public SecurityIncidentView assign(Long id, IncidentActionRequest req) {
        if (req == null || req.ownerName() == null || req.ownerName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Owner is required");
        }
        SecurityIncident incident = requireIncident(id);
        incident.setOwnerId(req.ownerId());
        incident.setOwnerName(req.ownerName().trim());
        incident.setStatus(incident.getRemediationTaskId() == null ? IncidentStatus.ASSIGNED : IncidentStatus.IN_REMEDIATION);
        incident.setUpdatedAt(Instant.now());
        incidents.save(incident);
        findings.findById(incident.getFindingId()).ifPresent(f -> {
            f.setOwnerId(req.ownerId()); f.setOwnerName(req.ownerName().trim()); findings.save(f);
        });
        if (incident.getRemediationTaskId() != null) tasks.findById(incident.getRemediationTaskId()).ifPresent(t -> {
            t.setOwnerId(req.ownerId()); t.setOwnerName(req.ownerName().trim()); t.setUpdatedAt(Instant.now()); tasks.save(t);
        });
        audit.log("SECURITY_INCIDENT", id, "ASSIGN", "安全事件已分派给 " + req.ownerName(),
                "Security incident assigned to " + req.ownerName(), actor());
        return view.incident(incident);
    }

    @Transactional
    public SecurityIncidentView startRemediation(Long id, IncidentActionRequest req) {
        SecurityIncident incident = requireIncident(id);
        if (incident.getRemediationTaskId() != null) return view.incident(incident);
        Finding finding = findings.findById(incident.getFindingId()).orElseThrow();
        Long patchId = req == null ? null : req.patchId();
        if (patchId == null) patchId = patchCves.findByCveId(finding.getCveId()).stream()
                .map(PatchCve::getPatchId).filter(pid -> patches.existsById(pid)).findFirst().orElse(null);
        if (patchId == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "No applicable patch selected");
        patches.findById(patchId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patch not found"));
        RemediationTask task = tasks.save(RemediationTask.builder()
                .taskNo("RMD-" + System.currentTimeMillis())
                .findingId(finding.getId()).securityIncidentId(incident.getId())
                .patchId(patchId).assetId(incident.getAssetId()).ownerId(incident.getOwnerId())
                .ownerName(incident.getOwnerName()).priority(incident.getPriority())
                .stage(TaskStage.ASSIGNED).status(TaskStatus.OPEN).dueAt(incident.getDueAt()).build());
        incident.setRemediationTaskId(task.getId());
        incident.setStatus(IncidentStatus.IN_REMEDIATION);
        incident.setDecisionReason(req == null ? null : req.reason());
        incident.setUpdatedAt(Instant.now());
        incidents.save(incident);
        finding.setRemediationTaskId(task.getId());
        finding.setStatus(FindingStatus.IN_REMEDIATION);
        findings.save(finding);
        audit.log("SECURITY_INCIDENT", id, "START_REMEDIATION",
                "安全事件已生成补丁处置任务 " + task.getTaskNo(),
                "Patch remediation task created from security incident " + task.getTaskNo(), actor());
        return view.incident(incident);
    }

    @Transactional
    public ChangeWorkOrderView createChange(Long incidentId, ChangeCreateRequest req) {
        SecurityIncident incident = requireIncident(incidentId);
        if (incident.getChangeOrderId() != null) return change(incident.getChangeOrderId());
        if (incident.getRemediationTaskId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Remediation task is required");
        }
        RemediationTask task = tasks.findById(incident.getRemediationTaskId()).orElseThrow();
        if (task.getStage() != TaskStage.RELEASE_APPROVAL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Test patch, application validation and test rescan must pass before change creation");
        }
        ChangeType type;
        try { type = ChangeType.valueOf(req.changeType().toUpperCase(Locale.ROOT)); }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid change type"); }
        Instant maintenanceStart = parseInstant(req.maintenanceStart());
        Instant maintenanceEnd = parseInstant(req.maintenanceEnd());
        if (maintenanceStart != null && maintenanceEnd != null && !maintenanceEnd.isAfter(maintenanceStart)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Maintenance end must be after start");
        }
        long stamp = System.currentTimeMillis();
        ChangeWorkOrder change = changes.save(ChangeWorkOrder.builder()
                .changeNo("CHG-" + stamp).externalChangeNo("AITSM-CHG-" + stamp)
                .incidentId(incident.getId()).remediationTaskId(task.getId()).changeType(type)
                .status(ChangeStatus.PENDING_APPROVAL).summary(req.summary().trim())
                .riskAssessment(blank(req.riskAssessment())).implementationPlan(blank(req.implementationPlan()))
                .rollbackPlan(blank(req.rollbackPlan())).maintenanceStart(maintenanceStart)
                .maintenanceEnd(maintenanceEnd).build());
        ApprovalRequest approval = approvalService.createForTask(task, type,
                change.getRiskAssessment() == null ? change.getSummary() : change.getRiskAssessment(),
                change.getRollbackPlan() == null ? "失败时暂停执行并恢复至最近回退点。" : change.getRollbackPlan());
        approval.setChangeOrderId(change.getId());
        approvalRequests.save(approval);
        change.setApprovalId(approval.getId());
        change.setUpdatedAt(Instant.now());
        changes.save(change);
        task.setSecurityIncidentId(incident.getId());
        task.setChangeOrderId(change.getId());
        tasks.save(task);
        incident.setChangeOrderId(change.getId());
        incident.setStatus(IncidentStatus.PENDING_CHANGE);
        incident.setUpdatedAt(Instant.now());
        incidents.save(incident);
        audit.log("CHANGE", change.getId(), "CREATE",
                "安全事件触发生产变更工单 " + change.getChangeNo(),
                "Production change created from security incident " + change.getChangeNo(), actor());
        return view.change(change);
    }

    private SecurityIncident requireIncident(Long id) {
        return incidents.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private String priority(VulnerabilityDefinition vulnerability, Asset asset) {
        if (vulnerability.isKev() || vulnerability.getSeverity() == Severity.CRITICAL) return "P1";
        if (vulnerability.getSeverity() == Severity.HIGH || asset.getCriticality() >= 5) return "P2";
        if (vulnerability.getSeverity() == Severity.MEDIUM) return "P3";
        return "P4";
    }

    private static String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Instant.parse(value); }
        catch (Exception ignored) {
            try { return LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant(); }
            catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid maintenance time"); }
        }
    }

    private String actor() {
        try { return currentUser.name(); } catch (Exception ignored) { return "Gazellio"; }
    }
}
