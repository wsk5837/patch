package com.gazellio.platform.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name="regulatory_incident_cases", indexes={
        @Index(name="idx_reg_case_status",columnList="status"),
        @Index(name="idx_reg_case_notice_due",columnList="notice_due_at")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RegulatoryIncidentCase {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(unique=true,nullable=false,length=80) private String caseNo;
    @Column(name="security_incident_id",nullable=false,unique=true) private Long securityIncidentId;
    @Column(nullable=false,length=30) @Builder.Default private String status="ASSESSING";
    @Column(nullable=false) @Builder.Default private boolean reportable=false;
    @Column(columnDefinition="TEXT") private String assessment;
    @Column(length=120) private String assessorName;
    private Instant discoveredAt;
    private Instant noticeDueAt;
    private Instant noticeSubmittedAt;
    @Column(length=160) private String noticeReference;
    private Instant rcaDueAt;
    private Instant rcaSubmittedAt;
    @Column(columnDefinition="TEXT") private String rootCause;
    @Column(columnDefinition="TEXT") private String impactAnalysis;
    @Column(columnDefinition="TEXT") private String correctiveActions;
    @Column(nullable=false) @Builder.Default private Instant createdAt=Instant.now();
    @Column(nullable=false) @Builder.Default private Instant updatedAt=Instant.now();
}
