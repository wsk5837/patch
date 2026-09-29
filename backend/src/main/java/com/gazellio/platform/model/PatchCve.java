package com.gazellio.platform.model;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name="patch_cves", uniqueConstraints=@UniqueConstraint(name="uk_patch_cve", columnNames={"patch_id","cve_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PatchCve {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="patch_id", nullable=false) private Long patchId;
    @Column(name="cve_id", nullable=false, length=32) private String cveId;
}
