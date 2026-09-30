package com.gazellio.platform.repository;
import com.gazellio.platform.model.PatchDeployment;
import com.gazellio.platform.model.Enums.DeploymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.*;
public interface PatchDeploymentRepository extends JpaRepository<PatchDeployment, Long> {
 interface StatusCount { DeploymentStatus getStatus(); long getTotal(); }
 List<PatchDeployment> findTop200ByOrderByCreatedAtDesc();
 List<PatchDeployment> findByTaskIdOrderByCreatedAtDesc(Long taskId);
 long countByStatus(DeploymentStatus status);
 @Query("select d.status as status, count(d.id) as total from PatchDeployment d group by d.status")
 List<StatusCount> countByStatusGrouped();
}
