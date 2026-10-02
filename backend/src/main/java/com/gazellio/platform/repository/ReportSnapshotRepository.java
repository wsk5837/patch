package com.gazellio.platform.repository;

import com.gazellio.platform.model.ReportSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReportSnapshotRepository extends JpaRepository<ReportSnapshot,Long> {
    List<ReportSnapshot> findTop50ByOrderByCreatedAtDesc();
}
