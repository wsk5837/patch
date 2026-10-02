package com.gazellio.platform.dto;

import jakarta.validation.constraints.*;
import java.util.*;

public final class ComplianceDtos {
    private ComplianceDtos(){}

    public record ComplianceSummary(long pendingExceptions,long pendingRiskOverrides,long openAlerts,
                                    long lifecycleRisks,long openRegulatoryCases,String auditIntegrity,
                                    String auditCheckedAt){}
    public record ExceptionCreateRequest(@NotNull Long findingId,@NotBlank String reason,
                                         @NotBlank String compensatingControl,@NotBlank String residualRisk,
                                         @NotBlank String expiresAt){}
    public record DecisionRequest(@NotBlank String comment){}
    public record ExceptionView(Long id,String requestNo,Long findingId,String cveId,String assetName,
                                String status,String requesterName,String approverName,String reason,
                                String compensatingControl,String residualRisk,String expiresAt,
                                String decisionComment,String submittedAt,String decidedAt,String revokedAt,
                                boolean canApprove){}
    public record LifecycleSaveRequest(@NotBlank String productName,String versionPattern,@NotBlank String status,
                                       String endOfSupportDate,String endOfLifeDate,String vendorNoticeUrl,
                                       String replacementPlan,String ownerName){}
    public record LifecycleView(Long id,String productName,String versionPattern,String status,
                                String endOfSupportDate,String endOfLifeDate,String vendorNoticeUrl,
                                String replacementPlan,String ownerName,long affectedAssets,String updatedAt){}
    public record RegulatoryCreateRequest(@NotNull Long securityIncidentId,boolean reportable,
                                          @NotBlank String assessment,String discoveredAt){}
    public record RegulatoryUpdateRequest(@NotBlank String status,boolean reportable,String assessment,
                                          String noticeSubmittedAt,String noticeReference,String rcaSubmittedAt,
                                          String rootCause,String impactAnalysis,String correctiveActions){}
    public record RegulatoryCaseView(Long id,String caseNo,Long securityIncidentId,String incidentNo,
                                     String status,boolean reportable,String assessment,String assessorName,
                                     String discoveredAt,String noticeDueAt,String noticeSubmittedAt,
                                     String noticeReference,String rcaDueAt,String rcaSubmittedAt,
                                     String rootCause,String impactAnalysis,String correctiveActions,
                                     String createdAt,String updatedAt){}
    public record AlertView(Long id,String alertType,String severity,String status,String entityType,
                            String entityId,String messageZh,String messageEn,String dueAt,String createdAt,
                            String acknowledgedAt,String acknowledgedBy){}
    public record RiskOverrideCreateRequest(@NotNull Long findingId,@NotNull @DecimalMin("0.0") @DecimalMax("10.0") Double requestedScore,
                                            @NotBlank String reason){}
    public record RiskOverrideView(Long id,String requestNo,Long findingId,String cveId,String assetName,
                                   Double currentScore,Double requestedScore,String reason,String status,
                                   String requesterName,String approverName,String decisionComment,
                                   String submittedAt,String decidedAt,boolean canApprove){}
    public record AuditIntegrityView(String status,long totalEvents,long verifiedEvents,Long firstInvalidEventId,
                                     String chainHead,String checkedAt){}
    public record TargetResultRequest(@NotBlank String status,String resultCode,String message,String failureReason){}
}
