package com.gazellio.platform.repository;

import com.gazellio.platform.model.UserRoleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface UserRoleAssignmentRepository extends JpaRepository<UserRoleAssignment,Long> {
    List<UserRoleAssignment> findByUserId(Long userId);
    List<UserRoleAssignment> findByUserIdIn(Collection<Long> userIds);
    List<UserRoleAssignment> findByRoleId(Long roleId);
    long countByRoleId(Long roleId);
    boolean existsByUserIdAndRoleId(Long userId,Long roleId);
    void deleteByUserId(Long userId);
    void deleteByRoleId(Long roleId);
}
