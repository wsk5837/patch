package com.gazellio.platform.repository;
import com.gazellio.platform.model.Patch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.*;
public interface PatchRepository extends JpaRepository<Patch, Long> {
 Optional<Patch> findByPatchId(String patchId);
 List<Patch> findAllByOrderByPublishedDateDesc();
 @Query("select p from Patch p where upper(coalesce(p.status,'')) <> 'RETIRED' order by p.publishedDate desc")
 List<Patch> findActiveCatalog();
 List<Patch> findByPatchIdContainingIgnoreCaseOrTitleZhContainingIgnoreCaseOrTitleEnContainingIgnoreCase(String patchId,String titleZh,String titleEn);
 @Query("select p from Patch p where upper(coalesce(p.status,'')) <> 'RETIRED' and (lower(p.patchId) like :q or lower(p.titleZh) like :q or lower(p.titleEn) like :q or lower(p.product) like :q) order by p.updatedAt desc")
 List<Patch> search(@Param("q") String q, Pageable pageable);
}
