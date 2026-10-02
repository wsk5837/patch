package com.gazellio.platform.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name="risk_override_requests",indexes=@Index(name="idx_risk_override_status",columnList="status"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RiskOverrideRequest {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(unique=true,nullable=false,length=80) private String requestNo;
    @Column(nullable=false) private Long findingId;
    @Column(nullable=false) private Double requestedScore;
    @Column(nullable=false,columnDefinition="TEXT") private String reason;
    private Long requesterId;
    @Column(nullable=false,length=120) private String requesterName;
    @Column(nullable=false,length=30) @Builder.Default private String status="PENDING";
    private Long approverId;
    @Column(length=120) private String approverName;
    @Column(columnDefinition="TEXT") private String decisionComment;
    @Column(nullable=false) @Builder.Default private Instant submittedAt=Instant.now();
    private Instant decidedAt;
}
