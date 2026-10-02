package com.gazellio.platform.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name="control_alerts", uniqueConstraints=@UniqueConstraint(name="uk_control_alert_key",columnNames="alert_key"),
        indexes=@Index(name="idx_control_alert_status_due",columnList="status,due_at"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ControlAlert {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="alert_key",nullable=false,length=180) private String alertKey;
    @Column(nullable=false,length=40) private String alertType;
    @Column(nullable=false,length=20) private String severity;
    @Column(nullable=false,length=30) @Builder.Default private String status="OPEN";
    @Column(nullable=false,length=80) private String entityType;
    @Column(nullable=false,length=80) private String entityId;
    @Column(nullable=false,length=800) private String messageZh;
    @Column(nullable=false,length=800) private String messageEn;
    private Instant dueAt;
    @Column(nullable=false) @Builder.Default private Instant createdAt=Instant.now();
    private Instant acknowledgedAt;
    @Column(length=120) private String acknowledgedBy;
}
