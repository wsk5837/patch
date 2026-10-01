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
    @Column(length=120) private String department;
    @Column(length=80) private String employeeNo;
    @Column(length=40) private String phone;
    @Column(length=30) @Builder.Default private String accountType = "LOCAL";
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=30) private UserRole role;
    @Column(name="access_role_id") private Long accessRoleId;
    @Column(nullable=false) @Builder.Default private boolean enabled = true;
    @Column(nullable=false) @Builder.Default private boolean locked = false;
    @Column(nullable=false) @Builder.Default private Integer failedLoginAttempts = 0;
    private Instant lastLoginAt;
    private Instant passwordChangedAt;
    @Column(nullable=false) @Builder.Default private Instant updatedAt = Instant.now();
    @Column(nullable=false) @Builder.Default private Instant createdAt = Instant.now();
}
