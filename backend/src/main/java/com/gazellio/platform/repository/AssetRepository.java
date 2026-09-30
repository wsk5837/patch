package com.gazellio.platform.repository;
import com.gazellio.platform.model.Asset;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
import com.gazellio.platform.model.Enums.EnvironmentType;
public interface AssetRepository extends JpaRepository<Asset, Long> { Optional<Asset> findByAssetCode(String assetCode); List<Asset> findByActiveTrueOrderByNameAsc(); List<Asset> findByActiveTrueOrderByIpAddressAsc(); long countByActiveTrue(); List<Asset> findByBusinessServiceAndEnvironment(String businessService, EnvironmentType environment); }
