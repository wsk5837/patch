package com.gazellio.platform.repository;
import com.gazellio.platform.model.OrchestrationTemplateStep;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface OrchestrationTemplateStepRepository extends JpaRepository<OrchestrationTemplateStep, Long> { List<OrchestrationTemplateStep> findByTemplateIdOrderByStepOrderAsc(Long templateId); }
