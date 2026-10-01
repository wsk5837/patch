package com.gazellio.platform.repository;
import com.gazellio.platform.model.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> { List<AuditEvent> findTop500ByOrderByCreatedAtDesc(); List<AuditEvent> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType,String entityId); }
