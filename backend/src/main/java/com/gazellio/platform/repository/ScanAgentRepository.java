package com.gazellio.platform.repository;
import com.gazellio.platform.model.ScanAgent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ScanAgentRepository extends JpaRepository<ScanAgent, Long> { Optional<ScanAgent> findByAgentKey(String agentKey); List<ScanAgent> findAllByOrderByLastHeartbeatAtDesc(); }
