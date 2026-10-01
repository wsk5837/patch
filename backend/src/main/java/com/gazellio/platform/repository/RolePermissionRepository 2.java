package com.gazellio.platform.repository;
import com.gazellio.platform.model.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface RolePermissionRepository extends JpaRepository<RolePermission,Long>{
 List<RolePermission> findByRoleId(Long roleId);
 List<RolePermission> findByRoleIdIn(Collection<Long> roleIds);
 void deleteByRoleId(Long roleId);
}
