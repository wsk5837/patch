package com.gazellio.platform.repository;
import com.gazellio.platform.model.Finding;
import com.gazellio.platform.model.Enums.FindingStatus;
import com.gazellio.platform.model.Enums.EnvironmentType;
import com.gazellio.platform.model.Enums.Severity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface FindingRepository extends JpaRepository<Finding, Long> {
 interface AssetOpenCount { Long getAssetId(); long getTotal(); }
 interface CveAffectedCount { String getCveId(); long getTotal(); }
 interface SeverityCount { Severity getSeverity(); long getTotal(); }
 interface EnvironmentCount { EnvironmentType getEnvironment(); long getTotal(); }

 Optional<Finding> findByAssetIdAndCveId(Long assetId, String cveId);
    List<Finding> findTop200ByOrderByRiskScoreDescLastSeenAtDesc();
    List<Finding> findTop200ByCveIdInOrderByRiskScoreDescLastSeenAtDesc(Collection<String> cveIds);
 List<Finding> findTop6ByStatusNotInOrderByRiskScoreDescLastSeenAtDesc(Collection<FindingStatus> statuses);
 List<Finding> findTop200ByStatusNotInOrderByRiskScoreDescLastSeenAtDesc(Collection<FindingStatus> statuses);
 List<Finding> findTop200ByStatusOrderByRiskScoreDescLastSeenAtDesc(FindingStatus status);
 long countByStatus(FindingStatus status);
 long countByStatusIn(Collection<FindingStatus> statuses);
 long countByStatusNotIn(Collection<FindingStatus> statuses);
 List<Finding> findByScanJobId(Long scanJobId);
 List<Finding> findByAssetIdOrderByRiskScoreDescLastSeenAtDesc(Long assetId);

 @Query("select f.assetId as assetId, count(f.id) as total from Finding f where f.status not in :closed group by f.assetId")
 List<AssetOpenCount> countOpenByAsset(@Param("closed") Collection<FindingStatus> closed);

 @Query("select f.cveId as cveId, count(distinct f.assetId) as total from Finding f where f.status not in :closed group by f.cveId")
 List<CveAffectedCount> countAffectedByCve(@Param("closed") Collection<FindingStatus> closed);

 @Query("select v.severity as severity, count(f.id) as total from Finding f, VulnerabilityDefinition v where f.cveId=v.cveId and f.status not in :closed group by v.severity")
 List<SeverityCount> countOpenBySeverity(@Param("closed") Collection<FindingStatus> closed);

 @Query("select a.environment as environment, count(f.id) as total from Finding f, Asset a where f.assetId=a.id and f.status not in :closed group by a.environment")
 List<EnvironmentCount> countOpenByEnvironment(@Param("closed") Collection<FindingStatus> closed);

 @Query("select count(distinct f.assetId) from Finding f, VulnerabilityDefinition v, Asset a where f.cveId=v.cveId and f.assetId=a.id and a.active=true and f.status not in :closed and v.patchAvailable=true and v.severity in :severities")
 long countNonCompliantAssets(@Param("closed") Collection<FindingStatus> closed,
                              @Param("severities") Collection<Severity> severities);
}
