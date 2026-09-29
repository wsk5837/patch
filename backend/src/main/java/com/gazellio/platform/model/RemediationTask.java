package com.gazellio.platform.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import static com.gazellio.platform.model.Enums.*;

@Entity @Table(name="remediation_tasks", indexes={
        @Index(name="idx_task_status_updated", columnList="status,updated_at"),
        @Index(name="idx_task_finding", columnList="finding_id"),
        @Index(name="idx_task_asset", columnList="asset_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RemediationTask {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(unique=true, nullable=false, length=80) private String taskNo;
    @Column(nullable=false) private Long findingId;
    private Long securityIncidentId;
    private Long changeOrderId;
    private Long patchId;
    @Column(nullable=false) private Long assetId;
    private Long ownerId;
    @Column(length=120) private String ownerName;
    @Column(nullable=false, length=20) private String priority;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=30) @Builder.Default private TaskStage stage = TaskStage.ASSIGNED;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=30) @Builder.Default private TaskStatus status = TaskStatus.OPEN;
    @Enumerated(EnumType.STRING) @Column(length=30) private ChangeType changeType;
    private Long approvalId;
    private Long latestRunId;
    private Instant dueAt;
    @Column(nullable=false) @Builder.Default private Instant createdAt = Instant.now();
    @Column(nullable=false) @Builder.Default private Instant updatedAt = Instant.now();
}
