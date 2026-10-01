package com.gazellio.platform.repository;
import com.gazellio.platform.model.Asset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.*;
import com.gazellio.platform.model.Enums.EnvironmentType;
public interface AssetRepository extends JpaRepository<Asset, Long> {
 Optional<Asset> findByAssetCode(String assetCode); List<Asset> findByActiveTrueOrderByNameAsc();
 Optional<Asset> findByCmdbItemId(String cmdbItemId); List<Asset> findBySourceSystem(String sourceSystem);
 List<Asset> findByActiveTrueOrderByIpAddressAsc(); long countByActiveTrue();
 List<Asset> findByBusinessServiceAndEnvironment(String businessService, EnvironmentType environment);
 @Query("select a from Asset a where a.active=true and (lower(a.assetCode) like :q or lower(a.name) like :q or lower(a.ipAddress) like :q or lower(a.businessService) like :q or lower(a.installedProducts) like :q) order by a.name")
 List<Asset> search(@Param("q") String q, Pageable pageable);
}
