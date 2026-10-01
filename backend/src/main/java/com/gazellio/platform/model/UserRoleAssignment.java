package com.gazellio.platform.model;

import jakarta.persistence.*;
import lombok.*;

/** Many-to-many assignment between users and RBAC roles. */
@Entity
@Table(name="user_role_assignments",
        uniqueConstraints=@UniqueConstraint(name="uk_user_role_assignment",columnNames={"user_id","role_id"}),
        indexes={@Index(name="idx_user_role_user",columnList="user_id"),@Index(name="idx_user_role_role",columnList="role_id")})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserRoleAssignment {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="user_id",nullable=false) private Long userId;
    @Column(name="role_id",nullable=false) private Long roleId;
}
