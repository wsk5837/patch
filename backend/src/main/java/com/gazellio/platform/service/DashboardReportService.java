package com.gazellio.platform.service;

import com.gazellio.platform.dto.ApiDtos.*;
import com.gazellio.platform.model.Enums.*;
import com.gazellio.platform.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DashboardReportService {
    private static final List<FindingStatus> CLOSED_FINDINGS =
            List.of(FindingStatus.RESOLVED, FindingStatus.FALSE_POSITIVE, FindingStatus.EXEMPTED);
    private static final Duration DASHBOARD_CACHE_TTL = Duration.ofSeconds(5);
    private static final Duration REPORT_CACHE_TTL = Duration.ofSeconds(15);

    private final FindingRepository findings;
    private final VulnerabilityDefinitionRepository vulns;
    private final AssetRepository assets;
    private final ApprovalRequestRepository approvals;
    private final ScanJobRepository scans;
    private final OrchestrationRunRepository runs;
    private final RemediationTaskRepository tasks;
    private final SecurityIncidentRepository incidents;
    private final PatchDeploymentRepository deployments;
    private final PatchCveRepository patchCves;
    private final ViewService view;

    private volatile DashboardView cachedDashboard;
    private volatile Instant dashboardExpiresAt = Instant.EPOCH;
    private volatile ReportView cachedReport;
    private volatile Instant reportExpiresAt = Instant.EPOCH;

    public DashboardView dashboard() {
        Instant now = Instant.now();
        DashboardView cached = cachedDashboard;
        if (cached != null && now.isBefore(dashboardExpiresAt)) return cached;
        synchronized (this) {
            now = Instant.now();
            if (cachedDashboard != null && now.isBefore(dashboardExpiresAt)) return cachedDashboard;
            Instant dashboardNow=now;
            Map<Severity, Long> bySeverity = severityCounts();
            List<com.gazellio.platform.model.Finding> activeFindings=findings.findAllActive();
            List<com.gazellio.platform.model.Asset> activeAssets=assets.findByActiveTrueOrderByNameAsc();
            List<com.gazellio.platform.model.SecurityIncident> activeIncidents=incidents.findActive(PageRequest.of(0,1000));
            long overdue=activeIncidents.stream().filter(i->i.getDueAt()!=null&&i.getDueAt().isBefore(dashboardNow)
                    &&!List.of(IncidentStatus.CLOSED,IncidentStatus.RESOLVED,IncidentStatus.EXEMPTED,IncidentStatus.FALSE_POSITIVE).contains(i.getStatus())).count();
            double mttr=activeFindings.stream().filter(f->f.getResolvedAt()!=null&&f.getFirstSeenAt()!=null)
                    .mapToLong(f->Duration.between(f.getFirstSeenAt(),f.getResolvedAt()).toHours()).average().orElse(0.0);
            long recentlySeen=activeAssets.stream().filter(a->a.getLastSeenAt()!=null&&a.getLastSeenAt().isAfter(dashboardNow.minus(Duration.ofDays(7)))).count();
            double coverage=activeAssets.isEmpty()?100.0:Math.round(recentlySeen*1000.0/activeAssets.size())/10.0;
            DashboardView result = new DashboardView(
                    bySeverity.getOrDefault(Severity.CRITICAL, 0L),
                    bySeverity.getOrDefault(Severity.HIGH, 0L),
                    findings.countActiveOpen(CLOSED_FINDINGS),
                    approvals.countActiveByStatus(ApprovalStatus.PENDING),
                    scans.countByStatusIn(List.of(ScanStatus.QUEUED, ScanStatus.RUNNING)),
                    runs.countByStatusIn(List.of(RunStatus.QUEUED, RunStatus.RUNNING, RunStatus.PAUSED)),
                    patchCompliance(),
                    activeAssets.size(),overdue,Math.round(mttr*10.0)/10.0,coverage,
                    trend(activeFindings,dashboardNow,30),
                    view.findingViews(findings.findTopActiveOpen(CLOSED_FINDINGS,PageRequest.of(0,6))),
                    scans.findTop5ByOrderByCreatedAtDesc().stream().map(view::scan).toList(),
                    view.runViews(runs.findTop5ByOrderByCreatedAtDesc())
            );
            cachedDashboard = result;
            dashboardExpiresAt = now.plus(DASHBOARD_CACHE_TTL);
            return result;
        }
    }

    private List<TrendPointView> trend(List<com.gazellio.platform.model.Finding> rows,Instant now,int days){
        java.time.ZoneId zone=java.time.ZoneId.systemDefault();
        java.time.LocalDate today=now.atZone(zone).toLocalDate();
        List<TrendPointView> result=new ArrayList<>();
        for(int offset=days-1;offset>=0;offset--){
            java.time.LocalDate day=today.minusDays(offset);
            Instant end=day.plusDays(1).atStartOfDay(zone).toInstant();
            long opened=rows.stream().filter(f->f.getFirstSeenAt()!=null&&f.getFirstSeenAt().atZone(zone).toLocalDate().equals(day)).count();
            long resolved=rows.stream().filter(f->f.getResolvedAt()!=null&&f.getResolvedAt().atZone(zone).toLocalDate().equals(day)).count();
            long backlog=rows.stream().filter(f->f.getFirstSeenAt()!=null&&f.getFirstSeenAt().isBefore(end)
                    &&(f.getResolvedAt()==null||!f.getResolvedAt().isBefore(end))
                    &&f.getStatus()!=FindingStatus.FALSE_POSITIVE&&f.getStatus()!=FindingStatus.EXEMPTED).count();
            result.add(new TrendPointView(day.toString(),opened,resolved,backlog));
        }
        return result;
    }

    public ReportView report() {
        Instant now = Instant.now();
        ReportView cached = cachedReport;
        if (cached != null && now.isBefore(reportExpiresAt)) return cached;
        synchronized (this) {
            now = Instant.now();
            if (cachedReport != null && now.isBefore(reportExpiresAt)) return cachedReport;
            long totalRuns = runs.count();
            long succeededRuns = runs.countByStatusIn(List.of(RunStatus.SUCCEEDED));
            Map<String, Long> severity = new LinkedHashMap<>();
            Map<Severity, Long> severityRaw = severityCounts();
            for (Severity value : Severity.values()) severity.put(value.name(), severityRaw.getOrDefault(value, 0L));
            Map<String, Long> environment = new LinkedHashMap<>();
            Map<EnvironmentType, Long> environmentRaw = environmentCounts();
            for (EnvironmentType value : EnvironmentType.values()) environment.put(value.name(), environmentRaw.getOrDefault(value, 0L));
            Map<String,Long> deploymentStatus=new LinkedHashMap<>();
            for(DeploymentStatus value:DeploymentStatus.values())deploymentStatus.put(value.name(),0L);
            deployments.countByStatusGrouped().forEach(row->deploymentStatus.put(row.getStatus().name(),row.getTotal()));
            Instant reportNow=now;
            List<com.gazellio.platform.model.Finding> allActive=findings.findAllActive();
            List<com.gazellio.platform.model.Finding> openRows=allActive.stream().filter(f->!CLOSED_FINDINGS.contains(f.getStatus())).toList();
            Set<String> openCves=openRows.stream().map(com.gazellio.platform.model.Finding::getCveId).collect(java.util.stream.Collectors.toSet());
            Set<String> mappedCves=patchCves.findByCveIdIn(openCves).stream().map(com.gazellio.platform.model.PatchCve::getCveId).collect(java.util.stream.Collectors.toSet());
            long withoutPatch=openRows.stream().filter(f->!mappedCves.contains(f.getCveId())).count();
            Map<Long,com.gazellio.platform.model.Asset> assetById=assets.findByActiveTrueOrderByNameAsc().stream()
                    .collect(java.util.stream.Collectors.toMap(com.gazellio.platform.model.Asset::getId,java.util.function.Function.identity()));
            Map<String,com.gazellio.platform.model.VulnerabilityDefinition> vulnById=vulns.findAllById(openCves).stream()
                    .collect(java.util.stream.Collectors.toMap(com.gazellio.platform.model.VulnerabilityDefinition::getCveId,java.util.function.Function.identity()));
            long exposedCritical=openRows.stream().filter(f->{var a=assetById.get(f.getAssetId());var v=vulnById.get(f.getCveId());return a!=null&&Boolean.TRUE.equals(a.getInternetExposed())&&v!=null&&(v.getSeverity()==Severity.CRITICAL||v.getSeverity()==Severity.HIGH);}).count();
            List<com.gazellio.platform.model.SecurityIncident> incidentRows=incidents.findActive(PageRequest.of(0,1000));
            List<com.gazellio.platform.model.SecurityIncident> openIncidents=incidentRows.stream().filter(i->!List.of(IncidentStatus.CLOSED,IncidentStatus.RESOLVED,IncidentStatus.EXEMPTED,IncidentStatus.FALSE_POSITIVE).contains(i.getStatus())).toList();
            long overdueCount=openIncidents.stream().filter(i->i.getDueAt()!=null&&i.getDueAt().isBefore(reportNow)).count();
            double slaCompliance=openIncidents.isEmpty()?100.0:Math.round((openIncidents.size()-overdueCount)*1000.0/openIncidents.size())/10.0;
            Map<String,long[]> ownerStats=new HashMap<>();
            openRows.forEach(f->ownerStats.computeIfAbsent(value(f.getOwnerName(),"未分派"),k->new long[2])[0]++);
            openIncidents.stream().filter(i->i.getDueAt()!=null&&i.getDueAt().isBefore(reportNow)).forEach(i->ownerStats.computeIfAbsent(value(i.getOwnerName(),"未分派"),k->new long[2])[1]++);
            List<OwnerBacklogView> ownerBacklog=ownerStats.entrySet().stream()
                    .map(e->new OwnerBacklogView(e.getKey(),e.getValue()[0],e.getValue()[1]))
                    .sorted(Comparator.comparingLong(OwnerBacklogView::openFindings).reversed()).limit(8).toList();
            Map<Long,List<com.gazellio.platform.model.Finding>> byAsset=openRows.stream().collect(java.util.stream.Collectors.groupingBy(com.gazellio.platform.model.Finding::getAssetId));
            List<RiskAssetView> riskAssets=byAsset.entrySet().stream().map(e->{var a=assetById.get(e.getKey());if(a==null)return null;double highest=e.getValue().stream().mapToDouble(com.gazellio.platform.model.Finding::getRiskScore).max().orElse(0);return new RiskAssetView(a.getId(),a.getAssetCode(),a.getName(),a.getEnvironment().name(),Boolean.TRUE.equals(a.getInternetExposed()),a.getCriticality(),e.getValue().size(),Math.round(highest*10.0)/10.0);})
                    .filter(Objects::nonNull).sorted((left,right)->{int byRisk=Double.compare(right.highestRisk(),left.highestRisk());return byRisk!=0?byRisk:Long.compare(right.openFindings(),left.openFindings());}).limit(8).toList();

            ReportView result = new ReportView(
                    reportNow.toString(),30,assets.countByActiveTrue(),
                    vulns.count(),
                    findings.countActiveOpen(CLOSED_FINDINGS),
                    findings.countActiveByStatus(FindingStatus.RESOLVED),
                    findings.countActiveByStatus(FindingStatus.FALSE_POSITIVE),
                    findings.countActiveByStatus(FindingStatus.EXEMPTED),
                    overdueCount,
                    deployments.countByStatus(DeploymentStatus.RUNNING),
                    deployments.countByStatus(DeploymentStatus.FAILED),
                    tasks.countActiveByStatusIn(List.of(TaskStatus.OPEN, TaskStatus.IN_PROGRESS, TaskStatus.BLOCKED)),
                    approvals.countActiveByStatus(ApprovalStatus.PENDING),
                    totalRuns,
                    succeededRuns,
                    withoutPatch,
                    exposedCritical,
                    slaCompliance,
                    totalRuns == 0 ? 100.0 : Math.round(succeededRuns * 1000.0 / totalRuns) / 10.0,
                    patchCompliance(),
                    severity,
                    environment,
                    deploymentStatus,
                    trend(allActive,reportNow,30),ownerBacklog,riskAssets
            );
            cachedReport = result;
            reportExpiresAt = now.plus(REPORT_CACHE_TTL);
            return result;
        }
    }

    private static String value(String value,String fallback){return value==null||value.isBlank()?fallback:value;}

    private Map<Severity, Long> severityCounts() {
        EnumMap<Severity, Long> result = new EnumMap<>(Severity.class);
        findings.countOpenBySeverity(CLOSED_FINDINGS)
                .forEach(row -> result.put(row.getSeverity(), row.getTotal()));
        return result;
    }

    private Map<EnvironmentType, Long> environmentCounts() {
        EnumMap<EnvironmentType, Long> result = new EnumMap<>(EnvironmentType.class);
        findings.countOpenByEnvironment(CLOSED_FINDINGS)
                .forEach(row -> result.put(row.getEnvironment(), row.getTotal()));
        return result;
    }

    private double patchCompliance() {
        long totalAssets = assets.countByActiveTrue();
        if (totalAssets == 0) return 100.0;
        long nonCompliant = findings.countNonCompliantAssets(
                CLOSED_FINDINGS, List.of(Severity.CRITICAL, Severity.HIGH));
        long compliant = Math.max(0, totalAssets - nonCompliant);
        return Math.round(compliant * 1000.0 / totalAssets) / 10.0;
    }
}
