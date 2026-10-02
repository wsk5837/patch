package com.gazellio.platform.repository;
import com.gazellio.platform.model.RegulatoryIncidentCase;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface RegulatoryIncidentCaseRepository extends JpaRepository<RegulatoryIncidentCase,Long>{
    List<RegulatoryIncidentCase> findTop200ByOrderByCreatedAtDesc();
    Optional<RegulatoryIncidentCase> findBySecurityIncidentId(Long securityIncidentId);
    long countByStatusNot(String status);
}
