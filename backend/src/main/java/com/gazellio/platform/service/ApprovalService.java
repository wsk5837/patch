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

    public List<ApprovalView> list(){return approvals.findTop200ByOrderBySubmittedAtDesc().stream().map(view::approval).toList();}
    public ApprovalView get(Long id){return view.approval(require(id));}

    @Transactional
    public ApprovalRequest createForTask(RemediationTask task,ChangeType type,String reason,String rollback){
        if(task.getApprovalId()!=null){ApprovalRequest old=approvals.findById(task.getApprovalId()).orElse(null);if(old!=null&&old.getStatus()==ApprovalStatus.PENDING)return old;}
        ApprovalRequest a=approvals.save(ApprovalRequest.builder().approvalNo("APR-"+System.currentTimeMillis()).taskId(task.getId()).changeType(type).status(ApprovalStatus.PENDING).currentStep(1).requestedById(currentUser.current()==null?null:currentUser.current().getId()).requestedByName(currentUser.name()).reason(reason).rollbackPlan(rollback).build());
        List<String[]> flow=switch(type){
            case EMERGENCY -> List.<String[]>of(new String[]{"紧急变更审批人","Emergency Change Approver","发布审批人"});
            case MAJOR -> List.<String[]>of(new String[]{"运维负责人","Operations Lead","曾卫平"},new String[]{"安全负责人","Security Lead","王卫嘉"},new String[]{"重大变更审批人","Major Change Approver","发布审批人"});
            case NORMAL -> List.<String[]>of(new String[]{"运维负责人","Operations Lead","曾卫平"},new String[]{"发布审批人","Release Approver","发布审批人"});
            case STANDARD -> List.<String[]>of(new String[]{"标准变更授权","Standard Change Authorization","发布审批人"});
        };
        int i=1; for(String[] f:flow){steps.save(ApprovalStep.builder().approvalId(a.getId()).stepOrder(i).roleNameZh(f[0]).roleNameEn(f[1]).approverName(f[2]).status(i==1?ApprovalStepStatus.PENDING:ApprovalStepStatus.WAITING).build());i++;}
        task.setApprovalId(a.getId());task.setChangeType(type);task.setStage(TaskStage.RELEASE_APPROVAL);task.setStatus(TaskStatus.IN_PROGRESS);task.setUpdatedAt(Instant.now());tasks.save(task);
        audit.log("APPROVAL",a.getId(),"SUBMIT","提交生产发布审批 "+a.getApprovalNo(),"Submitted production release approval "+a.getApprovalNo(),currentUser.name()); return a;
    }

    @Transactional
    public ApprovalView approve(Long id,ApprovalActionRequest req){
        ApprovalRequest a=require(id); if(a.getStatus()!=ApprovalStatus.PENDING)throw new ResponseStatusException(HttpStatus.CONFLICT,"Approval is not pending");
        List<ApprovalStep> all=steps.findByApprovalIdOrderByStepOrderAsc(id); ApprovalStep current=all.stream().filter(s->s.getStatus()==ApprovalStepStatus.PENDING).findFirst().orElseThrow();
        current.setStatus(ApprovalStepStatus.APPROVED);current.setComment(req==null?null:req.comment());current.setActedAt(Instant.now());current.setApproverName(currentUser.name());steps.save(current);
        ApprovalStep next=all.stream().filter(s->s.getStepOrder()>current.getStepOrder()&&s.getStatus()==ApprovalStepStatus.WAITING).findFirst().orElse(null);
        if(next!=null){next.setStatus(ApprovalStepStatus.PENDING);steps.save(next);a.setCurrentStep(next.getStepOrder());approvals.save(a);}
        else{
            a.setStatus(ApprovalStatus.APPROVED);approvals.save(a); RemediationTask task=tasks.findById(a.getTaskId()).orElseThrow(); task.setStage(TaskStage.PREPROD_PATCH);task.setStatus(TaskStatus.IN_PROGRESS);task.setUpdatedAt(Instant.now());tasks.save(task);
            try{orchestration.startPatchRun(task,"PREPROD","Ring 0 · Pre-production");a.setStatus(ApprovalStatus.IMPLEMENTING);approvals.save(a);}
            catch(Exception ex){task.setStatus(TaskStatus.BLOCKED);tasks.save(task);audit.log("TASK",task.getId(),"BLOCKED","审批通过，但未找到预生产资产映射","Approval passed, but no pre-production asset mapping was found","Gazellio");}
        }
        audit.log("APPROVAL",id,"APPROVE","审批节点已通过","Approval step approved",currentUser.name()); return view.approval(a);
    }

    @Transactional public ApprovalView reject(Long id,ApprovalActionRequest req){
        ApprovalRequest a=require(id); if(a.getStatus()!=ApprovalStatus.PENDING)throw new ResponseStatusException(HttpStatus.CONFLICT); ApprovalStep current=steps.findByApprovalIdOrderByStepOrderAsc(id).stream().filter(s->s.getStatus()==ApprovalStepStatus.PENDING).findFirst().orElseThrow(); current.setStatus(ApprovalStepStatus.REJECTED);current.setComment(req==null?null:req.comment());current.setActedAt(Instant.now());current.setApproverName(currentUser.name());steps.save(current);a.setStatus(ApprovalStatus.REJECTED);a.setCompletedAt(Instant.now());approvals.save(a);RemediationTask t=tasks.findById(a.getTaskId()).orElseThrow();t.setStatus(TaskStatus.BLOCKED);t.setUpdatedAt(Instant.now());tasks.save(t);audit.log("APPROVAL",id,"REJECT","生产发布审批已驳回","Production release approval rejected",currentUser.name());return view.approval(a);
    }
    private ApprovalRequest require(Long id){return approvals.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));}
}
