package com.gazellio.platform.repository;
import com.gazellio.platform.model.OrchestrationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface OrchestrationTemplateRepository extends JpaRepository<OrchestrationTemplate, Long> { Optional<OrchestrationTemplate> findByCode(String code); List<OrchestrationTemplate> findByEnabledTrueOrderByNameZhAsc(); }
