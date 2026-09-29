package com.gazellio.platform.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import static com.gazellio.platform.model.Enums.EnvironmentType;

@Entity @Table(name="assets", indexes={
        @Index(name="idx_asset_active_name", columnList="active,name"),
        @Index(name="idx_asset_service_env", columnList="business_service,environment")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Asset {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(unique=true, nullable=false, length=120) private String assetCode;
    @Column(nullable=false, length=180) private String name;
    @Column(length=80) private String ipAddress;
    @Column(length=100) private String osName;
    @Column(length=80) private String osVersion;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private EnvironmentType environment;
    @Column(length=160) private String businessService;
    private Long ownerId;
    @Column(length=120) private String ownerName;
    @Column(nullable=false) @Builder.Default private Integer criticality = 3;
    @Column(length=30) @Builder.Default private String agentStatus = "OFFLINE";
    @Column(length=80) private String patchBaseline;
    private Instant lastSeenAt;
    @Column(nullable=false) @Builder.Default private boolean active = true;
}
