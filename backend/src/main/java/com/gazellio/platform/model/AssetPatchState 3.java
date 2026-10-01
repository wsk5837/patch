package com.gazellio.platform.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name="asset_patch_states", uniqueConstraints=@UniqueConstraint(name="uk_asset_patch", columnNames={"asset_id","patch_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AssetPatchState {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="asset_id", nullable=false) private Long assetId;
    @Column(name="patch_id", nullable=false) private Long patchId;
    @Column(nullable=false) @Builder.Default private boolean installed = false;
    @Column(nullable=false) @Builder.Default private boolean verified = false;
    private Instant installedAt;
    private Instant verifiedAt;
    @Column(length=80) private String deploymentNo;
}
