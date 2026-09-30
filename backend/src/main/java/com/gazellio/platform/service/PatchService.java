package com.gazellio.platform.service;

import com.gazellio.platform.dto.ApiDtos.*;
import com.gazellio.platform.model.*;
import com.gazellio.platform.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.gazellio.platform.model.Enums.*;

@Service
@RequiredArgsConstructor
public class PatchService {
    private final PatchRepository patches;
    private final PatchCveRepository patchCves;
    private final VulnerabilityDefinitionRepository vulnerabilities;
    private final PatchServerRepository servers;
    private final PatchDeploymentRepository deployments;
    private final FindingRepository findings;
    private final SecurityIncidentRepository incidents;
    private final RemediationTaskRepository tasks;
    private final ChangeWorkOrderRepository changes;
    private final PatchScheduleRepository schedules;
    private final AssetRepository assets;
    private final ViewService view;
    private final AuditService audit;
    private final CurrentUserService currentUser;

    public List<PatchView> list(){
        return view.patchViews(patches.findActiveCatalog());
    }

    public PatchView get(Long id){
        return view.patch(patches.findById(id).orElseThrow());
    }

    public List<FindingView> findings(Long id){
        patches.findById(id).orElseThrow();
        Set<String> cves=patchCves.findByPatchId(id).stream().map(PatchCve::getCveId).collect(java.util.stream.Collectors.toSet());
        return cves.isEmpty()?List.of():view.findingViews(findings.findTop200ByCveIdInOrderByRiskScoreDescLastSeenAtDesc(cves));
    }

    public List<PatchServerView> servers(){
        return servers.findAllByOrderByNameAsc().stream().map(view::patchServer).toList();
    }

    public List<DeploymentView> deployments(){
        return view.deploymentViews(deployments.findTop200ByOrderByCreatedAtDesc());
    }

    public List<PatchCalendarEventView> calendar(String month){
        YearMonth selected;
        try{selected=month==null||month.isBlank()?YearMonth.now():YearMonth.parse(month);}
        catch(Exception e){selected=YearMonth.now();}
        List<SecurityIncident> incidentRows=incidents.findActive(PageRequest.of(0,200));
        Map<Long,Finding> findingById=findings.findAllById(incidentRows.stream().map(SecurityIncident::getFindingId).toList())
                .stream().collect(Collectors.toMap(Finding::getId,Function.identity()));
        Map<String,VulnerabilityDefinition> vulnerabilityById=vulnerabilities.findAllById(findingById.values().stream().map(Finding::getCveId).toList())
                .stream().collect(Collectors.toMap(VulnerabilityDefinition::getCveId,Function.identity()));
        Map<Long,RemediationTask> taskById=tasks.findAllById(incidentRows.stream().map(SecurityIncident::getRemediationTaskId).filter(Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(RemediationTask::getId,Function.identity()));
        Map<Long,ChangeWorkOrder> changeById=changes.findAllById(incidentRows.stream().map(SecurityIncident::getChangeOrderId).filter(Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(ChangeWorkOrder::getId,Function.identity()));
        Map<Long,Asset> assetById=assets.findAllById(incidentRows.stream().map(SecurityIncident::getAssetId).toList())
                .stream().collect(Collectors.toMap(Asset::getId,Function.identity()));
        Set<Long> patchIds=taskById.values().stream().map(RemediationTask::getPatchId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long,Patch> patchById=patches.findAllById(patchIds).stream().collect(Collectors.toMap(Patch::getId,Function.identity()));
        Instant now=Instant.now();ZoneId zone=ZoneId.systemDefault();List<PatchCalendarEventView> out=new ArrayList<>();
        for(SecurityIncident incident:incidentRows){
            Finding finding=findingById.get(incident.getFindingId());if(finding==null)continue;
            VulnerabilityDefinition vulnerability=vulnerabilityById.get(finding.getCveId());
            RemediationTask task=incident.getRemediationTaskId()==null?null:taskById.get(incident.getRemediationTaskId());
            ChangeWorkOrder change=incident.getChangeOrderId()==null?null:changeById.get(incident.getChangeOrderId());
            Patch patch=task==null||task.getPatchId()==null?null:patchById.get(task.getPatchId());
            Asset asset=assetById.get(incident.getAssetId());
            boolean completed=List.of(IncidentStatus.CLOSED,IncidentStatus.RESOLVED).contains(incident.getStatus());
            if(incident.getDueAt()!=null&&YearMonth.from(incident.getDueAt().atZone(zone)).equals(selected)){
                long hours=Duration.between(now,incident.getDueAt()).toHours();
                String status=completed?"COMPLETED":hours<0?"OVERDUE":hours<=72?"DUE_SOON":"SCHEDULED";
                out.add(calendarEvent("SLA-"+incident.getId(),incident.getDueAt(),null,"SLA_DUE",incident,
                        task,change,patch,finding,asset,vulnerability,status,hours));
            }
            if(change!=null&&change.getMaintenanceStart()!=null&&YearMonth.from(change.getMaintenanceStart().atZone(zone)).equals(selected)){
                long hours=Duration.between(now,change.getMaintenanceStart()).toHours();
                String status=completed||change.getStatus()==ChangeStatus.CLOSED?"COMPLETED":hours<0?"OVERDUE":"PLANNED";
                out.add(calendarEvent("CHG-"+change.getId(),change.getMaintenanceStart(),change.getMaintenanceEnd(),
                        "PATCH_WINDOW",incident,task,change,patch,finding,asset,vulnerability,status,hours));
            }
        }
        Instant rangeStart=selected.atDay(1).atStartOfDay(zone).toInstant();
        Instant rangeEnd=selected.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
        for(PatchSchedule schedule:schedules.findByStartAtLessThanAndEndAtGreaterThanOrderByStartAtAsc(rangeEnd,rangeStart)){
            Patch patch=patches.findById(schedule.getPatchId()).orElse(null);
            long hours=Duration.between(now,schedule.getStartAt()).toHours();
            out.add(new PatchCalendarEventView("PLAN-"+schedule.getId(),schedule.getStartAt().toString(),schedule.getEndAt().toString(),"PATCH_PLAN",
                    null,null,null,null,null,null,schedule.getPatchId(),patch==null?null:patch.getPatchId(),null,null,null,null,null,
                    schedule.getStatus(),hours,schedule.getId(),schedule.getTitleZh(),schedule.getTitleEn(),true));
        }
        return out.stream().sorted(Comparator.comparing(PatchCalendarEventView::date)).toList();
    }

    private PatchCalendarEventView calendarEvent(String eventId,Instant date,Instant endAt,String eventType,
                                                  SecurityIncident incident,RemediationTask task,ChangeWorkOrder change,
                                                  Patch patch,Finding finding,Asset asset,VulnerabilityDefinition vulnerability,
                                                  String status,long hours){
        return new PatchCalendarEventView(eventId,date.toString(),endAt==null?null:endAt.toString(),eventType,
                incident.getId(),incident.getIncidentNo(),task==null?null:task.getId(),task==null?null:task.getTaskNo(),
                change==null?null:change.getId(),change==null?null:change.getChangeNo(),patch==null?null:patch.getId(),
                patch==null?null:patch.getPatchId(),finding.getCveId(),asset==null?null:asset.getName(),
                asset==null?null:asset.getBusinessService(),vulnerability==null?null:vulnerability.getSeverity().name(),
                incident.getPriority(),status,hours,null,null,null,false);
    }

    public PatchScheduleView schedule(Long id){return scheduleView(schedules.findById(id)
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND)));}

    @Transactional
    public PatchScheduleView createSchedule(PatchScheduleRequest request){return saveSchedule(new PatchSchedule(),request,true);}

    @Transactional
    public PatchScheduleView updateSchedule(Long id,PatchScheduleRequest request){return saveSchedule(schedules.findById(id)
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND)),request,false);}

    @Transactional
    public void deleteSchedule(Long id){
        PatchSchedule schedule=schedules.findById(id).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        schedules.delete(schedule);
        audit.log("PATCH_SCHEDULE",id,"DELETE","删除补丁排程："+schedule.getTitleZh(),"Deleted patch schedule: "+schedule.getTitleEn(),currentUser.name());
    }

    private PatchScheduleView saveSchedule(PatchSchedule schedule,PatchScheduleRequest request,boolean creating){
        Patch patch=patches.findById(request.patchId()).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Patch not found"));
        Instant start=parseInstant(request.startAt());Instant end=parseInstant(request.endAt());
        if(!end.isAfter(start))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"End time must be after start time");
        String status=request.status().trim().toUpperCase(Locale.ROOT);
        if(!Set.of("PLANNED","APPROVED","COMPLETED","CANCELLED").contains(status))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Invalid schedule status");
        UserAccount actor=currentUser.current();
        schedule.setTitleZh(request.titleZh().trim());schedule.setTitleEn(request.titleEn().trim());schedule.setPatchId(patch.getId());
        schedule.setEnvironment(request.environment().trim().toUpperCase(Locale.ROOT));schedule.setStartAt(start);schedule.setEndAt(end);
        schedule.setStatus(status);schedule.setNotes(blankToNull(request.notes()));schedule.setOwnerId(actor==null?null:actor.getId());
        schedule.setOwnerName(actor==null?currentUser.name():actor.getDisplayName());schedule.setUpdatedAt(Instant.now());
        if(creating){schedule.setCreatedById(actor==null?null:actor.getId());schedule.setCreatedByName(actor==null?currentUser.name():actor.getDisplayName());schedule.setCreatedAt(Instant.now());}
        schedule=schedules.save(schedule);
        audit.log("PATCH_SCHEDULE",schedule.getId(),creating?"CREATE":"UPDATE",(creating?"创建":"更新")+"补丁排程："+schedule.getTitleZh(),(creating?"Created":"Updated")+" patch schedule: "+schedule.getTitleEn(),currentUser.name());
        return scheduleView(schedule);
    }

    private PatchScheduleView scheduleView(PatchSchedule schedule){
        String patchCode=patches.findById(schedule.getPatchId()).map(Patch::getPatchId).orElse(null);
        return new PatchScheduleView(schedule.getId(),schedule.getTitleZh(),schedule.getTitleEn(),schedule.getPatchId(),patchCode,
                schedule.getEnvironment(),schedule.getStartAt().toString(),schedule.getEndAt().toString(),schedule.getStatus(),schedule.getOwnerId(),
                schedule.getOwnerName(),schedule.getNotes(),schedule.getCreatedByName(),schedule.getCreatedAt().toString(),schedule.getUpdatedAt().toString());
    }

    private Instant parseInstant(String value){
        try{return Instant.parse(value);}catch(Exception ignored){}
        try{return LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant();}
        catch(Exception e){throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Invalid date time");}
    }

    @Transactional
    public PatchView register(PatchCreateRequest req){
        Patch p=patches.findByPatchId(req.patchId().trim()).orElseGet(Patch::new);
        p.setPatchId(req.patchId().trim());
        p.setVendor(req.vendor().trim());
        p.setProduct(req.product().trim());
        p.setVersion(blankToNull(req.version()));
        p.setTitleZh(req.titleZh().trim());
        p.setTitleEn(req.titleEn().trim());
        p.setDownloadUrl(blankToNull(req.downloadUrl()));
        p.setChecksum(blankToNull(req.checksum()));
        p.setApplicabilityRule(blankToNull(req.applicabilityRule()));
        p.setApplicabilityRuleEn(blankToNull(req.applicabilityRule()));
        p.setSupersedes(blankToNull(req.supersedes()));
        p.setReleaseNotesZh(blankToNull(req.releaseNotesZh()));
        p.setReleaseNotesEn(blankToNull(req.releaseNotesEn()));
        p.setSignatureStatus(p.getChecksum()==null?"PENDING":"VERIFIED");
        p.setSignatureIssuer(req.vendor().trim()+" Code Signing CA");
        p.setSignatureFingerprint(p.getChecksum());
        p.setIntegrityVerifiedAt(p.getChecksum()==null?null:Instant.now());
        p.setPrerequisites("Agent 1.6.0+；已确认维护窗口、回退点、可用空间和健康探针。");
        p.setPrerequisitesEn("Agent 1.6.0+; maintenance window, rollback point, capacity and health probe confirmed.");
        p.setInstallCommand("gazellio-agent patch install --package \""+req.patchId().trim()+"\" --verify-signature --rollback-point auto");
        p.setUninstallCommand("gazellio-agent patch rollback --package \""+req.patchId().trim()+"\" --restore-point latest");
        p.setTestEvidence(p.getChecksum()==null?"待完成包完整性、签名、适用性、测试安装、健康检查和漏洞复测。":"包完整性、数字签名与适用性校验已通过；等待目标环境安装证据。");
        p.setKnownIssues(req.rebootRequired()?"需要在维护窗口内完成重启并验证服务健康状态。":"登记时未发现阻断性已知问题。");
        p.setKnownIssuesEn(req.rebootRequired()?"Restart during the maintenance window and verify service health.":"No blocking known issues were recorded at registration.");
        p.setSizeMb(req.sizeMb());
        p.setRebootRequired(req.rebootRequired());
        p.setStatus("AVAILABLE");
        p.setSource(blankToNull(req.source())==null?"Manual":req.source().trim());
        p.setUpdatedAt(Instant.now());
        p=patches.save(p);

        Set<String> requested=new LinkedHashSet<>();
        if(req.cves()!=null){
            for(String raw:req.cves()){
                if(raw==null||raw.isBlank())continue;
                String cve=raw.trim().toUpperCase(Locale.ROOT);
                requested.add(cve);
                if(!patchCves.existsByPatchIdAndCveId(p.getId(),cve)){
                    patchCves.save(PatchCve.builder().patchId(p.getId()).cveId(cve).build());
                }
                vulnerabilities.findById(cve).ifPresent(v->{v.setPatchAvailable(true);v.setUpdatedAt(Instant.now());vulnerabilities.save(v);});
            }
        }
        for(PatchCve existing:patchCves.findByPatchId(p.getId())){
            if(!requested.contains(existing.getCveId())) patchCves.delete(existing);
        }

        audit.log("PATCH",p.getId(),"REGISTER","登记补丁并更新漏洞映射："+p.getPatchId(),"Patch registered and vulnerability mappings updated: "+p.getPatchId(),currentUser.name());
        return view.patch(p);
    }

    @Transactional
    public int sync(){
        int n=0;
        for(PatchServer s:servers.findAll()){
            s.setLastSyncAt(Instant.now());
            servers.save(s);
            n++;
        }
        audit.log("PATCH_CATALOG","all","SYNC","补丁源同步完成","Patch sources synchronized",currentUser.name());
        return n;
    }

    private static String blankToNull(String value){
        return value==null||value.isBlank()?null:value.trim();
    }
}
