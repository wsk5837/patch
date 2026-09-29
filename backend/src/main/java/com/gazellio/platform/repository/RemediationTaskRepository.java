package com.gazellio.platform.repository;
import com.gazellio.platform.model.RemediationTask;
import com.gazellio.platform.model.Enums.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface RemediationTaskRepository extends JpaRepository<RemediationTask, Long> { Optional<RemediationTask> findByFindingId(Long findingId); List<RemediationTask> findTop200ByOrderByUpdatedAtDesc(); long countByStatusIn(Collection<TaskStatus> statuses); }
