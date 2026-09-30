package com.gazellio.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class ApiDtos {
    private ApiDtos() {}

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record UserView(Long id, String username, String displayName, String email, String role) {}
    public record LoginResponse(String token, UserView user) {}
    public record MessageResponse(String message) {}
    public record CisaKevSyncResult(String catalogVersion, String catalogReleasedAt, int catalogTotal,
                                    int matchedByPatchLibrary, int created, int updated, int withoutPatchMapping,
                                    String syncedAt, String source) {}

    public record ScanCreateRequest(@NotBlank String name, @NotBlank String scanType, @NotBlank String targetType,
                                    @NotBlank String targetValue, String credentialType) {}
    public record AgentRegisterRequest(@NotBlank String agentKey, @NotBlank String hostname, String ipAddress,
                                       String osName, String version, Long assetId) {}
    public record AgentHeartbeatRequest(String ipAddress, String osName, String version) {}
    public record AgentFindingItem(@NotBlank String cveId, String evidence) {}
    public record AgentResultRequest(@NotNull Long scanJobId, @NotNull Long assetId, @NotNull List<AgentFindingItem> findings) {}

    public record FindingActionRequest(String reason, Long patchId, String expiresAt) {}
    public record IncidentActionRequest(Long ownerId, String ownerName, Long patchId, String reason) {}
    public record ChangeCreateRequest(@NotBlank String changeType, @NotBlank String summary,
                                      String riskAssessment, String implementationPlan, String rollbackPlan,
                                      String maintenanceStart, String maintenanceEnd) {}
    public record TaskActionRequest(String result, String comment, String retestMode, String changeType, String reason, String rollbackPlan) {}
    public record TaskAssignRequest(Long ownerId, @NotBlank String ownerName) {}
    public record ApprovalActionRequest(String comment) {}
    public record SettingsUpdateRequest(Map<String,String> values) {}

    public record AssetView(Long id, String assetCode, String name, String hostname, String ipAddress, String networkSegment,
                            String assetType, String zone, boolean internetExposed, String osName, String osVersion,
                            String environment, String businessService, Long ownerId, String ownerName, Integer criticality,
                            String agentStatus, String patchBaseline, String installedProducts, String maintenanceWindow,
                            Instant lastSeenAt, long openFindings) {}

    public record AssetScopeOptions(List<String> networkSegments, List<String> assetTypes,
                                    List<String> businessServices, List<String> osNames) {}

    public record BatchScopeRequest(@NotNull Long patchId, @NotNull List<String> cidrs,
                                    List<String> environments, List<String> assetTypes, String osName,
                                    String businessService, boolean onlineOnly, List<Long> excludedAssetIds,
                                    Integer batchSize, Integer concurrency, Double failureThreshold,
                                    String planName, String maintenanceWindow) {}
    public record BatchScopePreview(long matchedCount, long applicableCount, long selectedCount,
                                    long excludedCount, long offlineCount,
                                    int batchSize, int concurrency, int totalBatches, double failureThreshold,
                                    String patchCode, String patchProduct, List<String> cidrs,
                                    Map<String,Long> environmentCounts, Map<String,Long> typeCounts,
                                    List<AssetView> assets, List<String> warnings) {}
    public record BatchRunResult(Long runId, String runNo, Long deploymentId, String deploymentNo,
                                 int targetCount, int totalBatches) {}

    public record PatchCandidateView(Long id, String patchId, String titleZh, String titleEn, String version,
                                     String signatureStatus, boolean rebootRequired, String status) {}

    public record VulnerabilityView(String cveId, String titleZh, String titleEn, String vendor, String product,
                                    String descriptionZh, String descriptionEn, Double cvss, String severity, boolean kev,
                                    boolean ransomwareKnown, boolean patchAvailable, String referenceUrl,
                                    String publishedDate, String kevDueDate, long affectedAssets, List<String> patchIds,
                                    List<PatchCandidateView> patches, String internalAdvisoryId, String attackVector,
                                    String attackComplexity, String privilegesRequired, String affectedVersionRange,
                                    String detectionGuidanceZh, String detectionGuidanceEn,
                                    String remediationGuidanceZh, String remediationGuidanceEn,
                                    String mitigationZh, String mitigationEn, boolean virtualPatchAvailable,
                                    String virtualPatchGuidanceZh, String virtualPatchGuidanceEn) {}
    public record VulnerabilityPageView(List<VulnerabilityView> items, long totalElements, int page,
                                        int size, int totalPages) {}

    public record FindingView(Long id, String cveId, String titleZh, String titleEn, Double cvss, String severity,
                              boolean kev, Long assetId, String assetCode, String assetName, String environment,
                              String businessService, String ownerName, Integer assetCriticality, boolean internetExposed,
                              List<String> priorityReasons, String status, Double riskScore, Integer occurrences,
                              Long scanJobId, String scanJobNo, Long remediationTaskId, String firstSeenAt, String lastSeenAt,
                              String evidence, String falsePositiveReason, String exemptionReason, String exemptionExpiresAt,
                              Long securityIncidentId, List<String> availablePatches, List<PatchCandidateView> patchCandidates) {}

    public record ScanJobView(Long id, String jobNo, String name, String scanType, String targetType, String targetValue,
                              String credentialType, String targetCve, String status, Integer progress, Integer findingsCount,
                              String requestedByName, Long remediationTaskId, Long automationRunId, String createdAt,
                              String startedAt, String completedAt, String errorMessage) {}

    public record AgentView(Long id, String agentKey, String hostname, String ipAddress, String osName, String version,
                            String status, Long assetId, String lastHeartbeatAt) {}

    public record PatchServerView(Long id, String name, String address, String region, String osSupport, String status, String lastSyncAt, Double capacityGb, Double usedGb) {}

    public record PatchCreateRequest(@NotBlank String patchId, @NotBlank String vendor, @NotBlank String product,
                                     String version, @NotBlank String titleZh, @NotBlank String titleEn,
                                     String downloadUrl, String checksum, Double sizeMb, boolean rebootRequired,
                                     String source, List<String> cves, String applicabilityRule, String supersedes,
                                     String releaseNotesZh, String releaseNotesEn) {}

    public record PatchCveEvidenceView(String cveId, String titleZh, String titleEn, String product,
                                       Double cvss, String severity, String affectedVersionRule,
                                       String fixedVersion, String scannerRuleId, String evidenceZh,
                                       String evidenceEn) {}

    public record PatchView(Long id, String patchId, String vendor, String product, String version,
                            String titleZh, String titleEn, String downloadUrl, String checksum, Double sizeMb,
                            boolean rebootRequired, String status, String source, String publishedDate,
                            List<String> cves, long affectedAssets, String applicabilityRule, String applicabilityRuleEn, String signatureStatus,
                            String supersedes, String releaseNotesZh, String releaseNotesEn, String signatureIssuer,
                            String signatureFingerprint, String integrityVerifiedAt, String vendorAdvisoryUrl,
                            String prerequisites, String prerequisitesEn, String installCommand, String uninstallCommand, String testEvidence,
                            String knownIssues, String knownIssuesEn, List<PatchCveEvidenceView> remediationEvidence) {}

    public record PatchCalendarEventView(String eventId, String date, String endAt, String eventType,
                                         Long incidentId, String incidentNo, Long taskId, String taskNo,
                                         Long changeOrderId, String changeNo, Long patchId, String patchCode,
                                         String cveId, String assetName, String businessService, String severity,
                                         String priority, String status, long hoursRemaining) {}

    public record TaskView(Long id, String taskNo, Long findingId, String cveId, String titleZh, String titleEn,
                           Long assetId, String assetCode, String assetName, String environment, String businessService,
                           Long patchId, String patchCode, String ownerName, String priority, String stage, String status,
                           String changeType, Long approvalId, Long latestRunId, Long securityIncidentId, Long changeOrderId,
                           String dueAt, String createdAt, String updatedAt, String lastRetestMode,
                           String lastRetestResult, String lastRetestComment, String lastRetestedBy,
                           String lastRetestedAt) {}

    public record ApprovalStepView(Long id, Integer stepOrder, String roleNameZh, String roleNameEn, String approverName,
                                   String status, String comment, String actedAt) {}
    public record ApprovalView(Long id, String approvalNo, Long taskId, String taskNo, String cveId, String assetName,
                               String changeType, String status, Integer currentStep, String requestedByName,
                               String submittedAt, String completedAt, String reason, String rollbackPlan,
                               Long changeOrderId, String changeNo, List<ApprovalStepView> steps) {}

    public record SecurityIncidentView(Long id, String incidentNo, Long findingId, String cveId, String titleZh,
                                       String titleEn, String severity, boolean kev, Long assetId, String assetCode,
                                       String assetName, String environment, String businessService, String priority,
                                       String status, Long ownerId, String ownerName, Long remediationTaskId,
                                       String remediationTaskNo, Long changeOrderId, String changeNo, String dueAt,
                                       String syncStatus, String externalTicketNo, String decisionReason,
                                       String createdAt, String updatedAt, List<PatchCandidateView> patchCandidates) {}

    public record ChangeWorkOrderView(Long id, String changeNo, Long incidentId, String incidentNo,
                                      Long remediationTaskId, String remediationTaskNo, Long approvalId,
                                      String approvalNo, String changeType, String status, String summary,
                                      String cveId, String assetName, String businessService, Long patchId,
                                      String patchCode, Long latestRunId, String riskAssessment,
                                      String implementationPlan, String rollbackPlan, String maintenanceStart,
                                      String maintenanceEnd, String syncStatus, String externalChangeNo,
                                      String createdAt, String updatedAt, String closedAt) {}

    public record TemplateStepView(Long id, Integer stepOrder, String code, String nameZh, String nameEn,
                                   String stepType, boolean rollbackPoint) {}
    public record TemplateView(Long id, String code, String nameZh, String nameEn, String type, boolean enabled,
                               Integer version, List<TemplateStepView> steps) {}

    public record RunStepView(Long id, Integer stepOrder, String code, String nameZh, String nameEn, String status,
                              String startedAt, String completedAt, String messageZh, String messageEn) {}
    public record DeploymentTargetView(Long id, Long assetId, String assetCode, String assetName, String environment,
                                       Integer batchNo, String status, Integer progress, String startedAt,
                                       String completedAt, String message) {}
    public record RunView(Long id, String runNo, Long templateId, String templateCode, String templateNameZh,
                          String templateNameEn, Long taskId, String taskNo, Long deploymentId, String environment,
                          String ring, String status, Integer currentStep, Integer progress, String createdAt,
                          String startedAt, String completedAt, String failureReason, List<RunStepView> steps,
                          List<DeploymentTargetView> targets) {}

    public record DeploymentView(Long id, String deploymentNo, Long taskId, String taskNo, Long patchId, String patchCode,
                                 String environment, String ring, String status, Integer progress, Long orchestrationRunId,
                                 Integer targetCount, Integer successCount, Integer failureCount, String createdAt,
                                 String startedAt, String completedAt, String selectionMode, String cidrScopes,
                                 Integer batchSize, Integer concurrency, Double failureThreshold, Integer totalBatches,
                                 String scopeSummary, List<DeploymentTargetView> targets) {}

    public record AuditView(Long id, String entityType, String entityId, String action, String messageZh,
                            String messageEn, String actor, String createdAt) {}

    public record DashboardView(long openCritical, long openHigh, long openFindings, long pendingApprovals,
                                long runningScans, long runningAutomations, double patchCompliance,
                                List<FindingView> topFindings, List<ScanJobView> recentScans,
                                List<RunView> recentRuns) {}

    public record ReportView(long totalLibrary, long openFindings, long resolvedFindings, long falsePositives, long exemptions,
                             long slaOverdue, long runningDeployments, long failedDeployments,
                             long openTasks, long approvalsPending, long automationRuns, long automationSucceeded,
                             double automationSuccessRate, double patchCompliance,
                             Map<String,Long> severityDistribution, Map<String,Long> environmentDistribution,
                             List<FindingView> pendingFindings, List<FindingView> exceptionFindings,
                             List<DeploymentView> recentDeployments) {}
}
