package com.gazellio.platform.repository;
import com.gazellio.platform.model.OrchestrationRunStep;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface OrchestrationRunStepRepository extends JpaRepository<OrchestrationRunStep, Long> { List<OrchestrationRunStep> findByRunIdOrderByStepOrderAsc(Long runId); List<OrchestrationRunStep> findByRunIdInOrderByRunIdAscStepOrderAsc(Collection<Long> runIds); }
