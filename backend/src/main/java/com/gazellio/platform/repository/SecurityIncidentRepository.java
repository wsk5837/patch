package com.gazellio.platform.repository;

import com.gazellio.platform.model.SecurityIncident;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SecurityIncidentRepository extends JpaRepository<SecurityIncident, Long> {
    Optional<SecurityIncident> findByFindingId(Long findingId);
    List<SecurityIncident> findTop200ByOrderByUpdatedAtDesc();
}
