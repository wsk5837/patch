package com.gazellio.platform.repository;

import com.gazellio.platform.model.SecurityIncident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface SecurityIncidentRepository extends JpaRepository<SecurityIncident, Long> {
    Optional<SecurityIncident> findByFindingId(Long findingId);
    List<SecurityIncident> findTop200ByOrderByUpdatedAtDesc();
    @Query("select i from SecurityIncident i, Asset a where i.assetId=a.id and a.active=true order by i.updatedAt desc")
    List<SecurityIncident> findActive(Pageable pageable);
}
