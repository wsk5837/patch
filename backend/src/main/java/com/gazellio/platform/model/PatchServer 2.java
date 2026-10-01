package com.gazellio.platform.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name="patch_servers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PatchServer {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(unique=true, nullable=false, length=120) private String name;
    @Column(nullable=false, length=200) private String address;
    @Column(length=100) private String region;
    @Column(length=200) private String osSupport;
    @Column(nullable=false, length=30) @Builder.Default private String status="ONLINE";
    private Instant lastSyncAt;
    private Double capacityGb;
    private Double usedGb;
}
