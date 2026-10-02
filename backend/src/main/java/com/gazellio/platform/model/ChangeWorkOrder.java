package com.gazellio.platform.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import static com.gazellio.platform.model.Enums.*;

@Entity
@Table(name = "change_work_orders", indexes = {
        @Index(name = "idx_change_status_updated", columnList = "status,updated_at"),
        @Index(name = "idx_change_incident", columnList = "incident_id"),
        @Index(name = "idx_change_task", columnList = "remediation_task_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ChangeWorkOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(unique = true, nullable = false, length = 80) private String changeNo;
    @Column(nullable = false) private Long incidentId;
    @Column(nullable = false) private Long remediationTaskId;
    /** Comma-separated finding ids covered by this change; the primary task remains the execution anchor. */
    @Column(columnDefinition = "TEXT") private String linkedFindingIds;
    private Long approvalId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private ChangeType changeType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) @Builder.Default private ChangeStatus status = ChangeStatus.DRAFT;
    @Column(nullable = false, length = 300) private String summary;
    @Column(columnDefinition = "TEXT") private String riskAssessment;
    @Column(columnDefinition = "TEXT") private String implementationPlan;
    @Column(columnDefinition = "TEXT") private String rollbackPlan;
    private Instant maintenanceStart;
    private Instant maintenanceEnd;
    @Column(length = 80) private String externalChangeNo;
    @Column(length = 30) @Builder.Default private String syncStatus = "SYNCED";
    @Column(nullable = false) @Builder.Default private Instant createdAt = Instant.now();
    @Column(nullable = false) @Builder.Default private Instant updatedAt = Instant.now();
    private Instant closedAt;
}
