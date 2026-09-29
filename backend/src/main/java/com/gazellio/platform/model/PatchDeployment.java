package com.gazellio.platform.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import static com.gazellio.platform.model.Enums.DeploymentStatus;

@Entity @Table(name="patch_deployments", indexes={
        @Index(name="idx_deployment_created", columnList="created_at"),
        @Index(name="idx_deployment_task", columnList="task_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PatchDeployment {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(unique=true, nullable=false, length=80) private String deploymentNo;
    @Column(nullable=false) private Long taskId;
    @Column(nullable=false) private Long patchId;
    @Column(nullable=false, length=20) private String environment;
    @Column(nullable=false, length=80) private String ring;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=30) @Builder.Default private DeploymentStatus status = DeploymentStatus.PENDING;
    @Column(nullable=false) @Builder.Default private Integer progress = 0;
    private Long orchestrationRunId;
    @Column(nullable=false) @Builder.Default private Integer targetCount = 1;
    @Column(nullable=false) @Builder.Default private Integer successCount = 0;
    @Column(nullable=false) @Builder.Default private Integer failureCount = 0;
    @Column(nullable=false) @Builder.Default private Instant createdAt = Instant.now();
    private Instant startedAt;
    private Instant completedAt;
}
