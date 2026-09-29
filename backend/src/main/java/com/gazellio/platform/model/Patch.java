package com.gazellio.platform.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.Instant;

@Entity @Table(name="patches")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Patch {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(unique=true, nullable=false, length=120) private String patchId;
    @Column(nullable=false, length=160) private String vendor;
    @Column(length=160) private String product;
    @Column(length=120) private String version;
    @Column(nullable=false, length=400) private String titleZh;
    @Column(nullable=false, length=400) private String titleEn;
    @Column(length=1000) private String downloadUrl;
    @Column(length=128) private String checksum;
    @Column(columnDefinition="TEXT") private String applicabilityRule;
    @Column(length=30) @Builder.Default private String signatureStatus = "VERIFIED";
    @Column(length=160) private String supersedes;
    @Column(columnDefinition="TEXT") private String releaseNotesZh;
    @Column(columnDefinition="TEXT") private String releaseNotesEn;
    private Double sizeMb;
    @Column(nullable=false) @Builder.Default private boolean rebootRequired = false;
    @Column(nullable=false, length=30) @Builder.Default private String status = "AVAILABLE";
    @Column(nullable=false, length=80) @Builder.Default private String source = "Vendor";
    private LocalDate publishedDate;
    @Column(nullable=false) @Builder.Default private Instant updatedAt = Instant.now();
}
