package com.gazellio.platform.repository;
import com.gazellio.platform.model.ControlAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ControlAlertRepository extends JpaRepository<ControlAlert,Long>{
    Optional<ControlAlert> findByAlertKey(String alertKey);
    List<ControlAlert> findTop200ByStatusOrderByCreatedAtDesc(String status);
    long countByStatus(String status);
}
