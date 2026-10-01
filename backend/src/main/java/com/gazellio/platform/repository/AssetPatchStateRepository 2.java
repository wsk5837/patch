package com.gazellio.platform.repository;
import com.gazellio.platform.model.AssetPatchState;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface AssetPatchStateRepository extends JpaRepository<AssetPatchState, Long> { Optional<AssetPatchState> findByAssetIdAndPatchId(Long assetId,Long patchId); List<AssetPatchState> findByAssetId(Long assetId); }
