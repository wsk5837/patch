package com.gazellio.platform.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import static com.gazellio.platform.model.Enums.ApprovalStepStatus;

@Entity @Table(name="approval_steps", uniqueConstraints=@UniqueConstraint(name="uk_approval_step", columnNames={"approval_id","step_order"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ApprovalStep {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="approval_id", nullable=false) private Long approvalId;
    @Column(name="step_order", nullable=false) private Integer stepOrder;
    @Column(nullable=false, length=120) private String roleNameZh;
    @Column(nullable=false, length=120) private String roleNameEn;
    private Long approverId;
    @Column(length=120) private String approverName;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=30) @Builder.Default private ApprovalStepStatus status = ApprovalStepStatus.WAITING;
    @Column(columnDefinition="TEXT") private String comment;
    private Instant actedAt;
}
