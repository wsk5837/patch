package com.gazellio.platform.service;

import com.gazellio.platform.dto.ApiDtos.*;
import com.gazellio.platform.model.*;
import com.gazellio.platform.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ViewService {
    private final AssetRepository assets;
    private final VulnerabilityDefinitionRepository vulns;
    private final FindingRepository findings;
    private final ScanJobRepository scans;
    private final ScanAgentRepository agents;
    private final PatchRepository patches;
    private final PatchServerRepository patchServers;
    private final PatchCveRepository patchCves;
    private final RemediationTaskRepository tasks;
    private final ApprovalRequestRepository approvals;
    private final ApprovalStepRepository approvalSteps;
    private final OrchestrationTemplateRepository templates;
    private final OrchestrationTemplateStepRepository templateSteps;
    private final OrchestrationRunRepository runs;
    private final OrchestrationRunStepRepository runSteps;
    private final PatchDeploymentRepository deployments;
    private final AuditEventRepository audits;

    private static String s(Object v){ return v==null?null:String.valueOf(v); }

    public AssetView asset(Asset a){
        long open=findings.findTop200ByOrderByRiskScoreDescLastSeenAtDesc().stream().filter(f->Objects.equals(f.getAssetId(),a.getId()) && f.getStatus()!=Enums.FindingStatus.RESOLVED && f.getStatus()!=Enums.FindingStatus.FALSE_POSITIVE && f.getStatus()!=Enums.FindingStatus.EXEMPTED).count();
        return new AssetView(a.getId(),a.getAssetCode(),a.getName(),a.getIpAddress(),a.getOsName(),a.getOsVersion(),s(a.getEnvironment()),a.getBusinessService(),a.getOwnerId(),a.getOwnerName(),a.getCriticality(),a.getAgentStatus(),a.getPatchBaseline(),a.getLastSeenAt(),open);
    }

    public VulnerabilityView vulnerability(VulnerabilityDefinition v){
        long affected=findings.findTop200ByOrderByRiskScoreDescLastSeenAtDesc().stream().filter(f->f.getCveId().equals(v.getCveId()) && f.getStatus()!=Enums.FindingStatus.RESOLVED && f.getStatus()!=Enums.FindingStatus.FALSE_POSITIVE && f.getStatus()!=Enums.FindingStatus.EXEMPTED).count();
        List<String> p=patchCves.findByCveId(v.getCveId()).stream().map(x->patches.findById(x.getPatchId()).map(Patch::getPatchId).orElse(null)).filter(Objects::nonNull).toList();
        return new VulnerabilityView(v.getCveId(),v.getTitleZh(),v.getTitleEn(),v.getVendor(),v.getProduct(),v.getDescriptionZh(),v.getDescriptionEn(),v.getCvss(),s(v.getSeverity()),v.isKev(),v.isRansomwareKnown(),v.isPatchAvailable(),v.getReferenceUrl(),s(v.getPublishedDate()),s(v.getKevDueDate()),affected,p);
    }

    public FindingView finding(Finding f){
        VulnerabilityDefinition v=vulns.findById(f.getCveId()).orElse(null); Asset a=assets.findById(f.getAssetId()).orElse(null); ScanJob sj=f.getScanJobId()==null?null:scans.findById(f.getScanJobId()).orElse(null);
        List<String> p=patchCves.findByCveId(f.getCveId()).stream().map(x->patches.findById(x.getPatchId()).map(Patch::getPatchId).orElse(null)).filter(Objects::nonNull).toList();
        return new FindingView(f.getId(),f.getCveId(),v==null?f.getCveId():v.getTitleZh(),v==null?f.getCveId():v.getTitleEn(),v==null?null:v.getCvss(),v==null?null:s(v.getSeverity()),v!=null&&v.isKev(),f.getAssetId(),a==null?null:a.getAssetCode(),a==null?null:a.getName(),a==null?null:s(a.getEnvironment()),a==null?null:a.getBusinessService(),f.getOwnerName(),s(f.getStatus()),f.getRiskScore(),f.getOccurrences(),f.getScanJobId(),sj==null?null:sj.getJobNo(),f.getRemediationTaskId(),s(f.getFirstSeenAt()),s(f.getLastSeenAt()),f.getEvidence(),f.getFalsePositiveReason(),f.getExemptionReason(),s(f.getExemptionExpiresAt()),p);
    }

    public ScanJobView scan(ScanJob x){ return new ScanJobView(x.getId(),x.getJobNo(),x.getName(),x.getScanType(),x.getTargetType(),x.getTargetValue(),x.getCredentialType(),x.getTargetCve(),s(x.getStatus()),x.getProgress(),x.getFindingsCount(),x.getRequestedByName(),x.getRemediationTaskId(),s(x.getCreatedAt()),s(x.getStartedAt()),s(x.getCompletedAt())); }
    public AgentView agent(ScanAgent x){ return new AgentView(x.getId(),x.getAgentKey(),x.getHostname(),x.getIpAddress(),x.getOsName(),x.getVersion(),s(x.getStatus()),x.getAssetId(),s(x.getLastHeartbeatAt())); }

    public PatchServerView patchServer(PatchServer p){return new PatchServerView(p.getId(),p.getName(),p.getAddress(),p.getRegion(),p.getOsSupport(),p.getStatus(),s(p.getLastSyncAt()),p.getCapacityGb(),p.getUsedGb());}

    public PatchView patch(Patch p){
        List<String> cves=patchCves.findByPatchId(p.getId()).stream().map(PatchCve::getCveId).toList();
        long affected=findings.findTop200ByOrderByRiskScoreDescLastSeenAtDesc().stream().filter(f->cves.contains(f.getCveId()) && f.getStatus()!=Enums.FindingStatus.RESOLVED && f.getStatus()!=Enums.FindingStatus.FALSE_POSITIVE && f.getStatus()!=Enums.FindingStatus.EXEMPTED).map(Finding::getAssetId).distinct().count();
        return new PatchView(p.getId(),p.getPatchId(),p.getVendor(),p.getProduct(),p.getVersion(),p.getTitleZh(),p.getTitleEn(),p.getDownloadUrl(),p.getChecksum(),p.getSizeMb(),p.isRebootRequired(),p.getStatus(),p.getSource(),s(p.getPublishedDate()),cves,affected);
    }

    public TaskView task(RemediationTask t){
        Finding f=findings.findById(t.getFindingId()).orElse(null); VulnerabilityDefinition v=f==null?null:vulns.findById(f.getCveId()).orElse(null); Asset a=assets.findById(t.getAssetId()).orElse(null); Patch p=t.getPatchId()==null?null:patches.findById(t.getPatchId()).orElse(null);
        return new TaskView(t.getId(),t.getTaskNo(),t.getFindingId(),f==null?null:f.getCveId(),v==null?null:v.getTitleZh(),v==null?null:v.getTitleEn(),t.getAssetId(),a==null?null:a.getAssetCode(),a==null?null:a.getName(),a==null?null:s(a.getEnvironment()),a==null?null:a.getBusinessService(),t.getPatchId(),p==null?null:p.getPatchId(),t.getOwnerName(),t.getPriority(),s(t.getStage()),s(t.getStatus()),s(t.getChangeType()),t.getApprovalId(),t.getLatestRunId(),s(t.getDueAt()),s(t.getCreatedAt()),s(t.getUpdatedAt()));
    }

    public ApprovalView approval(ApprovalRequest a){
        RemediationTask t=tasks.findById(a.getTaskId()).orElse(null); Finding f=t==null?null:findings.findById(t.getFindingId()).orElse(null); Asset asset=t==null?null:assets.findById(t.getAssetId()).orElse(null);
        List<ApprovalStepView> steps=approvalSteps.findByApprovalIdOrderByStepOrderAsc(a.getId()).stream().map(x->new ApprovalStepView(x.getId(),x.getStepOrder(),x.getRoleNameZh(),x.getRoleNameEn(),x.getApproverName(),s(x.getStatus()),x.getComment(),s(x.getActedAt()))).toList();
        return new ApprovalView(a.getId(),a.getApprovalNo(),a.getTaskId(),t==null?null:t.getTaskNo(),f==null?null:f.getCveId(),asset==null?null:asset.getName(),s(a.getChangeType()),s(a.getStatus()),a.getCurrentStep(),a.getRequestedByName(),s(a.getSubmittedAt()),s(a.getCompletedAt()),a.getReason(),a.getRollbackPlan(),steps);
    }

    public TemplateView template(OrchestrationTemplate t){
        var steps=templateSteps.findByTemplateIdOrderByStepOrderAsc(t.getId()).stream().map(x->new TemplateStepView(x.getId(),x.getStepOrder(),x.getCode(),x.getNameZh(),x.getNameEn(),x.getStepType(),x.isRollbackPoint())).toList();
        return new TemplateView(t.getId(),t.getCode(),t.getNameZh(),t.getNameEn(),t.getType(),t.isEnabled(),t.getVersion(),steps);
    }

    public RunView run(OrchestrationRun r){
        OrchestrationTemplate t=templates.findById(r.getTemplateId()).orElse(null); RemediationTask task=r.getTaskId()==null?null:tasks.findById(r.getTaskId()).orElse(null);
        var steps=runSteps.findByRunIdOrderByStepOrderAsc(r.getId()).stream().map(x->new RunStepView(x.getId(),x.getStepOrder(),x.getCode(),x.getNameZh(),x.getNameEn(),s(x.getStatus()),s(x.getStartedAt()),s(x.getCompletedAt()),x.getMessageZh(),x.getMessageEn())).toList();
        return new RunView(r.getId(),r.getRunNo(),r.getTemplateId(),t==null?null:t.getCode(),t==null?null:t.getNameZh(),t==null?null:t.getNameEn(),r.getTaskId(),task==null?null:task.getTaskNo(),r.getDeploymentId(),r.getEnvironment(),r.getRing(),s(r.getStatus()),r.getCurrentStep(),r.getProgress(),s(r.getCreatedAt()),s(r.getStartedAt()),s(r.getCompletedAt()),r.getFailureReason(),steps);
    }

    public DeploymentView deployment(PatchDeployment d){
        RemediationTask t=tasks.findById(d.getTaskId()).orElse(null); Patch p=patches.findById(d.getPatchId()).orElse(null);
        return new DeploymentView(d.getId(),d.getDeploymentNo(),d.getTaskId(),t==null?null:t.getTaskNo(),d.getPatchId(),p==null?null:p.getPatchId(),d.getEnvironment(),d.getRing(),s(d.getStatus()),d.getProgress(),d.getOrchestrationRunId(),d.getTargetCount(),d.getSuccessCount(),d.getFailureCount(),s(d.getCreatedAt()),s(d.getStartedAt()),s(d.getCompletedAt()));
    }

    public AuditView audit(AuditEvent a){ return new AuditView(a.getId(),a.getEntityType(),a.getEntityId(),a.getAction(),a.getMessageZh(),a.getMessageEn(),a.getActor(),s(a.getCreatedAt())); }
}
