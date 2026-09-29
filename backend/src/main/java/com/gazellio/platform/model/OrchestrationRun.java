package com.gazellio.platform.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import static com.gazellio.platform.model.Enums.RunStatus;

@Entity @Table(name="orchestration_runs", indexes={
        @Index(name="idx_run_status_created", columnList="status,created_at"),
        @Index(name="idx_run_task", columnList="task_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrchestrationRun {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(unique=true, nullable=false, length=80) private String runNo;
    @Column(nullable=false) private Long templateId;
    private Long taskId;
    private Long deploymentId;
    @Column(nullable=false, length=20) private String environment;
    @Column(nullable=false, length=80) private String ring;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=30) @Builder.Default private RunStatus status = RunStatus.QUEUED;
    @Column(nullable=false) @Builder.Default private Integer currentStep = 0;
    @Column(nullable=false) @Builder.Default private Integer progress = 0;
    @Column(nullable=false) @Builder.Default private Instant createdAt = Instant.now();
    private Instant startedAt;
    private Instant completedAt;
    @Column(columnDefinition="TEXT") private String failureReason;
}
