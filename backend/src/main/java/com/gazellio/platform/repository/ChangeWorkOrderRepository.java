package com.gazellio.platform.repository;

import com.gazellio.platform.model.ChangeWorkOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChangeWorkOrderRepository extends JpaRepository<ChangeWorkOrder, Long> {
    Optional<ChangeWorkOrder> findByIncidentId(Long incidentId);
    Optional<ChangeWorkOrder> findByRemediationTaskId(Long remediationTaskId);
    List<ChangeWorkOrder> findTop200ByOrderByUpdatedAtDesc();
}
