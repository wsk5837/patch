package com.gazellio.platform.repository;
import com.gazellio.platform.model.PatchCve;
import com.gazellio.platform.model.Enums.FindingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface PatchCveRepository extends JpaRepository<PatchCve, Long> {
 interface PatchAffectedCount { Long getPatchId(); long getTotal(); }
 List<PatchCve> findByCveId(String cveId);
 List<PatchCve> findByCveIdIn(Collection<String> cveIds);
 List<PatchCve> findByPatchId(Long patchId);
 List<PatchCve> findByPatchIdIn(Collection<Long> patchIds);
 boolean existsByPatchIdAndCveId(Long patchId,String cveId);

 @Query("select pc.patchId as patchId, count(distinct f.assetId) as total from PatchCve pc, Finding f where pc.cveId=f.cveId and pc.patchId in :patchIds and f.status not in :closed group by pc.patchId")
 List<PatchAffectedCount> countAffectedAssets(@Param("patchIds") Collection<Long> patchIds,
                                              @Param("closed") Collection<FindingStatus> closed);
}
