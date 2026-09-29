package com.gazellio.platform.repository;
import com.gazellio.platform.model.Finding;
import com.gazellio.platform.model.Enums.FindingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface FindingRepository extends JpaRepository<Finding, Long> {
 Optional<Finding> findByAssetIdAndCveId(Long assetId, String cveId);
 List<Finding> findTop200ByOrderByRiskScoreDescLastSeenAtDesc();
 long countByStatus(FindingStatus status);
 long countByStatusIn(Collection<FindingStatus> statuses);
 List<Finding> findByScanJobId(Long scanJobId);
}
