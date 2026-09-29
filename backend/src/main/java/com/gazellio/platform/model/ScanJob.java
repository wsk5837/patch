package com.gazellio.platform.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import static com.gazellio.platform.model.Enums.ScanStatus;

@Entity @Table(name="scan_jobs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ScanJob {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(unique=true, nullable=false, length=80) private String jobNo;
    @Column(nullable=false, length=200) private String name;
    @Column(nullable=false, length=40) private String scanType;
    @Column(nullable=false, length=40) private String targetType;
    @Column(columnDefinition="TEXT") private String targetValue;
    @Column(length=80) private String credentialType;
    @Column(length=32) private String targetCve;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) @Builder.Default private ScanStatus status = ScanStatus.QUEUED;
    @Column(nullable=false) @Builder.Default private Integer progress = 0;
    @Column(nullable=false) @Builder.Default private Integer findingsCount = 0;
    private Long requestedById;
    private Long remediationTaskId;
    @Column(length=120) private String requestedByName;
    @Column(nullable=false) @Builder.Default private Instant createdAt = Instant.now();
    private Instant startedAt;
    private Instant completedAt;
    @Column(columnDefinition="TEXT") private String errorMessage;
}
