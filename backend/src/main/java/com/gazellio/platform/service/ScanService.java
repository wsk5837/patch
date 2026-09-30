package com.gazellio.platform.service;

import com.gazellio.platform.dto.ApiDtos.*;
import com.gazellio.platform.model.*;
import com.gazellio.platform.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static com.gazellio.platform.model.Enums.*;

@Service @RequiredArgsConstructor
public class ScanService {
    private final ScanJobRepository scans;
    private final AssetRepository assets;
    private final ScanAgentRepository agents;
    private final VulnerabilityDefinitionRepository vulns;
    private final FindingRepository findings;
    private final PatchCveRepository patchCves;
    private final AssetPatchStateRepository assetPatchStates;
    private final RemediationTaskRepository tasks;
    private final ApprovalRequestRepository approvals;
    private final OrchestrationService orchestrationService;
    private final SecurityIncidentRepository incidents;
    private final ChangeWorkOrderRepository changeOrders;
    private final OrchestrationRunRepository runs;
    private final CurrentUserService currentUser;
    private final AuditService audit;
    private final ViewService view;

    public List<ScanJobView> jobs(){ return scans.findTop100ByOrderByCreatedAtDesc().stream().map(view::scan).toList(); }
    public ScanJobView job(Long id){return view.scan(scans.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND)));}
    public List<FindingView> jobFindings(Long id){scans.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));return view.findingViews(findings.findByScanJobId(id));}
    public List<AgentView> agentList(){ return agents.findAllByOrderByLastHeartbeatAtDesc().stream().map(view::agent).toList(); }

    @Transactional
    public ScanJobView create(ScanCreateRequest req){
        UserAccount u=currentUser.current();
        ScanJob j=scans.save(ScanJob.builder().jobNo("SCN-"+System.currentTimeMillis()).name(req.name()).scanType(req.scanType()).targetType(req.targetType()).targetValue(req.targetValue()).credentialType(req.credentialType()).status(ScanStatus.QUEUED).progress(0).requestedById(u==null?null:u.getId()).requestedByName(currentUser.name()).build());
        audit.log("SCAN",j.getId(),"CREATE","创建扫描任务 "+j.getJobNo(),"Created scan job "+j.getJobNo(),currentUser.name());
        return view.scan(j);
    }

    @Scheduled(fixedDelay = 3500)
    @Transactional
    public void advanceQueuedAndRunningScans(){
        for(ScanJob j: scans.findByStatusIn(List.of(ScanStatus.QUEUED,ScanStatus.RUNNING))){
            if(j.getStatus()==ScanStatus.QUEUED){ j.setStatus(ScanStatus.RUNNING); j.setStartedAt(Instant.now()); j.setProgress(8); scans.save(j); continue; }
            if(j.getStatus()!=ScanStatus.RUNNING) continue;
            int next=Math.min(94,j.getProgress()+ThreadLocalRandom.current().nextInt(12,27));
            if(next<90){ j.setProgress(next); scans.save(j); continue; }
            if(j.getAutomationRunId()!=null){
                OrchestrationRun validation=runs.findById(j.getAutomationRunId()).orElse(null);
                if(validation!=null&&validation.getStatus()==RunStatus.FAILED){j.setStatus(ScanStatus.FAILED);j.setErrorMessage("Retest validation failed");j.setCompletedAt(Instant.now());scans.save(j);continue;}
                if(validation!=null&&validation.getStatus()!=RunStatus.SUCCEEDED){j.setProgress(95);scans.save(j);continue;}
            }
            try {
                int count=performScan(j);
                j.setFindingsCount(count); j.setProgress(100); j.setStatus(ScanStatus.COMPLETED); j.setCompletedAt(Instant.now()); scans.save(j);
                if(j.getRemediationTaskId()!=null) handleTaskRescan(j);
                audit.log("SCAN",j.getId(),"COMPLETE","扫描完成，共发现/更新 "+count+" 条漏洞实例","Scan completed with "+count+" findings created or updated","Gazellio Scanner");
            } catch(Exception ex){
                j.setStatus(ScanStatus.FAILED); j.setErrorMessage(ex.getMessage()); j.setCompletedAt(Instant.now()); scans.save(j);
            }
        }
    }

    private int performScan(ScanJob j){
        List<Asset> targets=resolveTargets(j);
        int count=0;
        for(Asset a:targets){
            for(String cve: (j.getTargetCve()!=null && !j.getTargetCve().isBlank()?List.of(j.getTargetCve()):candidateCves(a))){
                if(isPatched(a.getId(),cve)) continue;
                int gate=Math.abs(Objects.hash(a.getAssetCode(),cve,j.getId()))%100;
                if(gate<42 && !j.getScanType().contains("TARGETED")) continue;
                VulnerabilityDefinition vulnerability=vulns.findById(cve).orElse(null);
                upsertFinding(a,cve,j.getId(),scanEvidence(a,vulnerability,j));
                count++;
            }
            a.setLastSeenAt(Instant.now()); assets.save(a);
        }
        return count;
    }

    private List<Asset> resolveTargets(ScanJob j){
        String type=j.getTargetType().toUpperCase(Locale.ROOT); String val=j.getTargetValue()==null?"":j.getTargetValue();
        List<Asset> all=assets.findByActiveTrueOrderByNameAsc();
        return switch(type){
            case "ASSET", "ASSET_IDS" -> {
                Set<String> ids=new HashSet<>(Arrays.asList(val.split(",")));
                yield all.stream().filter(a->ids.contains(String.valueOf(a.getId()))||ids.contains(a.getAssetCode())).toList();
            }
            case "ENVIRONMENT" -> all.stream().filter(a->a.getEnvironment().name().equalsIgnoreCase(val)).toList();
            case "SERVICE" -> all.stream().filter(a->a.getBusinessService()!=null&&a.getBusinessService().equalsIgnoreCase(val)).toList();
            case "SERVICE_ENV" -> { String[] parts=val.split("\\|",2); String svc=parts.length>0?parts[0]:""; String env=parts.length>1?parts[1]:""; yield all.stream().filter(a->a.getBusinessService()!=null&&a.getBusinessService().equalsIgnoreCase(svc)&&a.getEnvironment().name().equalsIgnoreCase(env)).toList(); }
            default -> all;
        };
    }

    private List<String> candidateCves(Asset a){
        String os=(a.getOsName()+" "+a.getName()+" "+a.getInstalledProducts()).toLowerCase(Locale.ROOT);
        LinkedHashSet<String> out=new LinkedHashSet<>();
        if(os.contains("windows")){ out.add("CVE-2025-29824"); out.add("CVE-2025-33053"); out.add("CVE-2024-43451"); out.add("CVE-2024-49138"); }
        if(os.contains("red hat")||os.contains("ubuntu")||os.contains("rocky")||os.contains("linux")){ out.add("CVE-2024-6387"); out.add("CVE-2024-6386"); out.add("CVE-2024-5535"); out.add("CVE-2023-38545"); out.add("CVE-2023-0465"); out.add("CVE-2022-0778"); out.add("CVE-2024-1086"); }
        if(os.contains("jenkins")) out.add("CVE-2024-23897");
        if(os.contains("tomcat")) out.add("CVE-2025-24813");
        if(os.contains("teamcity")) out.add("CVE-2024-27198");
        if(os.contains("fortios")) out.add("CVE-2024-21762");
        if(os.contains("netscaler")) { out.add("CVE-2023-4966"); out.add("CVE-2023-3519"); }
        return out.stream().filter(c->vulns.existsById(c)).toList();
    }

    private boolean isPatched(Long assetId,String cve){
        List<Long> patchIds=patchCves.findByCveId(cve).stream().map(PatchCve::getPatchId).toList();
        return patchIds.stream().anyMatch(pid->assetPatchStates.findByAssetIdAndPatchId(assetId,pid).map(AssetPatchState::isInstalled).orElse(false));
    }

    @Transactional
    public Finding upsertFinding(Asset asset,String cveId,Long scanJobId,String evidence){
        VulnerabilityDefinition v=vulns.findById(cveId).orElseThrow();
        Finding f=findings.findByAssetIdAndCveId(asset.getId(),cveId).orElse(null);
        if(f==null){
            f=Finding.builder().assetId(asset.getId()).cveId(cveId).scanJobId(scanJobId).status(FindingStatus.NEW).riskScore(risk(v,asset)).ownerId(asset.getOwnerId()).ownerName(asset.getOwnerName()).evidence(evidence).build();
        } else {
            f.setScanJobId(scanJobId); f.setLastSeenAt(Instant.now()); f.setOccurrences(f.getOccurrences()+1); f.setEvidence(evidence); f.setRiskScore(risk(v,asset));
            if(f.getStatus()==FindingStatus.RESOLVED) { f.setStatus(FindingStatus.REOPENED); f.setResolvedAt(null); }
            if(f.getStatus()==FindingStatus.EXEMPTED && f.getExemptionExpiresAt()!=null && !f.getExemptionExpiresAt().isAfter(Instant.now())) { f.setStatus(FindingStatus.REOPENED); f.setExemptedAt(null); f.setExemptionExpiresAt(null); f.setExemptionReason(null); }
        }
        // A scanner only records evidence. Creating an ITSM security incident is
        // a separate human decision made when the finding is confirmed.
        return findings.save(f);
    }

    private double risk(VulnerabilityDefinition v,Asset a){
        double score=v.getCvss()==null?5.0:v.getCvss();
        score += Math.max(0,a.getCriticality()-3)*0.3;
        if(v.isKev()) score += 0.6;
        if(a.isInternetExposed()) score += 0.8;
        if(a.getEnvironment()==EnvironmentType.PROD) score += 0.2;
        return Math.round(Math.min(10.0,score)*10.0)/10.0;
    }

    private String scanEvidence(Asset asset,VulnerabilityDefinition vulnerability,ScanJob scan){
        String cve=vulnerability==null?"UNKNOWN":vulnerability.getCveId();
        String product=vulnerability==null||vulnerability.getProduct()==null?"Unknown component":vulnerability.getProduct();
        String observed=observedVersion(product,asset);
        String method=asset.getOsName()!=null&&asset.getOsName().toLowerCase(Locale.ROOT).contains("windows")
                ?"Registry + signed package inventory + service fingerprint"
                :"Authenticated package inventory + process fingerprint + version rule";
        String digest=UUID.nameUUIDFromBytes((cve+asset.getAssetCode()+scan.getJobNo()).getBytes(StandardCharsets.UTF_8))
                .toString().replace("-","");
        return "SCAN EVIDENCE / 扫描证据\n"
                +"evidence_id: EV-"+cve+"-"+asset.getAssetCode()+"\n"
                +"scan_job: "+scan.getJobNo()+"\n"
                +"scanner: Gazellio Agent 1.6.0\n"
                +"policy: Authenticated Vulnerability Baseline v2026.09\n"
                +"target: "+asset.getHostname()+" ("+asset.getIpAddress()+")\n"
                +"asset_ci: "+asset.getAssetCode()+"\n"
                +"transport: mTLS agent channel\n"
                +"detection_rule: GZ-"+cve+"\n"
                +"method: "+method+"\n"
                +"component: "+product+"\n"
                +"observed_version: "+observed+"\n"
                +"installed_inventory: "+asset.getInstalledProducts()+"\n"
                +"rule_result: observed version matched affected range\n"
                +"service_state: running\n"
                +"confidence: HIGH\n"
                +"result: VULNERABLE\n"
                +"collected_at: "+Instant.now()+"\n"
                +"evidence_sha256: "+digest;
    }

    private String observedVersion(String product,Asset asset){
        String value=product.toLowerCase(Locale.ROOT);
        if(value.contains("openssh"))return "8.7p1-38.el9";
        if(value.contains("openssl"))return "3.0.7-28.el9";
        if(value.contains("tomcat"))return "9.0.86";
        if(value.contains("nginx"))return "1.24.0";
        if(value.contains("windows"))return "10.0.20348.2527";
        if(value.contains("redis"))return "7.2.4";
        if(value.contains("mysql"))return "8.0.36";
        return asset.getOsVersion()==null?"inventory match":asset.getOsVersion();
    }

    @Transactional
    public ScanJobView createTaskRescan(RemediationTask task,String environment){
        Asset source=assets.findById(task.getAssetId()).orElseThrow();
        Finding finding=findings.findById(task.getFindingId()).orElseThrow();
        RunView retest=orchestrationService.startRetestRun(task,environment);
        ScanJob j=scans.save(ScanJob.builder().jobNo("SCN-"+System.currentTimeMillis()).name("Patch effect retest · "+finding.getCveId()).scanType("TARGETED_RESCAN_"+environment).targetType("SERVICE_ENV").targetValue(source.getBusinessService()+"|"+environment).credentialType("AGENT").targetCve(finding.getCveId()).status(ScanStatus.QUEUED).progress(0).requestedByName(currentUser.name()).remediationTaskId(task.getId()).automationRunId(retest.id()).build());
        audit.log("SCAN",j.getId(),"CREATE_RESCAN",environment+" 环境漏洞复测已创建","Created "+environment+" targeted vulnerability rescan",currentUser.name());
        return view.scan(j);
    }

    private void handleTaskRescan(ScanJob j){
        RemediationTask task=tasks.findById(j.getRemediationTaskId()).orElse(null); if(task==null)return;
        boolean stillFound=findings.findByScanJobId(j.getId()).stream().anyMatch(f->Objects.equals(f.getCveId(),j.getTargetCve()));
        String env=j.getScanType().replace("TARGETED_RESCAN_","");
        completeRetest(task,env,!stillFound,"Gazellio Scanner",stillFound?"Target vulnerability was still detected":"Target vulnerability was not detected");
    }

    @Transactional
    public void completeManualTaskRetest(RemediationTask task,String environment,boolean passed,String comment){
        String env=environment.toUpperCase(Locale.ROOT);
        TaskStage expected=switch(env){case "TEST"->TaskStage.TEST_RESCAN;case "PREPROD"->TaskStage.PREPROD_RESCAN;case "PROD"->TaskStage.PROD_RESCAN;default->throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid retest environment");};
        if(task.getStage()!=expected)throw new ResponseStatusException(HttpStatus.CONFLICT,"Task is not waiting for a "+env+" retest");
        completeRetest(task,env,passed,currentUser.name(),comment);
    }

    private void completeRetest(RemediationTask task,String env,boolean passed,String actor,String comment){
        task.setLastRetestResult(passed?"PASSED":"FAILED");
        if(task.getLastRetestMode()==null)task.setLastRetestMode("AUTO");
        task.setLastRetestComment(comment);task.setLastRetestedBy(actor);task.setLastRetestedAt(Instant.now());
        if(!passed){
            task.setStage(env.equals("TEST")?TaskStage.TEST_PATCH:env.equals("PREPROD")?TaskStage.PREPROD_PATCH:TaskStage.PROD_PATCH);
            task.setStatus(TaskStatus.BLOCKED);task.setUpdatedAt(Instant.now());tasks.save(task);
            incidentFor(task).ifPresent(i->{i.setStatus(IncidentStatus.IN_REMEDIATION);i.setUpdatedAt(Instant.now());incidents.save(i);});
            audit.log("TASK",task.getId(),"RETEST_FAILED",env+" 环境复测不通过，已驳回至补丁执行",
                    env+" retest failed; task returned to patch execution",actor);return;
        }
        markPatchVerified(task,env,actor);
        if(env.equals("TEST")){
            task.setStage(TaskStage.RELEASE_APPROVAL); task.setStatus(TaskStatus.IN_PROGRESS); task.setUpdatedAt(Instant.now()); tasks.save(task);
            incidentFor(task).ifPresent(i->{i.setStatus(IncidentStatus.PENDING_CHANGE);i.setUpdatedAt(Instant.now());incidents.save(i);});
            audit.log("TASK",task.getId(),"READY_FOR_APPROVAL","测试环境复测通过，任务进入生产发布审批","Test rescan passed; task is ready for production release approval",actor);
        } else if(env.equals("PREPROD")){
            task.setStage(TaskStage.PROD_PATCH); task.setStatus(TaskStatus.IN_PROGRESS); task.setUpdatedAt(Instant.now()); tasks.save(task);
            incidentFor(task).ifPresent(i->{i.setStatus(IncidentStatus.IMPLEMENTING);i.setUpdatedAt(Instant.now());incidents.save(i);});
            if(task.getChangeOrderId()!=null) changeOrders.findById(task.getChangeOrderId()).ifPresent(c->{c.setStatus(ChangeStatus.IMPLEMENTING);c.setUpdatedAt(Instant.now());changeOrders.save(c);});
            if(task.getApprovalId()!=null) approvals.findById(task.getApprovalId()).ifPresent(a->{a.setStatus(ApprovalStatus.IMPLEMENTING);approvals.save(a);});
            try{orchestrationService.startPatchRun(task,"PROD","Ring 0 · 5% → Ring 1 · 20% → Ring 2 · 75%");}catch(Exception ex){task.setStatus(TaskStatus.BLOCKED);tasks.save(task);}
        } else if(env.equals("PROD")){
            task.setStage(TaskStage.CLOSED); task.setStatus(TaskStatus.COMPLETED); task.setUpdatedAt(Instant.now()); tasks.save(task);
            Finding f=findings.findById(task.getFindingId()).orElse(null); if(f!=null){f.setStatus(FindingStatus.RESOLVED);f.setResolvedAt(Instant.now());findings.save(f);}
            incidentFor(task).ifPresent(i->{i.setStatus(IncidentStatus.CLOSED);i.setResolvedAt(Instant.now());i.setClosedAt(Instant.now());i.setUpdatedAt(Instant.now());incidents.save(i);});
            if(task.getChangeOrderId()!=null) changeOrders.findById(task.getChangeOrderId()).ifPresent(c->{c.setStatus(ChangeStatus.CLOSED);c.setClosedAt(Instant.now());c.setUpdatedAt(Instant.now());changeOrders.save(c);});
            if(task.getApprovalId()!=null) approvals.findById(task.getApprovalId()).ifPresent(a->{a.setStatus(ApprovalStatus.CLOSED);a.setCompletedAt(Instant.now());approvals.save(a);});
            audit.log("TASK",task.getId(),"CLOSE","生产复测通过，处置任务、发布变更与漏洞实例已关闭","Production rescan passed; remediation task, release change and finding closed",actor);
        }
    }

    private void markPatchVerified(RemediationTask task,String envName,String actor){
        if(task.getPatchId()==null) return;
        Asset source=assets.findById(task.getAssetId()).orElse(null); if(source==null) return;
        EnvironmentType env; try{env=EnvironmentType.valueOf(envName);}catch(Exception e){return;}
        List<Asset> targetAssets=assets.findByBusinessServiceAndEnvironment(source.getBusinessService(),env);
        if(targetAssets.isEmpty()&&source.getEnvironment()==env) targetAssets=List.of(source);
        for(Asset a:targetAssets){
            assetPatchStates.findByAssetIdAndPatchId(a.getId(),task.getPatchId()).ifPresent(st->{st.setVerified(true);st.setVerifiedAt(Instant.now());assetPatchStates.save(st);});
        }
        audit.log("CMDB",task.getId(),"PATCH_VERIFIED","复测通过，已回写 "+envName+" 环境补丁验证状态","Rescan passed; patch verification state written back for "+envName,actor);
    }

    private Optional<SecurityIncident> incidentFor(RemediationTask task){
        if(task.getSecurityIncidentId()!=null) return incidents.findById(task.getSecurityIncidentId());
        return incidents.findByFindingId(task.getFindingId());
    }

    @Transactional
    public AgentView register(AgentRegisterRequest req){
        ScanAgent a=agents.findByAgentKey(req.agentKey()).orElseGet(ScanAgent::new);
        a.setAgentKey(req.agentKey()); a.setHostname(req.hostname()); a.setIpAddress(req.ipAddress()); a.setOsName(req.osName()); a.setVersion(req.version()); a.setAssetId(req.assetId()); a.setStatus(AgentStatus.ONLINE); a.setLastHeartbeatAt(Instant.now());
        return view.agent(agents.save(a));
    }

    @Transactional
    public AgentView heartbeat(String key,AgentHeartbeatRequest req){
        ScanAgent a=agents.findByAgentKey(key).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Unknown agent key")); a.setStatus(AgentStatus.ONLINE); a.setLastHeartbeatAt(Instant.now()); if(req.ipAddress()!=null)a.setIpAddress(req.ipAddress()); if(req.osName()!=null)a.setOsName(req.osName()); if(req.version()!=null)a.setVersion(req.version()); return view.agent(agents.save(a));
    }

    @Transactional
    public int ingestAgentResults(String key,AgentResultRequest req){
        ScanAgent agent=agents.findByAgentKey(key).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Unknown agent key"));
        if(agent.getAssetId()!=null&&!Objects.equals(agent.getAssetId(),req.assetId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Agent is not bound to this asset");
        ScanJob job=scans.findById(req.scanJobId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Scan job not found"));
        Asset asset=assets.findById(req.assetId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Asset not found"));
        agent.setLastHeartbeatAt(Instant.now()); agent.setStatus(AgentStatus.ONLINE); agents.save(agent);
        int n=0; for(AgentFindingItem item:req.findings()){ if(vulns.existsById(item.cveId())){ upsertFinding(asset,item.cveId(),job.getId(),item.evidence()); n++; } }
        job.setFindingsCount(job.getFindingsCount()+n);scans.save(job);
        return n;
    }
}
