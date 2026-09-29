package com.gazellio.platform.repository;
import com.gazellio.platform.model.PatchDeployment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface PatchDeploymentRepository extends JpaRepository<PatchDeployment, Long> { List<PatchDeployment> findTop200ByOrderByCreatedAtDesc(); List<PatchDeployment> findByTaskIdOrderByCreatedAtDesc(Long taskId); }
