package com.gazellio.platform.repository;
import com.gazellio.platform.model.RiskOverrideRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface RiskOverrideRequestRepository extends JpaRepository<RiskOverrideRequest,Long>{
    List<RiskOverrideRequest> findTop200ByOrderBySubmittedAtDesc();
    Optional<RiskOverrideRequest> findFirstByFindingIdAndStatusOrderBySubmittedAtDesc(Long findingId,String status);
    long countByStatus(String status);
}
