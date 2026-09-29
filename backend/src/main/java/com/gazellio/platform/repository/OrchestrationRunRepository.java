package com.gazellio.platform.repository;
import com.gazellio.platform.model.OrchestrationRun;
import com.gazellio.platform.model.Enums.RunStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface OrchestrationRunRepository extends JpaRepository<OrchestrationRun, Long> { List<OrchestrationRun> findTop200ByOrderByCreatedAtDesc(); List<OrchestrationRun> findTop5ByOrderByCreatedAtDesc(); List<OrchestrationRun> findByStatusIn(Collection<RunStatus> statuses); long countByStatusIn(Collection<RunStatus> statuses); }
