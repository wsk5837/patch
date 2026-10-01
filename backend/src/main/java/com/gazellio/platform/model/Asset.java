package com.gazellio.platform.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import static com.gazellio.platform.model.Enums.EnvironmentType;

@Entity @Table(name="assets", indexes={
        @Index(name="idx_asset_active_name", columnList="active,name"),
        @Index(name="idx_asset_service_env", columnList="business_service,environment"),
        @Index(name="idx_asset_ip", columnList="ip_address"),
        @Index(name="idx_asset_segment_type", columnList="network_segment,asset_type"),
        @Index(name="idx_asset_cmdb_source", columnList="source_system,cmdb_class_key")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Asset {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(unique=true, nullable=false, length=120) private String assetCode;
    @Column(nullable=false, length=180) private String name;
    @Column(length=160) private String hostname;
    @Column(length=80) private String ipAddress;
    @Column(length=40) private String networkSegment;
    @Column(length=40) @Builder.Default private String assetType = "UNCLASSIFIED";
    @Column(length=80) private String zone;
    // Nullable at ORM bootstrap so an existing populated table can be upgraded safely. The
    // compatibility migration immediately backfills NULL to false and restores NOT NULL.
    @Column(nullable=true) @Builder.Default private Boolean internetExposed = false;
    @Column(length=100) private String osName;
    @Column(length=80) private String osVersion;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private EnvironmentType environment;
    @Column(length=160) private String businessService;
    private Long ownerId;
    @Column(length=120) private String ownerName;
    @Column(nullable=false) @Builder.Default private Integer criticality = 3;
    @Column(length=30) @Builder.Default private String agentStatus = "OFFLINE";
    @Column(length=80) private String patchBaseline;
    @Column(columnDefinition="TEXT") private String installedProducts;
    @Column(length=120) private String maintenanceWindow;
    private Instant lastSeenAt;
    @Column(nullable=false) @Builder.Default private boolean active = true;
    // Kept nullable at ORM bootstrap so an existing populated PostgreSQL table can be
    // upgraded without a failing ADD COLUMN ... NOT NULL statement. The compatibility
    // migration backfills and restores the constraint before data synchronization starts.
    @Column(nullable=true,length=30) @Builder.Default private String sourceSystem = "LOCAL";
    @Column(unique=true,length=80) private String cmdbItemId;
    @Column(length=80) private String cmdbClassKey;
    @Column(length=120) private String cmdbClassName;
    @Column(length=120) private String cmdbState;
    private Boolean cmdbLocked;
    private Boolean cmdbEnabled;
    private Boolean cmdbAutoDiscovery;
    private Instant cmdbUpdatedAt;
    private Instant cmdbSyncedAt;
}
