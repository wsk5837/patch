package com.gazellio.platform.model;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name="role_permissions",uniqueConstraints=@UniqueConstraint(name="uk_role_permission",columnNames={"role_id","permission_code"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RolePermission {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="role_id",nullable=false) private Long roleId;
    @Column(name="permission_code",nullable=false,length=80) private String permissionCode;
}
