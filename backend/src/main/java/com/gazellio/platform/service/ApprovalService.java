package com.gazellio.platform.service;

import com.gazellio.platform.dto.ApiDtos.*;
import com.gazellio.platform.model.*;
import com.gazellio.platform.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import static com.gazellio.platform.model.Enums.*;

@Service @RequiredArgsConstructor
public class ApprovalService {
    private final ApprovalRequestRepository approvals;
    private final ApprovalStepRepository steps;
    private final RemediationTaskRepository tasks;
    private final ViewService view;
    private final CurrentUserService currentUser;
    private final AuditService audit;
    private final OrchestrationService orchestration;
    private final ChangeWorkOrderRepository changes;
    private final SecurityIncidentRepository incidents;
    private final UserAccountRepository users;

    public List<ApprovalView> list(){return view.approvalViews(approvals.findTop200ByOrderBySubmittedAtDesc());}
    public ApprovalView get(Long id){return view.approval(require(id));}

    @Transactional
    public ApprovalRequest createForTask(RemediationTask task,ChangeType type,String reason,String rollback){
        if(task.getApprovalId()!=null){ApprovalRequest old=approvals.findById(task.getApprovalId()).orElse(null);if(old!=null&&old.getStatus()==ApprovalStatus.PENDING)return old;}
        Long requesterId=currentUser.current()==null?null:currentUser.current().getId();
        ApprovalRequest a=approvals.save(ApprovalRequest.builder().approvalNo("APR-PENDING-"+UUID.randomUUID()).taskId(task.getId()).changeType(type).status(ApprovalStatus.PENDING).currentStep(1).requestedById(requesterId).requestedByName(currentUser.name()).reason(reason).rollbackPlan(rollback).build());
        a.setApprovalNo("APR-"+String.format("%06d",a.getId()));approvals.save(a);
        List<String[]> flow=switch(type){
            case EMERGENCY -> List.<String[]>of(new String[]{"紧急变更审批人","Emergency Change Approver","approver"});
            case MAJOR -> List.<String[]>of(new String[]{"运维负责人","Operations Lead","ops"},new String[]{"安全负责人","Security Lead","security"},new String[]{"重大变更审批人","Major Change Approver","approver"});
            case NORMAL -> List.<String[]>of(new String[]{"运维负责人","Operations Lead","ops"},new String[]{"发布审批人","Release Approver","approver"});
            case STANDARD -> List.<String[]>of(new String[]{"标准变更授权","Standard Change Authorization","approver"});
        };
        Set<Long> routed=new HashSet<>();int i=1;
        for(String[] f:flow){
            UserAccount approver=resolveApprover(f[2],requesterId,routed);
            routed.add(approver.getId());
            steps.save(ApprovalStep.builder().approvalId(a.getId()).stepOrder(i).roleNameZh(f[0]).roleNameEn(f[1]).approverId(approver.getId()).approverName(approver.getDisplayName()).status(i==1?ApprovalStepStatus.PENDING:ApprovalStepStatus.WAITING).build());i++;
        }
        task.setApprovalId(a.getId());task.setChangeType(type);task.setStage(TaskStage.RELEASE_APPROVAL);task.setStatus(TaskStatus.IN_PROGRESS);task.setUpdatedAt(Instant.now());tasks.save(task);
        audit.log("APPROVAL",a.getId(),"SUBMIT","提交生产发布审批 "+a.getApprovalNo(),"Submitted production release approval "+a.getApprovalNo(),currentUser.name()); return a;
    }

    @Transactional
    public ApprovalView approve(Long id,ApprovalActionRequest req){
        ApprovalRequest a=require(id); if(a.getStatus()!=ApprovalStatus.PENDING)throw new ResponseStatusException(HttpStatus.CONFLICT,"Approval is not pending");
        List<ApprovalStep> all=steps.findByApprovalIdOrderByStepOrderAsc(id); ApprovalStep current=all.stream().filter(s->s.getStatus()==ApprovalStepStatus.PENDING).findFirst().orElseThrow();
        validateDecision(a,current,req);
        current.setStatus(ApprovalStepStatus.APPROVED);current.setComment(req.comment().trim());current.setActedAt(Instant.now());current.setApproverName(currentUser.name());steps.save(current);
        ApprovalStep next=all.stream().filter(s->s.getStepOrder()>current.getStepOrder()&&s.getStatus()==ApprovalStepStatus.WAITING).findFirst().orElse(null);
        if(next!=null){next.setStatus(ApprovalStepStatus.PENDING);steps.save(next);a.setCurrentStep(next.getStepOrder());approvals.save(a);}
        else{
            a.setStatus(ApprovalStatus.APPROVED);approvals.save(a); RemediationTask task=tasks.findById(a.getTaskId()).orElseThrow(); task.setStage(TaskStage.PREPROD_PATCH);task.setStatus(TaskStatus.IN_PROGRESS);task.setUpdatedAt(Instant.now());tasks.save(task);
            if(a.getChangeOrderId()!=null) changes.findById(a.getChangeOrderId()).ifPresent(c->{c.setStatus(ChangeStatus.APPROVED);c.setUpdatedAt(Instant.now());changes.save(c);});
            try{orchestration.startPatchRun(task,"PREPROD","Ring 0 · Pre-production");a.setStatus(ApprovalStatus.IMPLEMENTING);approvals.save(a);if(a.getChangeOrderId()!=null)changes.findById(a.getChangeOrderId()).ifPresent(c->{c.setStatus(ChangeStatus.IMPLEMENTING);c.setUpdatedAt(Instant.now());changes.save(c);});if(task.getSecurityIncidentId()!=null)incidents.findById(task.getSecurityIncidentId()).ifPresent(i->{i.setStatus(IncidentStatus.IMPLEMENTING);i.setUpdatedAt(Instant.now());incidents.save(i);});}
            catch(Exception ex){task.setStatus(TaskStatus.BLOCKED);tasks.save(task);audit.log("TASK",task.getId(),"BLOCKED","审批通过，但未找到预生产资产映射","Approval passed, but no pre-production asset mapping was found","Gazellio");}
        }
        audit.log("APPROVAL",id,"APPROVE","审批节点已通过，意见："+req.comment().trim(),"Approval step approved. Comment: "+req.comment().trim(),currentUser.name()); return view.approval(a);
    }

    @Transactional public ApprovalView reject(Long id,ApprovalActionRequest req){
        ApprovalRequest a=require(id); if(a.getStatus()!=ApprovalStatus.PENDING)throw new ResponseStatusException(HttpStatus.CONFLICT); ApprovalStep current=steps.findByApprovalIdOrderByStepOrderAsc(id).stream().filter(s->s.getStatus()==ApprovalStepStatus.PENDING).findFirst().orElseThrow();validateDecision(a,current,req);current.setStatus(ApprovalStepStatus.REJECTED);current.setComment(req.comment().trim());current.setActedAt(Instant.now());current.setApproverName(currentUser.name());steps.save(current);a.setStatus(ApprovalStatus.REJECTED);a.setCompletedAt(Instant.now());approvals.save(a);RemediationTask t=tasks.findById(a.getTaskId()).orElseThrow();t.setStatus(TaskStatus.BLOCKED);t.setUpdatedAt(Instant.now());tasks.save(t);if(a.getChangeOrderId()!=null)changes.findById(a.getChangeOrderId()).ifPresent(c->{c.setStatus(ChangeStatus.REJECTED);c.setUpdatedAt(Instant.now());changes.save(c);});if(t.getSecurityIncidentId()!=null)incidents.findById(t.getSecurityIncidentId()).ifPresent(i->{i.setStatus(IncidentStatus.PENDING_CHANGE);i.setUpdatedAt(Instant.now());incidents.save(i);});audit.log("APPROVAL",id,"REJECT","生产发布审批已驳回，意见："+req.comment().trim(),"Production release approval rejected. Comment: "+req.comment().trim(),currentUser.name());return view.approval(a);
    }

    private void validateDecision(ApprovalRequest approval,ApprovalStep step,ApprovalActionRequest req){
        if(req==null||req.comment()==null||req.comment().isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Approval comment is required");
        UserAccount actor=currentUser.current();
        if(actor==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if((approval.getRequestedById()!=null&&approval.getRequestedById().equals(actor.getId()))
                ||(approval.getRequestedById()==null&&actor.getDisplayName().equals(approval.getRequestedByName())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Requester cannot approve the same change");
        Long assigned=step.getApproverId();
        if(assigned==null&&step.getApproverName()!=null){assigned=users.findByDisplayName(step.getApproverName()).map(UserAccount::getId).orElse(null);if(assigned!=null){step.setApproverId(assigned);steps.save(step);}}
        if(assigned==null||!assigned.equals(actor.getId()))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Only the assigned approver can act on this step");
    }
    private UserAccount resolveApprover(String preferred,Long requesterId,Set<Long> routed){
        for(String username:List.of(preferred,"approver","security","ops","appowner")){
            UserAccount candidate=users.findByUsername(username).orElse(null);
            if(candidate!=null&&!Objects.equals(candidate.getId(),requesterId)&&!routed.contains(candidate.getId()))return candidate;
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT,"No independent approver is available for this workflow");
    }
    private ApprovalRequest require(Long id){return approvals.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));}
}
