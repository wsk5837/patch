package com.gazellio.platform.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name="patch_schedules")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PatchSchedule {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,length=200) private String titleZh;
    @Column(nullable=false,length=200) private String titleEn;
    @Column(nullable=false) private Long patchId;
    @Column(nullable=false,length=30) private String environment;
    @Column(nullable=false) private Instant startAt;
    @Column(nullable=false) private Instant endAt;
    @Column(nullable=false,length=30) private String status;
    private Long ownerId;
    @Column(length=120) private String ownerName;
    @Column(length=2000) private String notes;
    private Long createdById;
    @Column(length=120) private String createdByName;
    @Column(nullable=false) @Builder.Default private Instant createdAt=Instant.now();
    @Column(nullable=false) @Builder.Default private Instant updatedAt=Instant.now();
}
