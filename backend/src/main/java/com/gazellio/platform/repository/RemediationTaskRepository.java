package com.gazellio.platform.repository;
import com.gazellio.platform.model.RemediationTask;
import com.gazellio.platform.model.Enums.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.*;
public interface RemediationTaskRepository extends JpaRepository<RemediationTask, Long> {
 Optional<RemediationTask> findByFindingId(Long findingId); List<RemediationTask> findTop200ByOrderByUpdatedAtDesc();
 long countByStatusIn(Collection<TaskStatus> statuses);
 @Query("select t from RemediationTask t where lower(t.taskNo) like :q or lower(coalesce(t.ownerName,'')) like :q order by t.updatedAt desc")
 List<RemediationTask> search(@Param("q") String q, Pageable pageable);
}
