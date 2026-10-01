package com.gazellio.platform.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import static com.gazellio.platform.model.Enums.RunStepStatus;

@Entity @Table(name="orchestration_run_steps", uniqueConstraints=@UniqueConstraint(name="uk_run_step", columnNames={"run_id","step_order"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrchestrationRunStep {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="run_id", nullable=false) private Long runId;
    @Column(name="step_order", nullable=false) private Integer stepOrder;
    @Column(nullable=false, length=80) private String code;
    @Column(nullable=false, length=180) private String nameZh;
    @Column(nullable=false, length=180) private String nameEn;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=30) @Builder.Default private RunStepStatus status = RunStepStatus.WAITING;
    private Instant startedAt;
    private Instant completedAt;
    @Column(columnDefinition="TEXT") private String messageZh;
    @Column(columnDefinition="TEXT") private String messageEn;
}
