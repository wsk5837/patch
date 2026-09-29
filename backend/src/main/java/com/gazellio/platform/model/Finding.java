package com.gazellio.platform.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import static com.gazellio.platform.model.Enums.FindingStatus;

@Entity @Table(name="findings", uniqueConstraints=@UniqueConstraint(name="uk_finding_asset_cve", columnNames={"asset_id","cve_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Finding {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="cve_id", nullable=false, length=32) private String cveId;
    @Column(name="asset_id", nullable=false) private Long assetId;
    private Long scanJobId;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=30) @Builder.Default private FindingStatus status = FindingStatus.NEW;
    @Column(nullable=false) @Builder.Default private Double riskScore = 0.0;
    @Column(nullable=false) @Builder.Default private Integer occurrences = 1;
    @Column(nullable=false) @Builder.Default private Instant firstSeenAt = Instant.now();
    @Column(nullable=false) @Builder.Default private Instant lastSeenAt = Instant.now();
    private Instant resolvedAt;
    private Instant exemptedAt;
    private Instant exemptionExpiresAt;
    private Long ownerId;
    @Column(length=120) private String ownerName;
    private Long remediationTaskId;
    @Column(columnDefinition="TEXT") private String evidence;
    @Column(columnDefinition="TEXT") private String falsePositiveReason;
    @Column(columnDefinition="TEXT") private String exemptionReason;
}
