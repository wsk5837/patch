package com.gazellio.platform.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name="audit_events", indexes=@Index(name="idx_audit_entity", columnList="entityType,entityId"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditEvent {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false, length=80) private String entityType;
    @Column(nullable=false, length=80) private String entityId;
    @Column(nullable=false, length=80) private String action;
    @Column(nullable=false, length=800) private String messageZh;
    @Column(nullable=false, length=800) private String messageEn;
    @Column(length=120) private String actor;
    @Column(length=80) private String sourceIp;
    @Column(length=500) private String userAgent;
    @Column(length=64) private String previousHash;
    @Column(length=64) private String eventHash;
    @Column(length=20) private String hashVersion;
    @Column(nullable=false) @Builder.Default private Instant createdAt = Instant.now();
}
