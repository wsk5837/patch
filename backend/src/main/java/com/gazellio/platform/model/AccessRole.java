package com.gazellio.platform.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name="access_roles")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AccessRole {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(unique=true,nullable=false,length=60) private String code;
    @Column(nullable=false,length=120) private String nameZh;
    @Column(nullable=false,length=120) private String nameEn;
    @Column(columnDefinition="TEXT") private String descriptionZh;
    @Column(columnDefinition="TEXT") private String descriptionEn;
    @Column(nullable=false,length=30) @Builder.Default private String dataScope="ALL";
    @Column(nullable=false) @Builder.Default private boolean systemRole=true;
    @Column(nullable=false) @Builder.Default private boolean enabled=true;
    @Column(nullable=false) @Builder.Default private Instant updatedAt=Instant.now();
}
