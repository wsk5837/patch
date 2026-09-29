package com.gazellio.platform.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name="system_settings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SystemSetting {
    @Id @Column(length=100) private String settingKey;
    @Column(columnDefinition="TEXT") private String settingValue;
    @Column(nullable=false) @Builder.Default private Instant updatedAt = Instant.now();
}
