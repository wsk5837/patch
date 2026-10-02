package com.gazellio.platform.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name="report_snapshots",indexes=@Index(name="idx_report_snapshot_created",columnList="created_at"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReportSnapshot {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,unique=true,length=40) private String snapshotNo;
    @Column(nullable=false) private Integer windowDays;
    @Column(nullable=false,length=120) private String createdBy;
    @Column(nullable=false) @Builder.Default private Instant createdAt=Instant.now();
    @Column(nullable=false,columnDefinition="TEXT") private String reportJson;
}
