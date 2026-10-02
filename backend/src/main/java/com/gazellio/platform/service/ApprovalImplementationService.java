package com.gazellio.platform.service;

import com.gazellio.platform.model.ApprovalRequest;
import com.gazellio.platform.model.RemediationTask;
import com.gazellio.platform.repository.ApprovalRequestRepository;
import com.gazellio.platform.repository.ChangeWorkOrderRepository;
import com.gazellio.platform.repository.RemediationTaskRepository;
import com.gazellio.platform.repository.SecurityIncidentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

import static com.gazellio.platform.model.Enums.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApprovalImplementationService {
    private final ApprovalRequestRepository approvals;
    private final RemediationTaskRepository tasks;
    private final ChangeWorkOrderRepository changes;
    private final SecurityIncidentRepository incidents;
    private final OrchestrationService orchestration;
    private final AuditService audit;
    private final PlatformTransactionManager transactionManager;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void startAfterApprovalCommit(ApprovalImplementationRequested event) {
        try {
            requiresNew().executeWithoutResult(status -> {
                RemediationTask task = tasks.findById(event.taskId()).orElseThrow();
                orchestration.startPatchRun(task,"PREPROD","Ring 0 · Pre-production");
            });
            updateImplementationState(event,true,null);
        } catch (Exception error) {
            log.warn("Approval {} committed but pre-production automation could not start: {}",
                    event.approvalId(),error.getMessage());
            updateImplementationState(event,false,error.getMessage());
        }
    }

    private void updateImplementationState(ApprovalImplementationRequested event, boolean started, String reason) {
        requiresNew().executeWithoutResult(status -> {
            ApprovalRequest approval=approvals.findById(event.approvalId()).orElseThrow();
            RemediationTask task=tasks.findById(event.taskId()).orElseThrow();
            Instant now=Instant.now();
            if(started){
                approval.setStatus(ApprovalStatus.IMPLEMENTING);approvals.save(approval);
                if(approval.getChangeOrderId()!=null)changes.findById(approval.getChangeOrderId()).ifPresent(change->{change.setStatus(ChangeStatus.IMPLEMENTING);change.setUpdatedAt(now);changes.save(change);});
                if(task.getSecurityIncidentId()!=null)incidents.findById(task.getSecurityIncidentId()).ifPresent(incident->{incident.setStatus(IncidentStatus.IMPLEMENTING);incident.setUpdatedAt(now);incidents.save(incident);});
                return;
            }
            task.setStatus(TaskStatus.BLOCKED);task.setUpdatedAt(now);tasks.save(task);
            audit.log("TASK",task.getId(),"AUTOMATION_BLOCKED",
                    "审批已通过，预生产自动化启动失败",
                    "Approval passed, but pre-production automation could not start"+(reason==null?"":": "+reason),"ANOWX");
        });
    }

    private TransactionTemplate requiresNew(){
        TransactionTemplate template=new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }
}
