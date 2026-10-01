package com.gazellio.platform.repository;
import com.gazellio.platform.model.OrchestrationTemplateStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
public interface OrchestrationTemplateStepRepository extends JpaRepository<OrchestrationTemplateStep, Long> { List<OrchestrationTemplateStep> findByTemplateIdOrderByStepOrderAsc(Long templateId); List<OrchestrationTemplateStep> findByTemplateIdInOrderByTemplateIdAscStepOrderAsc(Collection<Long> templateIds); @Transactional void deleteByTemplateId(Long templateId); }
