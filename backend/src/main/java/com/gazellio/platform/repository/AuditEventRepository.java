package com.gazellio.platform.repository;
import com.gazellio.platform.model.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
import java.time.Instant;
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
 List<AuditEvent> findTop500ByOrderByCreatedAtDesc();
 List<AuditEvent> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType,String entityId);
 List<AuditEvent> findAllByOrderByIdAsc();
 Optional<AuditEvent> findTopByOrderByIdDesc();
 long deleteByCreatedAtBefore(Instant threshold);
}
