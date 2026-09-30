package com.gazellio.platform.repository;
import com.gazellio.platform.model.ScanJob;
import com.gazellio.platform.model.Enums.ScanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ScanJobRepository extends JpaRepository<ScanJob, Long> { Optional<ScanJob> findByJobNo(String jobNo); List<ScanJob> findTop100ByOrderByCreatedAtDesc(); List<ScanJob> findTop5ByOrderByCreatedAtDesc(); List<ScanJob> findByStatusIn(Collection<ScanStatus> statuses); long countByStatusIn(Collection<ScanStatus> statuses); }
