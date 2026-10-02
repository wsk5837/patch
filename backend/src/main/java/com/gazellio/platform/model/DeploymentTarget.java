package com.gazellio.platform.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "deployment_targets", uniqueConstraints = {
        @UniqueConstraint(name = "uk_deployment_target", columnNames = {"deployment_id", "asset_id"}),
        @UniqueConstraint(name = "uk_run_target", columnNames = {"run_id", "asset_id"})
}, indexes = {
        @Index(name = "idx_deployment_target", columnList = "deployment_id,status"),
        @Index(name = "idx_run_target", columnList = "run_id,status")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DeploymentTarget {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "deployment_id") private Long deploymentId;
    @Column(name = "run_id") private Long runId;
    @Column(name = "asset_id", nullable = false) private Long assetId;
    @Builder.Default private Integer batchNo = 1;
    @Column(nullable = false, length = 30) @Builder.Default private String status = "WAITING";
    @Column(nullable = false) @Builder.Default private Integer progress = 0;
    private Instant startedAt;
    private Instant completedAt;
    @Column(columnDefinition = "TEXT") private String message;
    @Builder.Default private Integer retryCount = 0;
    @Builder.Default private Integer maxRetries = 2;
    @Column(columnDefinition="TEXT") private String failureReason;
    @Column(length=80) private String resultCode;
}
