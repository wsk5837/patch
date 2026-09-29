package com.gazellio.platform.repository;
import com.gazellio.platform.model.ScanJob;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ScanJobRepository extends JpaRepository<ScanJob, Long> { List<ScanJob> findTop100ByOrderByCreatedAtDesc(); }
