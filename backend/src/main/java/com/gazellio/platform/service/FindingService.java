package com.gazellio.platform.service;

import com.gazellio.platform.dto.ApiDtos.*;
import com.gazellio.platform.model.*;
import com.gazellio.platform.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.*;

import static com.gazellio.platform.model.Enums.*;

@Service @RequiredArgsConstructor
public class FindingService {
    private final VulnerabilityDefinitionRepository vulns;
    private final FindingRepository findings;
    private final AssetRepository assets;
    private final PatchRepository patches;
    private final PatchCveRepository patchCves;
    private final RemediationTaskRepository tasks;
    private final ViewService view;
    private final AuditService audit;
    private final CurrentUserService currentUser;

    public List<VulnerabilityView> library(String q,String severity,Boolean kev){
        Severity sev=null; if(severity!=null&&!severity.isBlank()&&!severity.equalsIgnoreCase("ALL")) try{sev=Severity.valueOf(severity.toUpperCase());}catch(Exception ignored){}
        String query=q==null||q.isBlank()?null:q;
        Specification<VulnerabilityDefinition> spec=(root,cq,cb)->cb.conjunction();
        if(query!=null){
            String pattern="%"+query.toLowerCase(Locale.ROOT)+"%";
            spec=spec.and((root,cq,cb)->cb.or(
                    cb.like(cb.lower(root.get("cveId")),pattern),
                    cb.like(cb.lower(root.get("titleZh")),pattern),
                    cb.like(cb.lower(root.get("titleEn")),pattern),
                    cb.like(cb.lower(root.get("vendor")),pattern),
                    cb.like(cb.lower(root.get("product")),pattern)));
        }
        if(sev!=null){ Severity selected=sev; spec=spec.and((root,cq,cb)->cb.equal(root.get("severity"),selected)); }
        if(kev!=null) spec=spec.and((root,cq,cb)->cb.equal(root.get("kev"),kev));
        Sort sort=Sort.by(Sort.Order.desc("kev"),Sort.Order.desc("cvss"),Sort.Order.desc("updatedAt"));
        return vulns.findAll(spec,sort).stream().map(view::vulnerability).toList();
    }
    public VulnerabilityView vulnerability(String cve){ return view.vulnerability(vulns.findById(cve).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND))); }

    public List<FindingView> list(String status,String severity,String q){
        return findings.findTop200ByOrderByRiskScoreDescLastSeenAtDesc().stream().filter(f->{
            if(status!=null&&!status.isBlank()&&!status.equalsIgnoreCase("ALL")&&!f.getStatus().name().equalsIgnoreCase(status)) return false;
            VulnerabilityDefinition v=vulns.findById(f.getCveId()).orElse(null); Asset a=assets.findById(f.getAssetId()).orElse(null);
            if(severity!=null&&!severity.isBlank()&&!severity.equalsIgnoreCase("ALL")&&(v==null||!v.getSeverity().name().equalsIgnoreCase(severity)))return false;
            if(q!=null&&!q.isBlank()){String z=(f.getCveId()+" "+(a==null?"":a.getName()+" "+a.getAssetCode()+" "+a.getBusinessService())+" "+(v==null?"":v.getTitleZh()+" "+v.getTitleEn())).toLowerCase(); if(!z.contains(q.toLowerCase()))return false;}
            return true;
        }).map(view::finding).toList();
    }
    public FindingView get(Long id){return view.finding(findings.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND)));}

    @Transactional
    public FindingView confirm(Long id, FindingActionRequest req){
        Finding f=findings.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        if(f.getStatus()==FindingStatus.RESOLVED||f.getStatus()==FindingStatus.FALSE_POSITIVE||f.getStatus()==FindingStatus.EXEMPTED) throw new ResponseStatusException(HttpStatus.CONFLICT,"Finding is closed");
        f.setStatus(FindingStatus.CONFIRMED); findings.save(f);
        RemediationTask task=tasks.findByFindingId(f.getId()).orElse(null);
        if(task==null){
            Long patchId=req!=null?req.patchId():null;
            if(patchId==null) patchId=patchCves.findByCveId(f.getCveId()).stream().findFirst().map(PatchCve::getPatchId).orElse(null);
            Asset a=assets.findById(f.getAssetId()).orElseThrow(); VulnerabilityDefinition v=vulns.findById(f.getCveId()).orElseThrow();
            String priority=(v.isKev()||v.getSeverity()==Severity.CRITICAL)?"P1":v.getSeverity()==Severity.HIGH?"P2":"P3";
            long days=priority.equals("P1")?3:priority.equals("P2")?7:30;
            task=tasks.save(RemediationTask.builder().taskNo("RMD-"+System.currentTimeMillis()).findingId(f.getId()).patchId(patchId).assetId(f.getAssetId()).ownerId(a.getOwnerId()).ownerName(a.getOwnerName()).priority(priority).stage(TaskStage.ASSIGNED).status(TaskStatus.OPEN).dueAt(Instant.now().plus(Duration.ofDays(days))).build());
            f.setRemediationTaskId(task.getId()); f.setStatus(FindingStatus.IN_REMEDIATION); findings.save(f);
            audit.log("TASK",task.getId(),"CREATE","漏洞确认后自动创建处置任务 "+task.getTaskNo(),"Remediation task automatically created after finding confirmation: "+task.getTaskNo(),currentUser.name());
        }
        audit.log("FINDING",f.getId(),"CONFIRM","确认漏洞并进入处置","Finding confirmed and entered remediation",currentUser.name());
        return view.finding(f);
    }

    @Transactional
    public FindingView falsePositive(Long id, FindingActionRequest req){
        Finding f=findings.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        f.setStatus(FindingStatus.FALSE_POSITIVE); f.setFalsePositiveReason(req==null?null:req.reason()); findings.save(f);
        tasks.findByFindingId(id).ifPresent(t->{t.setStatus(TaskStatus.CANCELLED);t.setUpdatedAt(Instant.now());tasks.save(t);});
        audit.log("FINDING",f.getId(),"FALSE_POSITIVE","漏洞标记为误报","Finding marked as false positive",currentUser.name());
        return view.finding(f);
    }

    @Transactional
    public FindingView exempt(Long id, FindingActionRequest req){
        Finding f=findings.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        if(f.getStatus()==FindingStatus.RESOLVED||f.getStatus()==FindingStatus.FALSE_POSITIVE) throw new ResponseStatusException(HttpStatus.CONFLICT,"Finding is closed");
        if(f.getRemediationTaskId()!=null) throw new ResponseStatusException(HttpStatus.CONFLICT,"Finding already has a remediation task");
        String reason=req==null?null:req.reason();
        if(reason==null||reason.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Exemption reason is required");
        Instant expires=Instant.now().plus(Duration.ofDays(30));
        if(req.expiresAt()!=null&&!req.expiresAt().isBlank()){
            try{expires=LocalDate.parse(req.expiresAt()).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();}
            catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid exemption expiry date");}
        }
        f.setStatus(FindingStatus.EXEMPTED);f.setExemptionReason(reason);f.setExemptedAt(Instant.now());f.setExemptionExpiresAt(expires);findings.save(f);
        audit.log("FINDING",f.getId(),"EXEMPT","漏洞已豁免至 "+expires,"Finding exempted until "+expires,currentUser.name());
        return view.finding(f);
    }
}
