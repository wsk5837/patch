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

            ReportView result = new ReportView(
                    vulns.count(),
                    findings.countActiveOpen(CLOSED_FINDINGS),
                    findings.countActiveByStatus(FindingStatus.RESOLVED),
                    findings.countActiveByStatus(FindingStatus.FALSE_POSITIVE),
                    findings.countActiveByStatus(FindingStatus.EXEMPTED),
                    incidents.findTop200ByOrderByUpdatedAtDesc().stream().filter(i->i.getDueAt()!=null&&i.getDueAt().isBefore(reportNow)
                            &&!List.of(IncidentStatus.CLOSED,IncidentStatus.RESOLVED,IncidentStatus.EXEMPTED,IncidentStatus.FALSE_POSITIVE).contains(i.getStatus())).count(),
                    deployments.countByStatus(DeploymentStatus.RUNNING),
                    deployments.countByStatus(DeploymentStatus.FAILED),
                    tasks.countActiveByStatusIn(List.of(TaskStatus.OPEN, TaskStatus.IN_PROGRESS, TaskStatus.BLOCKED)),
                    approvals.countActiveByStatus(ApprovalStatus.PENDING),
                    totalRuns,
                    succeededRuns,
                    totalRuns == 0 ? 100.0 : Math.round(succeededRuns * 1000.0 / totalRuns) / 10.0,
                    patchCompliance(),
                    severity,
                    environment,
                    deploymentStatus
            );
            cachedReport = result;
            reportExpiresAt = now.plus(REPORT_CACHE_TTL);
            return result;
        }
    }

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
