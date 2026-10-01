package com.gazellio.platform.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name="orchestration_templates")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrchestrationTemplate {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(unique=true, nullable=false, length=80) private String code;
    @Column(nullable=false, length=200) private String nameZh;
    @Column(nullable=false, length=200) private String nameEn;
    @Column(nullable=false, length=60) private String type;
    @Column(nullable=false) @Builder.Default private boolean enabled = true;
    @Column(nullable=false) @Builder.Default private Integer version = 1;
    @Column(nullable=false) @Builder.Default private Instant updatedAt = Instant.now();
}
