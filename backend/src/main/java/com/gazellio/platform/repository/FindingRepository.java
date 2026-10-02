package com.gazellio.platform.repository;
import com.gazellio.platform.model.Finding;
import com.gazellio.platform.model.Enums.FindingStatus;
import com.gazellio.platform.model.Enums.EnvironmentType;
import com.gazellio.platform.model.Enums.Severity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.*;
public interface FindingRepository extends JpaRepository<Finding, Long>, JpaSpecificationExecutor<Finding> {
 interface AssetOpenCount { Long getAssetId(); long getTotal(); }
 interface CveAffectedCount { String getCveId(); long getTotal(); }
 interface SeverityCount { Severity getSeverity(); long getTotal(); }
 interface EnvironmentCount { EnvironmentType getEnvironment(); long getTotal(); }

 Optional<Finding> findByAssetIdAndCveId(Long assetId, String cveId);
    List<Finding> findTop200ByOrderByRiskScoreDescLastSeenAtDesc();
 @Query("select f from Finding f, Asset a where f.assetId=a.id and a.active=true order by f.riskScore desc, f.lastSeenAt desc")
 List<Finding> findTop200Active(Pageable pageable);
    List<Finding> findTop200ByCveIdInOrderByRiskScoreDescLastSeenAtDesc(Collection<String> cveIds);
 List<Finding> findTop6ByStatusNotInOrderByRiskScoreDescLastSeenAtDesc(Collection<FindingStatus> statuses);
 List<Finding> findTop200ByStatusNotInOrderByRiskScoreDescLastSeenAtDesc(Collection<FindingStatus> statuses);
 List<Finding> findTop200ByStatusOrderByRiskScoreDescLastSeenAtDesc(FindingStatus status);
 long countByStatus(FindingStatus status);
 long countByStatusIn(Collection<FindingStatus> statuses);
 long countByStatusNotIn(Collection<FindingStatus> statuses);
 List<Finding> findByScanJobId(Long scanJobId);
 List<Finding> findByAssetIdOrderByRiskScoreDescLastSeenAtDesc(Long assetId);

 @Query("select f.assetId as assetId, count(f.id) as total from Finding f, Asset a where f.assetId=a.id and a.active=true and f.status not in :closed group by f.assetId")
 List<AssetOpenCount> countOpenByAsset(@Param("closed") Collection<FindingStatus> closed);

 @Query("select f.cveId as cveId, count(distinct f.assetId) as total from Finding f, Asset a where f.assetId=a.id and a.active=true and f.status not in :closed group by f.cveId")
 List<CveAffectedCount> countAffectedByCve(@Param("closed") Collection<FindingStatus> closed);

 @Query("select v.severity as severity, count(f.id) as total from Finding f, VulnerabilityDefinition v, Asset a where f.cveId=v.cveId and f.assetId=a.id and a.active=true and f.status not in :closed group by v.severity")
 List<SeverityCount> countOpenBySeverity(@Param("closed") Collection<FindingStatus> closed);

 @Query("select a.environment as environment, count(f.id) as total from Finding f, Asset a where f.assetId=a.id and a.active=true and f.status not in :closed group by a.environment")
 List<EnvironmentCount> countOpenByEnvironment(@Param("closed") Collection<FindingStatus> closed);

 @Query("select count(distinct f.assetId) from Finding f, VulnerabilityDefinition v, Asset a where f.cveId=v.cveId and f.assetId=a.id and a.active=true and f.status not in :closed and v.patchAvailable=true and v.severity in :severities")
 long countNonCompliantAssets(@Param("closed") Collection<FindingStatus> closed,
                              @Param("severities") Collection<Severity> severities);

 @Query("select count(f.id) from Finding f, Asset a where f.assetId=a.id and a.active=true and f.status not in :closed")
 long countActiveOpen(@Param("closed") Collection<FindingStatus> closed);

 @Query("select count(f.id) from Finding f, Asset a where f.assetId=a.id and a.active=true and f.status=:status")
 long countActiveByStatus(@Param("status") FindingStatus status);

 @Query("select f from Finding f, Asset a where f.assetId=a.id and a.active=true and f.status not in :closed order by f.riskScore desc, f.lastSeenAt desc")
 List<Finding> findTopActiveOpen(@Param("closed") Collection<FindingStatus> closed, Pageable pageable);

 @Query("select f from Finding f, Asset a where f.assetId=a.id and a.active=true")
 List<Finding> findAllActive();
}
