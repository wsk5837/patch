package com.gazellio.platform.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.*;

@Entity
@Table(name="software_lifecycle_records", indexes=@Index(name="idx_lifecycle_status_eol",columnList="status,end_of_life_date"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SoftwareLifecycleRecord {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,length=180) private String productName;
    @Column(length=120) private String versionPattern;
    @Column(nullable=false,length=30) @Builder.Default private String status="SUPPORTED";
    private LocalDate endOfSupportDate;
    private LocalDate endOfLifeDate;
    @Column(length=1000) private String vendorNoticeUrl;
    @Column(columnDefinition="TEXT") private String replacementPlan;
    @Column(length=120) private String ownerName;
    @Column(nullable=false) @Builder.Default private Instant createdAt=Instant.now();
    @Column(nullable=false) @Builder.Default private Instant updatedAt=Instant.now();
}
