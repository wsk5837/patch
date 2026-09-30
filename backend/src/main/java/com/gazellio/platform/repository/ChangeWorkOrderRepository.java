package com.gazellio.platform.repository;

import com.gazellio.platform.model.ChangeWorkOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ChangeWorkOrderRepository extends JpaRepository<ChangeWorkOrder, Long> {
    Optional<ChangeWorkOrder> findByIncidentId(Long incidentId);
    Optional<ChangeWorkOrder> findByRemediationTaskId(Long remediationTaskId);
    List<ChangeWorkOrder> findTop200ByOrderByUpdatedAtDesc();
    @Query("select c from ChangeWorkOrder c, SecurityIncident i, Asset a where c.incidentId=i.id and i.assetId=a.id and a.active=true order by c.updatedAt desc")
    List<ChangeWorkOrder> findActive(Pageable pageable);
}
