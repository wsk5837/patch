package com.gazellio.platform.service;

import com.gazellio.platform.dto.ApiDtos.*;
import com.gazellio.platform.model.*;
import com.gazellio.platform.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
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
    private final SettingsService settings;
    private final SlaPolicyService slaPolicy;

    public List<FindingView> findingOptions(String stage) {
        List<Finding> candidates = findings.findTop200ByOrderByRiskScoreDescLastSeenAtDesc();
        boolean changeStage = "CHANGE".equalsIgnoreCase(stage);
        List<Finding> eligible = candidates.stream().filter(f -> {
            if (!changeStage) return f.getSecurityIncidentId() == null
                    && Set.of(FindingStatus.CONFIRMED, FindingStatus.IN_REMEDIATION).contains(f.getStatus());
            if (f.getRemediationTaskId() == null || f.getSecurityIncidentId() == null) return false;
            RemediationTask task = tasks.findById(f.getRemediationTaskId()).orElse(null);
            return task != null && task.getStage() == TaskStage.RELEASE_APPROVAL && task.getChangeOrderId() == null;
        }).toList();
        return view.findingViews(eligible);
    }

    public List<SecurityIncidentView> incidents() {
        List<SecurityIncident> rows = incidents.findActive(PageRequest.of(0,200));
        Set<Long> pendingConfirmation = findings.findAllById(rows.stream().map(SecurityIncident::getFindingId).toList())
                .stream()
                .filter(f -> f.getStatus() == FindingStatus.NEW || f.getStatus() == FindingStatus.REOPENED)
                .map(Finding::getId)
                .collect(java.util.stream.Collectors.toSet());
        return view.incidentViews(rows.stream()
                .filter(i -> !pendingConfirmation.contains(i.getFindingId()))
                .toList());
    }

    public SecurityIncidentView incident(Long id) {
        SecurityIncident incident = requireIncident(id);
        Finding finding = findings.findById(incident.getFindingId()).orElseThrow();
        if (finding.getStatus() == FindingStatus.NEW || finding.getStatus() == FindingStatus.REOPENED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Finding must be confirmed before opening its security incident");
        }
        return view.incident(incident);
    }

    public List<ChangeWorkOrderView> changes() {
        return view.changeViews(changes.findActive(PageRequest.of(0,200)));
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
        if (finding.getStatus() != FindingStatus.CONFIRMED && finding.getStatus() != FindingStatus.IN_REMEDIATION) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Finding must be confirmed before a security incident can be created");
        }
        SecurityIncident existing = finding.getSecurityIncidentId() == null ? incidents.findByFindingId(finding.getId()).orElse(null)
                : incidents.findById(finding.getSecurityIncidentId()).orElse(null);
        if (existing != null) {
            if (Set.of(IncidentStatus.CLOSED, IncidentStatus.RESOLVED, IncidentStatus.EXEMPTED,
                    IncidentStatus.FALSE_POSITIVE).contains(existing.getStatus())) {
                existing.setStatus(existing.getOwnerId() == null ? IncidentStatus.OPEN : IncidentStatus.ASSIGNED);
                existing.setResolvedAt(null);
                existing.setClosedAt(null);
                existing.setDecisionReason(null);
                existing.setUpdatedAt(Instant.now());
                incidents.save(existing);
            }
            if (!Objects.equals(finding.getSecurityIncidentId(), existing.getId())) {
                finding.setSecurityIncidentId(existing.getId());
                findings.save(finding);
            }
            return existing;
        }
        Asset asset = assets.findById(finding.getAssetId()).orElseThrow();
        VulnerabilityDefinition vulnerability = vulnerabilities.findById(finding.getCveId()).orElseThrow();
        SlaDecisionView decision = slaPolicy.decide(vulnerability, asset);
        String priority = decision.priority();
        long days = decision.slaDays();
        long stamp = System.currentTimeMillis();
        SecurityIncident incident = incidents.save(SecurityIncident.builder()
                .incidentNo("SEC-" + stamp)
                .externalTicketNo("AITSM-SEC-" + stamp)
                .findingId(finding.getId())
                .linkedFindingIds(String.valueOf(finding.getId()))
                .assetId(finding.getAssetId())
                .priority(priority)
                .status(asset.getOwnerId() == null ? IncidentStatus.OPEN : IncidentStatus.ASSIGNED)
                .ownerId(asset.getOwnerId())
                .ownerName(asset.getOwnerName())
                .dueAt(Instant.now().plus(Duration.ofDays(days)))
                .build());
        finding.setSecurityIncidentId(incident.getId());
        findings.save(finding);
        audit.log("SECURITY_INCIDENT", incident.getId(), "CREATE",
                "漏洞确认后生成安全事件工单 " + incident.getIncidentNo(),
                "Security incident created after finding confirmation " + incident.getIncidentNo(), actor());
        return incident;
    }

    @Transactional
    public SecurityIncidentView createIncident(AggregateIncidentCreateRequest req) {
        List<Long> ids = normalizedIds(req == null ? null : req.findingIds());
        if (ids.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select at least one finding");
        List<Finding> selected = findings.findAllById(ids);
        if (selected.size() != ids.size()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "One or more findings were not found");
        for (Finding finding : selected) {
            if (!Set.of(FindingStatus.CONFIRMED, FindingStatus.IN_REMEDIATION).contains(finding.getStatus()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "All selected findings must be confirmed");
            if (finding.getSecurityIncidentId() != null)
                throw new ResponseStatusException(HttpStatus.CONFLICT, finding.getCveId() + " is already linked to an incident");
        }
        Finding primary = selected.stream().max(Comparator.comparingDouble(f -> f.getRiskScore() == null ? 0 : f.getRiskScore())).orElseThrow();
        SecurityIncident incident = ensureForFinding(primary);
        incident.setLinkedFindingIds(joinIds(ids));
        if (req.ownerName() != null && !req.ownerName().isBlank()) {
            incident.setOwnerId(req.ownerId()); incident.setOwnerName(req.ownerName().trim());
            incident.setStatus(IncidentStatus.ASSIGNED);
        }
        String highest = incident.getPriority();
        int shortestDays = Integer.MAX_VALUE;
        for (Finding finding : selected) {
            Asset asset = assets.findById(finding.getAssetId()).orElseThrow();
            VulnerabilityDefinition vulnerability = vulnerabilities.findById(finding.getCveId()).orElseThrow();
            SlaDecisionView decision = slaPolicy.decide(vulnerability, asset);
            if (priorityRank(decision.priority()) < priorityRank(highest)) highest = decision.priority();
            shortestDays = Math.min(shortestDays, decision.slaDays());
            finding.setSecurityIncidentId(incident.getId());
            if (req.ownerName() != null && !req.ownerName().isBlank()) { finding.setOwnerId(req.ownerId()); finding.setOwnerName(req.ownerName().trim()); }
            findings.save(finding);
        }
        incident.setPriority(highest);
        incident.setDueAt(Instant.now().plus(Duration.ofDays(shortestDays)));
        incident.setUpdatedAt(Instant.now());
        incidents.save(incident);
        audit.log("SECURITY_INCIDENT", incident.getId(), "CREATE_AGGREGATE",
                "新建聚合安全事件，关联 " + ids.size() + " 条漏洞", "Aggregate security incident created for " + ids.size() + " findings", actor());
        return view.incident(incident);
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
        if (finding.getStatus() != FindingStatus.CONFIRMED && finding.getStatus() != FindingStatus.IN_REMEDIATION) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Finding must be confirmed before remediation starts");
        }
        if (Set.of(IncidentStatus.CLOSED, IncidentStatus.RESOLVED, IncidentStatus.EXEMPTED,
                IncidentStatus.FALSE_POSITIVE).contains(incident.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Closed incident cannot start remediation");
        }
        Long patchId = req == null ? null : req.patchId();
        if (patchId == null) patchId = patchCves.findByCveId(finding.getCveId()).stream()
                .map(PatchCve::getPatchId).filter(pid -> patches.existsById(pid)).findFirst().orElse(null);
        if (patchId == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "No applicable patch selected");
        patches.findById(patchId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patch not found"));
        if (!patchCves.existsByPatchIdAndCveId(patchId, finding.getCveId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Selected patch is not mapped to " + finding.getCveId());
        }
        if (req == null || req.reason() == null || req.reason().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Patch selection rationale is required");
        }
        RemediationTask task = tasks.save(RemediationTask.builder()
                .taskNo("RMD-PENDING-" + UUID.randomUUID())
                .findingId(finding.getId()).securityIncidentId(incident.getId())
                .patchId(patchId).assetId(incident.getAssetId()).ownerId(incident.getOwnerId())
                .ownerName(incident.getOwnerName()).priority(incident.getPriority())
                .stage(TaskStage.ASSIGNED).status(TaskStatus.OPEN).dueAt(incident.getDueAt()).build());
        task.setTaskNo("RMD-"+String.format("%06d",task.getId()));
        tasks.save(task);
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
        if (incident.getChangeOrderId() != null) {
            ChangeWorkOrder existing = changes.findById(incident.getChangeOrderId()).orElse(null);
            if (existing != null && existing.getStatus() == ChangeStatus.REJECTED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Rejected change must be revised and resubmitted");
            }
            return change(incident.getChangeOrderId());
        }
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
        validateChangeRequest(req);
        Instant maintenanceStart = parseInstant(req.maintenanceStart());
        Instant maintenanceEnd = parseInstant(req.maintenanceEnd());
        long stamp = System.currentTimeMillis();
        ChangeWorkOrder change = changes.save(ChangeWorkOrder.builder()
                .changeNo("CHG-" + stamp).externalChangeNo("AITSM-CHG-" + stamp)
                .incidentId(incident.getId()).remediationTaskId(task.getId()).changeType(type)
                .status(ChangeStatus.PENDING_APPROVAL).summary(req.summary().trim())
                .riskAssessment(req.riskAssessment().trim()).implementationPlan(req.implementationPlan().trim())
                .rollbackPlan(req.rollbackPlan().trim()).maintenanceStart(maintenanceStart)
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
        audit.log("SECURITY_INCIDENT", incident.getId(), "TRIGGER_CHANGE",
                incident.getIncidentNo()+" 已触发变更 "+change.getChangeNo(),
                incident.getIncidentNo()+" triggered change "+change.getChangeNo(), actor());
        return view.change(change);
    }

    @Transactional
    public ChangeWorkOrderView createChange(AggregateChangeCreateRequest req) {
        List<Long> ids = normalizedIds(req == null ? null : req.findingIds());
        if (ids.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select at least one finding");
        List<Finding> selected = findings.findAllById(ids);
        if (selected.size() != ids.size()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "One or more findings were not found");
        for (Finding finding : selected) {
            if (finding.getSecurityIncidentId() == null || finding.getRemediationTaskId() == null)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Each finding must have an incident and remediation task");
            RemediationTask task = tasks.findById(finding.getRemediationTaskId()).orElseThrow();
            if (task.getStage() != TaskStage.RELEASE_APPROVAL || task.getChangeOrderId() != null)
                throw new ResponseStatusException(HttpStatus.CONFLICT, finding.getCveId() + " has not reached the release approval gate");
        }
        Finding primary = selected.stream().max(Comparator.comparingDouble(f -> f.getRiskScore() == null ? 0 : f.getRiskScore())).orElseThrow();
        ChangeCreateRequest base = new ChangeCreateRequest(req.changeType(), req.summary(), req.riskAssessment(),
                req.implementationPlan(), req.rollbackPlan(), req.maintenanceStart(), req.maintenanceEnd());
        ChangeWorkOrderView createdView = createChange(primary.getSecurityIncidentId(), base);
        ChangeWorkOrder change = changes.findById(createdView.id()).orElseThrow();
        change.setLinkedFindingIds(joinIds(ids));
        changes.save(change);
        for (Finding finding : selected) {
            RemediationTask task = tasks.findById(finding.getRemediationTaskId()).orElseThrow();
            SecurityIncident incident = incidents.findById(finding.getSecurityIncidentId()).orElseThrow();
            task.setChangeOrderId(change.getId()); task.setApprovalId(change.getApprovalId()); task.setUpdatedAt(Instant.now()); tasks.save(task);
            incident.setChangeOrderId(change.getId()); incident.setStatus(IncidentStatus.PENDING_CHANGE); incident.setUpdatedAt(Instant.now()); incidents.save(incident);
        }
        audit.log("CHANGE", change.getId(), "CREATE_AGGREGATE",
                "新建聚合变更，关联 " + ids.size() + " 条漏洞", "Aggregate change created for " + ids.size() + " findings", actor());
        return view.change(change);
    }

    @Transactional
    public ChangeWorkOrderView resubmitChange(Long changeId, ChangeCreateRequest req) {
        ChangeWorkOrder change = changes.findById(changeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Change not found"));
        if (change.getStatus() != ChangeStatus.REJECTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a rejected change can be resubmitted");
        }
        RemediationTask task = tasks.findById(change.getRemediationTaskId()).orElseThrow();
        if (task.getStage() != TaskStage.RELEASE_APPROVAL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task is no longer at the production release gate");
        }
        ChangeType type;
        try { type = ChangeType.valueOf(req.changeType().toUpperCase(Locale.ROOT)); }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid change type"); }
        validateChangeRequest(req);
        Instant maintenanceStart = parseInstant(req.maintenanceStart());
        Instant maintenanceEnd = parseInstant(req.maintenanceEnd());
        change.setChangeType(type);
        change.setSummary(req.summary().trim());
        change.setRiskAssessment(req.riskAssessment().trim());
        change.setImplementationPlan(req.implementationPlan().trim());
        change.setRollbackPlan(req.rollbackPlan().trim());
        change.setMaintenanceStart(maintenanceStart);
        change.setMaintenanceEnd(maintenanceEnd);
        change.setStatus(ChangeStatus.PENDING_APPROVAL);
        change.setUpdatedAt(Instant.now());
        change.setClosedAt(null);
        changes.save(change);

        ApprovalRequest approval = approvalService.createForTask(task, type,
                change.getRiskAssessment() == null ? change.getSummary() : change.getRiskAssessment(),
                change.getRollbackPlan() == null ? "失败时暂停执行并恢复至最近回退点。" : change.getRollbackPlan());
        approval.setChangeOrderId(change.getId());
        approvalRequests.save(approval);
        change.setApprovalId(approval.getId());
        changes.save(change);
        task.setChangeOrderId(change.getId());
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setUpdatedAt(Instant.now());
        tasks.save(task);
        incidents.findById(change.getIncidentId()).ifPresent(incident -> {
            incident.setStatus(IncidentStatus.PENDING_CHANGE);
            incident.setUpdatedAt(Instant.now());
            incidents.save(incident);
        });
        audit.log("CHANGE", change.getId(), "RESUBMIT",
                "已修订并重新提交变更审批 " + change.getChangeNo(),
                "Change revised and resubmitted for approval " + change.getChangeNo(), actor());
        return view.change(change);
    }

    private SecurityIncident requireIncident(Long id) {
        return incidents.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private static List<Long> normalizedIds(List<Long> ids) {
        if (ids == null) return List.of();
        return ids.stream().filter(Objects::nonNull).distinct().toList();
    }
    private static String joinIds(List<Long> ids) { return ids.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")); }
    private static int priorityRank(String value) { return switch (value) { case "P1" -> 1; case "P2" -> 2; case "P3" -> 3; default -> 4; }; }

    private static String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private static void validateChangeRequest(ChangeCreateRequest req) {
        if (req == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Change request is required");
        if (blank(req.riskAssessment()) == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Risk assessment is required");
        if (blank(req.implementationPlan()) == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Implementation plan is required");
        if (blank(req.rollbackPlan()) == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rollback plan is required");
        Instant start = parseInstant(req.maintenanceStart());
        Instant end = parseInstant(req.maintenanceEnd());
        if (start == null || end == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Maintenance window is required");
        if (!end.isAfter(start))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Maintenance end must be after start");
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Instant.parse(value); }
        catch (Exception ignored) {
            try { return LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant(); }
            catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid maintenance time"); }
        }
    }

    private String actor() {
        try { return currentUser.name(); } catch (Exception ignored) { return "ANOWX"; }
    }
}
