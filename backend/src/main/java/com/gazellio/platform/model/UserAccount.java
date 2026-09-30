package com.gazellio.platform.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import static com.gazellio.platform.model.Enums.UserRole;

@Entity @Table(name="user_accounts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserAccount {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(unique=true, nullable=false, length=80) private String username;
    @Column(nullable=false, length=120) private String passwordHash;
    @Column(nullable=false, length=120) private String displayName;
    @Column(length=160) private String email;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=30) private UserRole role;
    @Column(name="access_role_id") private Long accessRoleId;
    @Column(nullable=false) @Builder.Default private boolean enabled = true;
    @Column(nullable=false) @Builder.Default private Instant createdAt = Instant.now();
}
