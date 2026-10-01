package com.gazellio.platform.repository;
import com.gazellio.platform.model.ApprovalRequest;
import com.gazellio.platform.model.Enums.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.*;
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {
 List<ApprovalRequest> findTop200ByOrderBySubmittedAtDesc();
 long countByStatus(ApprovalStatus status);
 @Query("select p from ApprovalRequest p, RemediationTask t, Asset a where p.taskId=t.id and t.assetId=a.id and a.active=true order by p.submittedAt desc")
 List<ApprovalRequest> findActive(Pageable pageable);
 @Query("select count(p.id) from ApprovalRequest p, RemediationTask t, Asset a where p.taskId=t.id and t.assetId=a.id and a.active=true and p.status=:status")
 long countActiveByStatus(@Param("status") ApprovalStatus status);
}
