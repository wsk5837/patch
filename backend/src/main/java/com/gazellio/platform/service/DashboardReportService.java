package com.gazellio.platform.service;

import com.gazellio.platform.dto.ApiDtos.*;
import com.gazellio.platform.model.*;
import com.gazellio.platform.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import static com.gazellio.platform.model.Enums.*;

@Service @RequiredArgsConstructor
public class DashboardReportService {
 private final FindingRepository findings; private final VulnerabilityDefinitionRepository vulns; private final AssetRepository assets; private final ApprovalRequestRepository approvals; private final ScanJobRepository scans; private final OrchestrationRunRepository runs; private final RemediationTaskRepository tasks; private final ViewService view;

 public DashboardView dashboard(){
   List<Finding> open=findings.findTop200ByOrderByRiskScoreDescLastSeenAtDesc().stream().filter(this::open).toList();
   long critical=open.stream().filter(f->vulns.findById(f.getCveId()).map(v->v.getSeverity()==Severity.CRITICAL).orElse(false)).count();
   long high=open.stream().filter(f->vulns.findById(f.getCveId()).map(v->v.getSeverity()==Severity.HIGH).orElse(false)).count();
   long runningScans=scans.findTop100ByOrderByCreatedAtDesc().stream().filter(s->s.getStatus()==ScanStatus.RUNNING||s.getStatus()==ScanStatus.QUEUED).count();
   long runningRuns=runs.findTop200ByOrderByCreatedAtDesc().stream().filter(r->r.getStatus()==RunStatus.RUNNING||r.getStatus()==RunStatus.QUEUED||r.getStatus()==RunStatus.PAUSED).count();
   return new DashboardView(critical,high,open.size(),approvals.countByStatus(ApprovalStatus.PENDING),runningScans,runningRuns,patchCompliance(),open.stream().limit(6).map(view::finding).toList(),scans.findTop100ByOrderByCreatedAtDesc().stream().limit(5).map(view::scan).toList(),runs.findTop200ByOrderByCreatedAtDesc().stream().limit(5).map(view::run).toList());
 }

 public ReportView report(){
   List<Finding> all=findings.findTop200ByOrderByRiskScoreDescLastSeenAtDesc(); List<Finding> open=all.stream().filter(this::open).toList();
   Map<String,Long> sev=Arrays.stream(Severity.values()).collect(Collectors.toMap(Enum::name,s->open.stream().filter(f->vulns.findById(f.getCveId()).map(v->v.getSeverity()==s).orElse(false)).count(),(a,b)->a,LinkedHashMap::new));
   Map<String,Long> env=Arrays.stream(EnvironmentType.values()).collect(Collectors.toMap(Enum::name,e->open.stream().filter(f->assets.findById(f.getAssetId()).map(a->a.getEnvironment()==e).orElse(false)).count(),(a,b)->a,LinkedHashMap::new));
   long success=runs.findTop200ByOrderByCreatedAtDesc().stream().filter(r->r.getStatus()==RunStatus.SUCCEEDED).count(), total=runs.count();
   long openTasks=tasks.findTop200ByOrderByUpdatedAtDesc().stream().filter(t->t.getStatus()!=TaskStatus.COMPLETED&&t.getStatus()!=TaskStatus.CANCELLED).count();
   return new ReportView(vulns.count(),open.size(),all.stream().filter(f->f.getStatus()==FindingStatus.RESOLVED).count(),all.stream().filter(f->f.getStatus()==FindingStatus.FALSE_POSITIVE).count(),openTasks,approvals.countByStatus(ApprovalStatus.PENDING),total,success,total==0?100.0:Math.round(success*1000.0/total)/10.0,patchCompliance(),sev,env);
 }
 private boolean open(Finding f){return f.getStatus()!=FindingStatus.RESOLVED&&f.getStatus()!=FindingStatus.FALSE_POSITIVE&&f.getStatus()!=FindingStatus.EXEMPTED;}
 private double patchCompliance(){List<Asset> list=assets.findByActiveTrueOrderByNameAsc();if(list.isEmpty())return 100.0;long compliant=list.stream().filter(a->findings.findTop200ByOrderByRiskScoreDescLastSeenAtDesc().stream().filter(f->Objects.equals(f.getAssetId(),a.getId())&&open(f)).noneMatch(f->vulns.findById(f.getCveId()).map(v->v.isPatchAvailable()&&(v.getSeverity()==Severity.CRITICAL||v.getSeverity()==Severity.HIGH)).orElse(false))).count();return Math.round(compliant*1000.0/list.size())/10.0;}
}
