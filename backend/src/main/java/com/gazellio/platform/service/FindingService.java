package com.gazellio.platform.service;

import com.gazellio.platform.dto.ApiDtos.*;
import com.gazellio.platform.dto.ComplianceDtos.ExceptionCreateRequest;
import com.gazellio.platform.model.*;
import com.gazellio.platform.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
    private final PatchRepository patches;
    private final PatchCveRepository patchCves;
    private final RemediationTaskRepository tasks;
    private final SecurityIncidentRepository incidents;
    private final ViewService view;
    private final AuditService audit;
    private final CurrentUserService currentUser;
    private final WorkOrderService workOrders;
    private final ComplianceService compliance;

    public VulnerabilityPageView library(String q,String severity,Boolean kev,Boolean patchAvailable,int page,int size){
        Severity sev=null; if(severity!=null&&!severity.isBlank()&&!severity.equalsIgnoreCase("ALL")) try{sev=Severity.valueOf(severity.toUpperCase());}catch(Exception ignored){}
        String query=q==null||q.isBlank()?null:q;
        Specification<VulnerabilityDefinition> spec=(root,cq,cb)->cb.conjunction();
        if(query!=null){
            String pattern="%"+query.toLowerCase(Locale.ROOT)+"%";
            Set<Long> patchIds=patches.findByPatchIdContainingIgnoreCaseOrTitleZhContainingIgnoreCaseOrTitleEnContainingIgnoreCase(query,query,query)
                    .stream().map(Patch::getId).collect(java.util.stream.Collectors.toSet());
            Set<String> patchCveIds=patchIds.isEmpty()?Set.of():patchCves.findByPatchIdIn(patchIds).stream()
                    .map(PatchCve::getCveId).collect(java.util.stream.Collectors.toSet());
            spec=spec.and((root,cq,cb)->patchCveIds.isEmpty()?cb.or(
                    cb.like(cb.lower(root.get("cveId")),pattern),
                    cb.like(cb.lower(root.get("titleZh")),pattern),
                    cb.like(cb.lower(root.get("titleEn")),pattern),
                    cb.like(cb.lower(root.get("vendor")),pattern),
                    cb.like(cb.lower(root.get("product")),pattern)):cb.or(
                    cb.like(cb.lower(root.get("cveId")),pattern),
                    cb.like(cb.lower(root.get("titleZh")),pattern),
                    cb.like(cb.lower(root.get("titleEn")),pattern),
                    cb.like(cb.lower(root.get("vendor")),pattern),
                    cb.like(cb.lower(root.get("product")),pattern),
                    root.get("cveId").in(patchCveIds)));
        }
        if(sev!=null){ Severity selected=sev; spec=spec.and((root,cq,cb)->cb.equal(root.get("severity"),selected)); }
        if(kev!=null) spec=spec.and((root,cq,cb)->cb.equal(root.get("kev"),kev));
        if(patchAvailable!=null) spec=spec.and((root,cq,cb)->cb.equal(root.get("patchAvailable"),patchAvailable));
        int safePage=Math.max(0,page),safeSize=Math.max(1,Math.min(size,100));
        spec=spec.and((root,cq,cb)->{
            // Spring Data's Criteria implementation rejects Sort.Order.nullsLast(). Express the
            // same ordering through COALESCE so unscored CVEs stay behind scored vulnerabilities.
            if(cq.getResultType()!=Long.class&&cq.getResultType()!=long.class){
                cq.orderBy(cb.desc(root.get("kev")),
                        cb.desc(cb.coalesce(root.<Double>get("cvss"),-1.0)),
                        cb.desc(root.get("updatedAt")));
            }
            return cb.conjunction();
        });
        Page<VulnerabilityDefinition> result=vulns.findAll(spec,PageRequest.of(safePage,safeSize));
        return new VulnerabilityPageView(view.vulnerabilityViews(result.getContent()),result.getTotalElements(),
                result.getNumber()+1,result.getSize(),Math.max(1,result.getTotalPages()));
    }
    public VulnerabilityView vulnerability(String cve){ return view.vulnerability(vulns.findById(cve).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND))); }

    public String exportLibrary(String q,String severity,Boolean kev,Boolean patchAvailable){
        StringBuilder csv=new StringBuilder("CVE,Title,Vendor,Product,CVSS,Severity,KEV,Patch Available,Affected Assets,Published Date\n");
        int page=0;
        while(page<100){
            VulnerabilityPageView batch=library(q,severity,kev,patchAvailable,page,100);
            for(VulnerabilityView v:batch.items()) csv.append(csv(v.cveId())).append(',').append(csv(v.titleEn())).append(',')
                    .append(csv(v.vendor())).append(',').append(csv(v.product())).append(',')
                    .append(v.cvss()==null?"":v.cvss()).append(',').append(csv(v.severity())).append(',')
                    .append(v.kev()).append(',').append(v.patchAvailable()).append(',').append(v.affectedAssets()).append(',')
                    .append(csv(v.publishedDate())).append('\n');
            if(page+1>=batch.totalPages())break;
            page++;
        }
        return csv.toString();
    }

    private String csv(Object value){String s=Objects.toString(value,"");return "\""+s.replace("\"","\"\"")+"\"";}

    public List<FindingView> list(String status,String severity,String q){return list(status,severity,q,null);}

    public List<FindingView> list(String status,String severity,String q,Long assetId){
        String needle=q==null?null:q.toLowerCase(Locale.ROOT);
        List<Finding> source=assetId==null?findings.findTop200Active(PageRequest.of(0,200)):findings.findByAssetIdOrderByRiskScoreDescLastSeenAtDesc(assetId);
        return view.findingViews(source).stream().filter(f->{
            if(status!=null&&!status.isBlank()&&!status.equalsIgnoreCase("ALL")&&!status.equalsIgnoreCase(f.status())) return false;
            if(severity!=null&&!severity.isBlank()&&!severity.equalsIgnoreCase("ALL")&&!severity.equalsIgnoreCase(f.severity())) return false;
            if(needle!=null&&!needle.isBlank()){
                String haystack=String.join(" ",Objects.toString(f.cveId(),""),Objects.toString(f.assetName(),""),Objects.toString(f.assetCode(),""),Objects.toString(f.businessService(),""),Objects.toString(f.titleZh(),""),Objects.toString(f.titleEn(),"")).toLowerCase(Locale.ROOT);
                return haystack.contains(needle);
            }
            return true;
        }).toList();
    }
    public FindingView get(Long id){return view.finding(findings.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND)));}

    public PagedView<FindingView> page(String status,String severity,String q,Long assetId,int page,int size){
        int safePage=Math.max(0,page),safeSize=Math.max(1,Math.min(100,size));
        Specification<Finding> spec=(root,query,cb)->{
            var sq=query.subquery(Long.class);var asset=sq.from(Asset.class);sq.select(asset.get("id")).where(cb.equal(asset.get("id"),root.get("assetId")),cb.isTrue(asset.get("active")));return cb.exists(sq);
        };
        if(assetId!=null)spec=spec.and((root,query,cb)->cb.equal(root.get("assetId"),assetId));
        if(status!=null&&!status.isBlank()&&!"ALL".equalsIgnoreCase(status)){FindingStatus selected=FindingStatus.valueOf(status.toUpperCase());spec=spec.and((root,query,cb)->cb.equal(root.get("status"),selected));}
        if(severity!=null&&!severity.isBlank()&&!"ALL".equalsIgnoreCase(severity)){Severity selected=Severity.valueOf(severity.toUpperCase());spec=spec.and((root,query,cb)->{var sq=query.subquery(String.class);var vuln=sq.from(VulnerabilityDefinition.class);sq.select(vuln.get("cveId")).where(cb.equal(vuln.get("cveId"),root.get("cveId")),cb.equal(vuln.get("severity"),selected));return cb.exists(sq);});}
        if(q!=null&&!q.isBlank()){String pattern="%"+q.trim().toLowerCase(Locale.ROOT)+"%";spec=spec.and((root,query,cb)->{
            var assetSq=query.subquery(Long.class);var asset=assetSq.from(Asset.class);assetSq.select(asset.get("id")).where(cb.equal(asset.get("id"),root.get("assetId")),cb.or(cb.like(cb.lower(asset.get("name")),pattern),cb.like(cb.lower(asset.get("assetCode")),pattern),cb.like(cb.lower(asset.get("businessService")),pattern),cb.like(cb.lower(asset.get("ownerName")),pattern)));
            var vulnSq=query.subquery(String.class);var vuln=vulnSq.from(VulnerabilityDefinition.class);vulnSq.select(vuln.get("cveId")).where(cb.equal(vuln.get("cveId"),root.get("cveId")),cb.or(cb.like(cb.lower(vuln.get("titleZh")),pattern),cb.like(cb.lower(vuln.get("titleEn")),pattern)));
            return cb.or(cb.like(cb.lower(root.get("cveId")),pattern),cb.exists(assetSq),cb.exists(vulnSq));
        });}
        var result=findings.findAll(spec,PageRequest.of(safePage,safeSize,Sort.by(Sort.Order.desc("riskScore"),Sort.Order.desc("lastSeenAt"))));
        return new PagedView<>(view.findingViews(result.getContent()),result.getTotalElements(),result.getNumber()+1,result.getSize(),Math.max(1,result.getTotalPages()));
    }

    @Transactional
    public FindingView confirm(Long id, FindingActionRequest req){
        Finding f=findings.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        if(f.getStatus()!=FindingStatus.NEW&&f.getStatus()!=FindingStatus.REOPENED&&f.getStatus()!=FindingStatus.CONFIRMED)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Only a new or reopened finding can be confirmed");
        f.setStatus(FindingStatus.CONFIRMED); findings.save(f);
        SecurityIncident incident=workOrders.ensureForFinding(f);
        audit.log("FINDING",f.getId(),"CONFIRM","确认漏洞并生成安全事件工单 "+incident.getIncidentNo(),"Finding confirmed and security incident created: "+incident.getIncidentNo(),currentUser.name());
        return view.finding(f);
    }

    @Transactional
    public FindingView falsePositive(Long id, FindingActionRequest req){
        Finding f=findings.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        f.setStatus(FindingStatus.FALSE_POSITIVE); f.setFalsePositiveReason(req==null?null:req.reason()); findings.save(f);
        tasks.findByFindingId(id).ifPresent(t->{t.setStatus(TaskStatus.CANCELLED);t.setUpdatedAt(Instant.now());tasks.save(t);});
        incidents.findByFindingId(id).ifPresent(i->{i.setStatus(IncidentStatus.FALSE_POSITIVE);i.setDecisionReason(req==null?null:req.reason());i.setClosedAt(Instant.now());i.setUpdatedAt(Instant.now());incidents.save(i);});
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
        String control=req.compensatingControl();
        String residualRisk=req.residualRisk();
        if(control==null||control.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Compensating control is required");
        if(residualRisk==null||residualRisk.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Residual risk assessment is required");
        String expires=req.expiresAt();
        if(expires==null||expires.isBlank())expires=LocalDate.now(ZoneOffset.UTC).plusDays(30).toString();
        compliance.requestException(new ExceptionCreateRequest(id,reason,control,residualRisk,expires));
        return view.finding(findings.findById(id).orElseThrow());
    }

    @Transactional
    public BulkFindingActionResult bulk(BulkFindingActionRequest req){
        List<Long> ids=req.findingIds()==null?List.of():req.findingIds().stream().filter(Objects::nonNull).distinct().limit(200).toList();
        if(ids.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Select at least one finding");
        String action=req.action().trim().toUpperCase(Locale.ROOT);
        switch(action){
            case "CONFIRM" -> currentUser.requireAnyAuthority("VULNERABILITY_CONFIRM","VULNERABILITY_MANAGE");
            case "FALSE_POSITIVE" -> currentUser.requireAnyAuthority("VULNERABILITY_FALSE_POSITIVE","VULNERABILITY_MANAGE");
            case "EXEMPT" -> currentUser.requireAnyAuthority("VULNERABILITY_EXEMPT","VULNERABILITY_MANAGE");
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported bulk action");
        }
        List<String> errors=new ArrayList<>();int succeeded=0;
        for(Long id:ids){
            try{
                FindingActionRequest item=new FindingActionRequest(req.reason(),null,req.expiresAt(),
                        req.compensatingControl(),req.residualRisk());
                switch(action){
                    case "CONFIRM" -> confirm(id,item);
                    case "FALSE_POSITIVE" -> {
                        if(req.reason()==null||req.reason().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Reason is required");
                        falsePositive(id,item);
                    }
                    case "EXEMPT" -> exempt(id,item);
                    default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported bulk action");
                }
                succeeded++;
            }catch(Exception ex){errors.add(id+": "+Objects.toString(ex.getMessage(),"Failed"));}
        }
        audit.log("FINDING","BULK",action,"批量处置漏洞：成功 "+succeeded+"，失败 "+errors.size(),
                "Bulk finding action: "+succeeded+" succeeded, "+errors.size()+" failed",currentUser.name());
        return new BulkFindingActionResult(ids.size(),succeeded,errors.size(),errors.stream().limit(20).toList());
    }
}
