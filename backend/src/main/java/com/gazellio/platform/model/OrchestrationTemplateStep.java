package com.gazellio.platform.model;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name="orchestration_template_steps", uniqueConstraints=@UniqueConstraint(name="uk_template_step", columnNames={"template_id","step_order"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrchestrationTemplateStep {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="template_id", nullable=false) private Long templateId;
    @Column(name="step_order", nullable=false) private Integer stepOrder;
    @Column(nullable=false, length=80) private String code;
    @Column(nullable=false, length=180) private String nameZh;
    @Column(nullable=false, length=180) private String nameEn;
    @Column(nullable=false, length=40) @Builder.Default private String stepType = "ACTION";
    @Column(nullable=false) @Builder.Default private boolean rollbackPoint = false;
}
