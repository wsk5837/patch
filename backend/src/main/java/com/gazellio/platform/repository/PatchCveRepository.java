package com.gazellio.platform.repository;
import com.gazellio.platform.model.PatchCve;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface PatchCveRepository extends JpaRepository<PatchCve, Long> { List<PatchCve> findByCveId(String cveId); List<PatchCve> findByPatchId(Long patchId); boolean existsByPatchIdAndCveId(Long patchId,String cveId); }
