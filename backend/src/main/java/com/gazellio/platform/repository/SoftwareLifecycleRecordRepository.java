package com.gazellio.platform.repository;
import com.gazellio.platform.model.SoftwareLifecycleRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface SoftwareLifecycleRecordRepository extends JpaRepository<SoftwareLifecycleRecord,Long>{
    List<SoftwareLifecycleRecord> findAllByOrderByEndOfLifeDateAscProductNameAsc();
    long countByStatusIn(List<String> statuses);
}
