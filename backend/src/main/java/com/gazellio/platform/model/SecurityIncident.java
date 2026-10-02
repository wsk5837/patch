package com.gazellio.platform.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import static com.gazellio.platform.model.Enums.IncidentStatus;

@Entity
@Table(name = "security_incidents", indexes = {
        @Index(name = "idx_incident_status_updated", columnList = "status,updated_at"),
        @Index(name = "idx_incident_finding", columnList = "finding_id"),
        @Index(name = "idx_incident_owner", columnList = "owner_id,status")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SecurityIncident {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(unique = true, nullable = false, length = 80) private String incidentNo;
    @Column(unique = true, nullable = false) private Long findingId;
    /** Comma-separated finding ids linked to this aggregate incident; findingId remains the primary finding. */
    @Column(columnDefinition = "TEXT") private String linkedFindingIds;
    @Column(nullable = false) private Long assetId;
    @Column(nullable = false, length = 20) private String priority;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) @Builder.Default private IncidentStatus status = IncidentStatus.OPEN;
    private Long ownerId;
    @Column(length = 120) private String ownerName;
    private Long remediationTaskId;
    private Long changeOrderId;
    private Instant dueAt;
    @Column(length = 30) @Builder.Default private String syncStatus = "SYNCED";
    @Column(length = 80) private String externalTicketNo;
    @Column(columnDefinition = "TEXT") private String decisionReason;
    @Column(nullable = false) @Builder.Default private Instant createdAt = Instant.now();
    @Column(nullable = false) @Builder.Default private Instant updatedAt = Instant.now();
    private Instant resolvedAt;
    private Instant closedAt;
}
