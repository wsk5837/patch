package com.gazellio.platform.service;

import com.gazellio.platform.dto.ApiDtos.*;
import com.gazellio.platform.model.Enums.*;
import com.gazellio.platform.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
            Map<Severity, Long> bySeverity = severityCounts();
            DashboardView result = new DashboardView(
                    bySeverity.getOrDefault(Severity.CRITICAL, 0L),
                    bySeverity.getOrDefault(Severity.HIGH, 0L),
                    findings.countByStatusNotIn(CLOSED_FINDINGS),
                    approvals.countByStatus(ApprovalStatus.PENDING),
                    scans.countByStatusIn(List.of(ScanStatus.QUEUED, ScanStatus.RUNNING)),
                    runs.countByStatusIn(List.of(RunStatus.QUEUED, RunStatus.RUNNING, RunStatus.PAUSED)),
                    patchCompliance(),
                    view.findingViews(findings.findTop6ByStatusNotInOrderByRiskScoreDescLastSeenAtDesc(CLOSED_FINDINGS)),
                    scans.findTop5ByOrderByCreatedAtDesc().stream().map(view::scan).toList(),
                    view.runViews(runs.findTop5ByOrderByCreatedAtDesc())
            );
            cachedDashboard = result;
            dashboardExpiresAt = now.plus(DASHBOARD_CACHE_TTL);
            return result;
        }
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

            ReportView result = new ReportView(
                    vulns.count(),
                    findings.countByStatusNotIn(CLOSED_FINDINGS),
                    findings.countByStatus(FindingStatus.RESOLVED),
                    findings.countByStatus(FindingStatus.FALSE_POSITIVE),
                    tasks.countByStatusIn(List.of(TaskStatus.OPEN, TaskStatus.IN_PROGRESS, TaskStatus.BLOCKED)),
                    approvals.countByStatus(ApprovalStatus.PENDING),
                    totalRuns,
                    succeededRuns,
                    totalRuns == 0 ? 100.0 : Math.round(succeededRuns * 1000.0 / totalRuns) / 10.0,
                    patchCompliance(),
                    severity,
                    environment
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
