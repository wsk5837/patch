package com.gazellio.platform.service;

import com.gazellio.platform.dto.ComplianceDtos.*;
import com.gazellio.platform.model.*;
import com.gazellio.platform.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.*;

import static com.gazellio.platform.model.Enums.*;

@Service @RequiredArgsConstructor
public class ComplianceService {
    private final VulnerabilityExceptionRequestRepository exceptions;
    private final SoftwareLifecycleRecordRepository lifecycle;
    private final RegulatoryIncidentCaseRepository regulatoryCases;
    private final ControlAlertRepository alerts;
    private final RiskOverrideRequestRepository riskOverrides;
    private final FindingRepository findings;
    private final AssetRepository assets;
    private final SecurityIncidentRepository incidents;
    private final VulnerabilityDefinitionRepository vulnerabilities;
    private final CurrentUserService currentUser;
    private final AuditService audit;
    private final AuditIntegrityService auditIntegrity;
    private final SettingsService settings;

    public ComplianceSummary summary(){
        AuditIntegrityView integrity=auditIntegrity.status();
        return new ComplianceSummary(exceptions.countByStatus("PENDING"),riskOverrides.countByStatus("PENDING"),
                alerts.countByStatus("OPEN"),lifecycle.countByStatusIn(List.of("EOS","EOL","EXTENDED_SUPPORT")),
                regulatoryCases.countByStatusNot("CLOSED"),integrity.status(),integrity.checkedAt());
    }

    public List<ExceptionView> exceptionRequests(){return exceptions.findTop200ByOrderBySubmittedAtDesc().stream().map(this::exceptionView).toList();}

    @Transactional
    public ExceptionView requestException(ExceptionCreateRequest request){
        Finding finding=findings.findById(request.findingId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Finding not found"));
        if(List.of(FindingStatus.RESOLVED,FindingStatus.FALSE_POSITIVE).contains(finding.getStatus()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Closed finding cannot be exempted");
        exceptions.findFirstByFindingIdAndStatusOrderBySubmittedAtDesc(finding.getId(),"PENDING").ifPresent(existing->{throw new ResponseStatusException(HttpStatus.CONFLICT,"An exception request is already pending");});
        Instant expiry=parseDate(request.expiresAt());
        if(!expiry.isAfter(Instant.now()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Expiry must be in the future");
        UserAccount actor=requireActor();
        VulnerabilityExceptionRequest row=exceptions.save(VulnerabilityExceptionRequest.builder()
                .requestNo("EXC-PENDING-"+UUID.randomUUID()).findingId(finding.getId()).requesterId(actor.getId())
                .requesterName(actor.getDisplayName()).reason(request.reason().trim())
                .compensatingControl(request.compensatingControl().trim()).residualRisk(request.residualRisk().trim())
                .expiresAt(expiry).build());
        row.setRequestNo("EXC-"+String.format("%06d",row.getId()));exceptions.save(row);
        finding.setExemptionRequestId(row.getId());finding.setExemptionStatus("PENDING");findings.save(finding);
        audit.log("EXCEPTION",row.getId(),"SUBMIT","提交漏洞豁免申请 "+row.getRequestNo(),"Vulnerability exception submitted "+row.getRequestNo(),actor.getDisplayName());
        return exceptionView(row);
    }

    @Transactional public ExceptionView decideException(Long id,boolean approve,DecisionRequest request){
        VulnerabilityExceptionRequest row=exceptions.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        if(!"PENDING".equals(row.getStatus()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Exception request is not pending");
        UserAccount actor=requireActor();
        if(Objects.equals(actor.getId(),row.getRequesterId()))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Requester cannot approve the same exception");
        row.setStatus(approve?"APPROVED":"REJECTED");row.setApproverId(actor.getId());row.setApproverName(actor.getDisplayName());
        row.setDecisionComment(request.comment().trim());row.setDecidedAt(Instant.now());exceptions.save(row);
        Finding finding=findings.findById(row.getFindingId()).orElseThrow();
        finding.setExemptionStatus(row.getStatus());
        if(approve){
            finding.setStatus(FindingStatus.EXEMPTED);finding.setExemptionReason(row.getReason());finding.setCompensatingControl(row.getCompensatingControl());
            finding.setResidualRisk(row.getResidualRisk());finding.setExemptedAt(Instant.now());finding.setExemptionExpiresAt(row.getExpiresAt());
            finding.setExemptionApprovedBy(actor.getDisplayName());finding.setExemptionApprovedAt(Instant.now());
            incidents.findByFindingId(finding.getId()).ifPresent(i->{i.setStatus(IncidentStatus.EXEMPTED);i.setDecisionReason(row.getReason());i.setUpdatedAt(Instant.now());incidents.save(i);});
        }
        findings.save(finding);
        audit.log("EXCEPTION",row.getId(),approve?"APPROVE":"REJECT",(approve?"批准":"驳回")+"漏洞豁免 "+row.getRequestNo(),
                (approve?"Approved":"Rejected")+" vulnerability exception "+row.getRequestNo(),actor.getDisplayName());
        return exceptionView(row);
    }

    @Transactional public ExceptionView revokeException(Long id,DecisionRequest request){
        VulnerabilityExceptionRequest row=exceptions.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        if(!"APPROVED".equals(row.getStatus()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Only an active exception can be revoked");
        row.setStatus("REVOKED");row.setRevokedAt(Instant.now());row.setDecisionComment(request.comment().trim());exceptions.save(row);
        reopenExceptionFinding(row,"REVOKED");
        audit.log("EXCEPTION",row.getId(),"REVOKE","撤销漏洞豁免 "+row.getRequestNo(),"Revoked vulnerability exception "+row.getRequestNo(),currentUser.name());
        return exceptionView(row);
    }

    public List<LifecycleView> lifecycleRecords(){return lifecycle.findAllByOrderByEndOfLifeDateAscProductNameAsc().stream().map(this::lifecycleView).toList();}
    @Transactional public LifecycleView saveLifecycle(Long id,LifecycleSaveRequest request){
        SoftwareLifecycleRecord row=id==null?new SoftwareLifecycleRecord():lifecycle.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        row.setProductName(request.productName().trim());row.setVersionPattern(blank(request.versionPattern()));row.setStatus(request.status().trim().toUpperCase(Locale.ROOT));
        if(!Set.of("SUPPORTED","EXTENDED_SUPPORT","EOS","EOL").contains(row.getStatus()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid lifecycle status");
        row.setEndOfSupportDate(parseLocalDate(request.endOfSupportDate()));row.setEndOfLifeDate(parseLocalDate(request.endOfLifeDate()));
        row.setVendorNoticeUrl(blank(request.vendorNoticeUrl()));row.setReplacementPlan(blank(request.replacementPlan()));row.setOwnerName(blank(request.ownerName()));row.setUpdatedAt(Instant.now());
        row=lifecycle.save(row);audit.log("SOFTWARE_LIFECYCLE",row.getId(),id==null?"CREATE":"UPDATE","更新软件生命周期："+row.getProductName(),"Software lifecycle updated: "+row.getProductName(),currentUser.name());return lifecycleView(row);
    }
    @Transactional public void deleteLifecycle(Long id){SoftwareLifecycleRecord row=lifecycle.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));lifecycle.delete(row);audit.log("SOFTWARE_LIFECYCLE",id,"DELETE","删除软件生命周期记录："+row.getProductName(),"Deleted software lifecycle record: "+row.getProductName(),currentUser.name());}

    public List<RegulatoryCaseView> regulatoryCases(){return regulatoryCases.findTop200ByOrderByCreatedAtDesc().stream().map(this::regulatoryView).toList();}
    @Transactional public RegulatoryCaseView createRegulatoryCase(RegulatoryCreateRequest request){
        SecurityIncident incident=incidents.findById(request.securityIncidentId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Security incident not found"));
        if(regulatoryCases.findBySecurityIncidentId(incident.getId()).isPresent())throw new ResponseStatusException(HttpStatus.CONFLICT,"A regulatory assessment already exists");
        Instant discovered=parseInstant(request.discoveredAt(),incident.getCreatedAt());
        RegulatoryIncidentCase row=regulatoryCases.save(RegulatoryIncidentCase.builder().caseNo("REG-PENDING-"+UUID.randomUUID())
                .securityIncidentId(incident.getId()).reportable(request.reportable()).assessment(request.assessment().trim())
                .assessorName(currentUser.name()).discoveredAt(discovered).noticeDueAt(discovered.plus(Duration.ofHours(1)))
                .rcaDueAt(discovered.plus(Duration.ofDays(14))).status(request.reportable()?"REPORTABLE":"MONITORING").build());
        row.setCaseNo("REG-"+String.format("%06d",row.getId()));regulatoryCases.save(row);
        audit.log("REGULATORY_CASE",row.getId(),"ASSESS","建立监管事件评估 "+row.getCaseNo(),"Regulatory incident assessment created "+row.getCaseNo(),currentUser.name());return regulatoryView(row);
    }
    @Transactional public RegulatoryCaseView updateRegulatoryCase(Long id,RegulatoryUpdateRequest request){
        RegulatoryIncidentCase row=regulatoryCases.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        row.setStatus(request.status().trim().toUpperCase(Locale.ROOT));row.setReportable(request.reportable());row.setAssessment(blank(request.assessment()));
        row.setNoticeSubmittedAt(parseInstant(request.noticeSubmittedAt(),null));row.setNoticeReference(blank(request.noticeReference()));
        row.setRcaSubmittedAt(parseInstant(request.rcaSubmittedAt(),null));row.setRootCause(blank(request.rootCause()));row.setImpactAnalysis(blank(request.impactAnalysis()));row.setCorrectiveActions(blank(request.correctiveActions()));row.setUpdatedAt(Instant.now());regulatoryCases.save(row);
        audit.log("REGULATORY_CASE",row.getId(),"UPDATE","更新监管事件 "+row.getCaseNo(),"Updated regulatory incident "+row.getCaseNo(),currentUser.name());return regulatoryView(row);
    }

    public List<AlertView> openAlerts(){refreshAlerts();return alerts.findTop200ByStatusOrderByCreatedAtDesc("OPEN").stream().map(this::alertView).toList();}
    @Transactional public AlertView acknowledgeAlert(Long id){ControlAlert row=alerts.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));row.setStatus("ACKNOWLEDGED");row.setAcknowledgedAt(Instant.now());row.setAcknowledgedBy(currentUser.name());alerts.save(row);audit.log("CONTROL_ALERT",id,"ACKNOWLEDGE","确认合规提醒","Compliance alert acknowledged",currentUser.name());return alertView(row);}

    public List<RiskOverrideView> riskOverrides(){return riskOverrides.findTop200ByOrderBySubmittedAtDesc().stream().map(this::riskOverrideView).toList();}
    @Transactional public RiskOverrideView requestRiskOverride(RiskOverrideCreateRequest request){
        Finding finding=findings.findById(request.findingId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        riskOverrides.findFirstByFindingIdAndStatusOrderBySubmittedAtDesc(finding.getId(),"PENDING").ifPresent(existing->{throw new ResponseStatusException(HttpStatus.CONFLICT,"A risk override is already pending");});
        UserAccount actor=requireActor();RiskOverrideRequest row=riskOverrides.save(RiskOverrideRequest.builder().requestNo("RSK-PENDING-"+UUID.randomUUID()).findingId(finding.getId()).requestedScore(request.requestedScore()).reason(request.reason().trim()).requesterId(actor.getId()).requesterName(actor.getDisplayName()).build());
        row.setRequestNo("RSK-"+String.format("%06d",row.getId()));riskOverrides.save(row);audit.log("RISK_OVERRIDE",row.getId(),"SUBMIT","提交风险评分调整 "+row.getRequestNo(),"Risk score override submitted "+row.getRequestNo(),actor.getDisplayName());return riskOverrideView(row);
    }
    @Transactional public RiskOverrideView decideRiskOverride(Long id,boolean approve,DecisionRequest request){
        RiskOverrideRequest row=riskOverrides.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));if(!"PENDING".equals(row.getStatus()))throw new ResponseStatusException(HttpStatus.CONFLICT);
        UserAccount actor=requireActor();if(Objects.equals(actor.getId(),row.getRequesterId()))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Requester cannot approve the same risk override");
        row.setStatus(approve?"APPROVED":"REJECTED");row.setApproverId(actor.getId());row.setApproverName(actor.getDisplayName());row.setDecisionComment(request.comment().trim());row.setDecidedAt(Instant.now());riskOverrides.save(row);
        if(approve){Finding finding=findings.findById(row.getFindingId()).orElseThrow();finding.setRiskScore(row.getRequestedScore());findings.save(finding);}
        audit.log("RISK_OVERRIDE",id,approve?"APPROVE":"REJECT",(approve?"批准":"驳回")+"风险评分调整 "+row.getRequestNo(),(approve?"Approved":"Rejected")+" risk score override "+row.getRequestNo(),actor.getDisplayName());return riskOverrideView(row);
    }

    public AuditIntegrityView auditIntegrity(){return auditIntegrity.verify();}

    @Scheduled(fixedDelay=60000,initialDelayString="${app.scheduler.initial-delay-ms:60000}")
    @Transactional public void refreshAlerts(){
        Instant now=Instant.now();long warn=settings.intValue("slaWarningHours",72,1,720);long escalate=settings.intValue("slaEscalationHours",24,1,720);
        for(SecurityIncident incident:incidents.findActive(PageRequest.of(0,2000))){
            if(incident.getDueAt()==null||List.of(IncidentStatus.CLOSED,IncidentStatus.RESOLVED,IncidentStatus.EXEMPTED,IncidentStatus.FALSE_POSITIVE).contains(incident.getStatus()))continue;
            long hours=Duration.between(now,incident.getDueAt()).toHours();
            if(hours<=warn)upsertAlert("SLA:"+incident.getId(),"SLA",hours<0?"CRITICAL":hours<=escalate?"HIGH":"MEDIUM","SECURITY_INCIDENT",incident.getId(),
                    hours<0?"安全事件 "+incident.getIncidentNo()+" 已逾期":"安全事件 "+incident.getIncidentNo()+" 将在 "+hours+" 小时内到期",
                    hours<0?"Security incident "+incident.getIncidentNo()+" is overdue":"Security incident "+incident.getIncidentNo()+" is due in "+hours+" hours",incident.getDueAt());
        }
        for(RegulatoryIncidentCase row:regulatoryCases.findTop200ByOrderByCreatedAtDesc()){
            if(!row.isReportable()||"CLOSED".equals(row.getStatus()))continue;
            if(row.getNoticeSubmittedAt()==null)upsertAlert("REG_NOTICE:"+row.getId(),"REGULATORY_NOTICE",now.isAfter(row.getNoticeDueAt())?"CRITICAL":"HIGH","REGULATORY_CASE",row.getId(),"监管事件 "+row.getCaseNo()+" 等待通知记录","Regulatory case "+row.getCaseNo()+" is awaiting notice evidence",row.getNoticeDueAt());
            if(row.getRcaSubmittedAt()==null&&row.getRcaDueAt()!=null&&row.getRcaDueAt().isBefore(now.plus(Duration.ofDays(3))))upsertAlert("REG_RCA:"+row.getId(),"REGULATORY_RCA",now.isAfter(row.getRcaDueAt())?"CRITICAL":"HIGH","REGULATORY_CASE",row.getId(),"监管事件 "+row.getCaseNo()+" 的RCA即将或已经到期","RCA for regulatory case "+row.getCaseNo()+" is due",row.getRcaDueAt());
        }
        for(VulnerabilityExceptionRequest row:exceptions.findByStatusAndExpiresAtBefore("APPROVED",now)){row.setStatus("EXPIRED");exceptions.save(row);reopenExceptionFinding(row,"EXPIRED");}
    }

    private void upsertAlert(String key,String type,String severity,String entityType,Object entityId,String zh,String en,Instant due){
        ControlAlert row=alerts.findByAlertKey(key).orElseGet(()->ControlAlert.builder().alertKey(key).alertType(type).entityType(entityType).entityId(String.valueOf(entityId)).messageZh(zh).messageEn(en).severity(severity).dueAt(due).build());
        if("ACKNOWLEDGED".equals(row.getStatus())&&due!=null&&due.isAfter(Instant.now()))return;
        row.setSeverity(severity);row.setMessageZh(zh);row.setMessageEn(en);row.setDueAt(due);alerts.save(row);
    }
    private void reopenExceptionFinding(VulnerabilityExceptionRequest row,String status){findings.findById(row.getFindingId()).ifPresent(f->{f.setStatus(FindingStatus.REOPENED);f.setExemptionStatus(status);f.setExemptionExpiresAt(null);f.setExemptionReason(null);f.setCompensatingControl(null);f.setResidualRisk(null);f.setExemptionApprovedBy(null);f.setExemptionApprovedAt(null);findings.save(f);});incidents.findByFindingId(row.getFindingId()).ifPresent(i->{i.setStatus(IncidentStatus.ASSIGNED);i.setDecisionReason(status);i.setUpdatedAt(Instant.now());incidents.save(i);});}

    private ExceptionView exceptionView(VulnerabilityExceptionRequest row){Finding f=findings.findById(row.getFindingId()).orElse(null);Asset a=f==null?null:assets.findById(f.getAssetId()).orElse(null);UserAccount actor=currentUser.current();return new ExceptionView(row.getId(),row.getRequestNo(),row.getFindingId(),f==null?null:f.getCveId(),a==null?null:a.getName(),row.getStatus(),row.getRequesterName(),row.getApproverName(),row.getReason(),row.getCompensatingControl(),row.getResidualRisk(),text(row.getExpiresAt()),row.getDecisionComment(),text(row.getSubmittedAt()),text(row.getDecidedAt()),text(row.getRevokedAt()),actor!=null&&!Objects.equals(actor.getId(),row.getRequesterId())&&"PENDING".equals(row.getStatus()));}
    private LifecycleView lifecycleView(SoftwareLifecycleRecord row){String product=row.getProductName().toLowerCase(Locale.ROOT);String version=Optional.ofNullable(row.getVersionPattern()).orElse("").toLowerCase(Locale.ROOT);long affected=assets.findByActiveTrueOrderByNameAsc().stream().filter(a->{String inventory=Optional.ofNullable(a.getInstalledProducts()).orElse("").toLowerCase(Locale.ROOT);return inventory.contains(product)&&(version.isBlank()||inventory.contains(version));}).count();return new LifecycleView(row.getId(),row.getProductName(),row.getVersionPattern(),row.getStatus(),text(row.getEndOfSupportDate()),text(row.getEndOfLifeDate()),row.getVendorNoticeUrl(),row.getReplacementPlan(),row.getOwnerName(),affected,text(row.getUpdatedAt()));}
    private RegulatoryCaseView regulatoryView(RegulatoryIncidentCase row){String incidentNo=incidents.findById(row.getSecurityIncidentId()).map(SecurityIncident::getIncidentNo).orElse(null);return new RegulatoryCaseView(row.getId(),row.getCaseNo(),row.getSecurityIncidentId(),incidentNo,row.getStatus(),row.isReportable(),row.getAssessment(),row.getAssessorName(),text(row.getDiscoveredAt()),text(row.getNoticeDueAt()),text(row.getNoticeSubmittedAt()),row.getNoticeReference(),text(row.getRcaDueAt()),text(row.getRcaSubmittedAt()),row.getRootCause(),row.getImpactAnalysis(),row.getCorrectiveActions(),text(row.getCreatedAt()),text(row.getUpdatedAt()));}
    private AlertView alertView(ControlAlert row){return new AlertView(row.getId(),row.getAlertType(),row.getSeverity(),row.getStatus(),row.getEntityType(),row.getEntityId(),row.getMessageZh(),row.getMessageEn(),text(row.getDueAt()),text(row.getCreatedAt()),text(row.getAcknowledgedAt()),row.getAcknowledgedBy());}
    private RiskOverrideView riskOverrideView(RiskOverrideRequest row){Finding f=findings.findById(row.getFindingId()).orElse(null);Asset a=f==null?null:assets.findById(f.getAssetId()).orElse(null);UserAccount actor=currentUser.current();return new RiskOverrideView(row.getId(),row.getRequestNo(),row.getFindingId(),f==null?null:f.getCveId(),a==null?null:a.getName(),f==null?null:f.getRiskScore(),row.getRequestedScore(),row.getReason(),row.getStatus(),row.getRequesterName(),row.getApproverName(),row.getDecisionComment(),text(row.getSubmittedAt()),text(row.getDecidedAt()),actor!=null&&!Objects.equals(actor.getId(),row.getRequesterId())&&"PENDING".equals(row.getStatus()));}
    private UserAccount requireActor(){UserAccount actor=currentUser.current();if(actor==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);return actor;}
    private Instant parseDate(String value){try{return LocalDate.parse(value).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid date");}}
    private LocalDate parseLocalDate(String value){if(value==null||value.isBlank())return null;try{return LocalDate.parse(value);}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid date");}}
    private Instant parseInstant(String value,Instant fallback){if(value==null||value.isBlank())return fallback;try{return Instant.parse(value);}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid timestamp");}}
    private String blank(String value){return value==null||value.isBlank()?null:value.trim();}
    private String text(Object value){return value==null?null:String.valueOf(value);}
}
