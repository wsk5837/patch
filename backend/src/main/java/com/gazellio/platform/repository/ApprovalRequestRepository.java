package com.gazellio.platform.repository;
import com.gazellio.platform.model.ApprovalRequest;
import com.gazellio.platform.model.Enums.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> { List<ApprovalRequest> findTop200ByOrderBySubmittedAtDesc(); long countByStatus(ApprovalStatus status); }
