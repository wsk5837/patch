package com.gazellio.platform.repository;

import com.gazellio.platform.model.DeploymentTarget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface DeploymentTargetRepository extends JpaRepository<DeploymentTarget, Long> {
    List<DeploymentTarget> findByDeploymentIdOrderByAssetIdAsc(Long deploymentId);
    List<DeploymentTarget> findByDeploymentIdInOrderByDeploymentIdAscAssetIdAsc(Collection<Long> deploymentIds);
}
