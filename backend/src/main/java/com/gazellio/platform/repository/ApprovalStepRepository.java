package com.gazellio.platform.repository;
import com.gazellio.platform.model.ApprovalStep;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ApprovalStepRepository extends JpaRepository<ApprovalStep, Long> { List<ApprovalStep> findByApprovalIdOrderByStepOrderAsc(Long approvalId); }
