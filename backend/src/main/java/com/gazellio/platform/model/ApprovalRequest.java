package com.gazellio.platform.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import static com.gazellio.platform.model.Enums.*;

@Entity @Table(name="approval_requests")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ApprovalRequest {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(unique=true, nullable=false, length=80) private String approvalNo;
    @Column(nullable=false) private Long taskId;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=30) private ChangeType changeType;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=30) @Builder.Default private ApprovalStatus status = ApprovalStatus.PENDING;
    @Column(nullable=false) @Builder.Default private Integer currentStep = 1;
    private Long requestedById;
    @Column(length=120) private String requestedByName;
    @Column(nullable=false) @Builder.Default private Instant submittedAt = Instant.now();
    private Instant completedAt;
    @Column(columnDefinition="TEXT") private String reason;
    @Column(columnDefinition="TEXT") private String rollbackPlan;
}
