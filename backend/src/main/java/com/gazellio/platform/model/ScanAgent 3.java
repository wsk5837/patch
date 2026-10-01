package com.gazellio.platform.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import static com.gazellio.platform.model.Enums.AgentStatus;

@Entity @Table(name="scan_agents")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ScanAgent {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(unique=true, nullable=false, length=120) private String agentKey;
    @Column(nullable=false, length=180) private String hostname;
    @Column(length=80) private String ipAddress;
    @Column(length=120) private String osName;
    @Column(length=80) private String version;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) @Builder.Default private AgentStatus status = AgentStatus.ONLINE;
    private Long assetId;
    private Instant lastHeartbeatAt;
    @Column(nullable=false) @Builder.Default private Instant createdAt = Instant.now();
}
