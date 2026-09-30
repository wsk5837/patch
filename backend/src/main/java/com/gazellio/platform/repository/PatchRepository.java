package com.gazellio.platform.repository;
import com.gazellio.platform.model.Patch;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface PatchRepository extends JpaRepository<Patch, Long> {
 Optional<Patch> findByPatchId(String patchId);
 List<Patch> findAllByOrderByPublishedDateDesc();
 List<Patch> findByPatchIdContainingIgnoreCaseOrTitleZhContainingIgnoreCaseOrTitleEnContainingIgnoreCase(String patchId,String titleZh,String titleEn);
}
