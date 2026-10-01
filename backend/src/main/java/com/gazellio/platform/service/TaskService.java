package com.gazellio.platform.service;

import com.gazellio.platform.dto.ApiDtos.*;
import com.gazellio.platform.model.*;
import com.gazellio.platform.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import static com.gazellio.platform.model.Enums.*;

@Service @RequiredArgsConstructor
public class TaskService {
    private final RemediationTaskRepository tasks;
    private final FindingRepository findings;
    private final SecurityIncidentRepository incidents;
    private final AssetRepository assets;
    private final PatchRepository patches;
    private final ApprovalRequestRepository approvals;
    private final ViewService view;
    private final OrchestrationService orchestration;
    private final ScanService scanService;
    private final AuditService audit;
    private final CurrentUserService currentUser;

    public List<TaskView> list(){return view.taskViews(tasks.findActive(PageRequest.of(0,200)));}
    public TaskView get(Long id){return view.task(require(id));}

    @Transactional
    public TaskView action(Long id,String action,TaskActionRequest req){
        RemediationTask t=require(id);
        switch(action.toLowerCase(Locale.ROOT)){
            case "start-test" -> { ensure(t,TaskStage.ASSIGNED); t.setStage(TaskStage.TEST_PATCH);t.setStatus(TaskStatus.IN_PROGRESS);tasks.save(t);orchestration.startPatchRun(t,"TEST","Ring 0 · Test"); }
            case "verify-test" -> verifyApplication(t,TaskStage.APP_VERIFY,TaskStage.TEST_RESCAN,"TEST",req);
            case "verify-preprod" -> verifyApplication(t,TaskStage.PREPROD_VERIFY,TaskStage.PREPROD_RESCAN,"PREPROD",req);
            case "verify-prod" -> verifyApplication(t,TaskStage.PROD_VERIFY,TaskStage.PROD_RESCAN,"PROD",req);
            case "start-auto-retest" -> startAutoRetest(t,req);
            case "submit-manual-retest" -> submitManualRetest(t,req);
            case "submit-approval" -> throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Create a production change from the linked security incident before approval");
            case "retry" -> retry(t);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unknown action");
        }
        t.setUpdatedAt(Instant.now());tasks.save(t);audit.log("TASK",t.getId(),action,"任务动作："+action,"Task action: "+action,currentUser.name());return view.task(t);
    }


    @Transactional
    public TaskView assign(Long id,TaskAssignRequest req){
        RemediationTask t=require(id);
        if(t.getStage()==TaskStage.CLOSED) throw new ResponseStatusException(HttpStatus.CONFLICT,"Closed task cannot be reassigned");
        t.setOwnerId(req.ownerId()); t.setOwnerName(req.ownerName()); t.setUpdatedAt(Instant.now()); tasks.save(t);
        findings.findById(t.getFindingId()).ifPresent(f->{f.setOwnerId(req.ownerId());f.setOwnerName(req.ownerName());findings.save(f);});
        incidents.findByFindingId(t.getFindingId()).ifPresent(i->{i.setOwnerId(req.ownerId());i.setOwnerName(req.ownerName());i.setUpdatedAt(Instant.now());incidents.save(i);});
        audit.log("TASK",t.getId(),"ASSIGN","任务分派给 "+req.ownerName(),"Task assigned to "+req.ownerName(),currentUser.name());
        return view.task(t);
    }

    private void verifyApplication(RemediationTask t,TaskStage expected,TaskStage rescanStage,String env,TaskActionRequest req){
        ensure(t,expected);
        if(req==null||req.result()==null||(!req.result().equalsIgnoreCase("PASS")&&!req.result().equalsIgnoreCase("FAIL")))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Application validation result must be PASS or FAIL");
        if(req.comment()==null||req.comment().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Application validation evidence is required");
        boolean pass=req.result().equalsIgnoreCase("PASS");
        if(!pass){
            t.setStage(env.equals("TEST")?TaskStage.TEST_PATCH:env.equals("PREPROD")?TaskStage.PREPROD_PATCH:TaskStage.PROD_PATCH);
            t.setStatus(TaskStatus.BLOCKED);tasks.save(t);
            audit.log("TASK",t.getId(),"APP_VALIDATION_FAILED",env+" 环境应用验证不通过："+req.comment(),
                    env+" application validation failed: "+req.comment(),currentUser.name());
            return;
        }
        t.setStage(rescanStage);t.setStatus(TaskStatus.OPEN);t.setLastRetestMode(null);t.setLastRetestResult(null);
        t.setLastRetestComment(null);t.setLastRetestedBy(null);t.setLastRetestedAt(null);tasks.save(t);
        audit.log("TASK",t.getId(),"APP_VALIDATED",env+" 环境应用验证通过，等待选择复测方式",
                env+" application validation passed; retest method selection required",currentUser.name());
    }

    private void startAutoRetest(RemediationTask t,TaskActionRequest req){
        String env=retestEnvironment(t);
        if("RUNNING".equalsIgnoreCase(t.getLastRetestResult()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"An automatic retest is already running");
        t.setLastRetestMode("AUTO");t.setLastRetestResult("RUNNING");
        t.setLastRetestComment(req==null?null:req.comment());t.setLastRetestedBy(currentUser.name());
        t.setLastRetestedAt(Instant.now());t.setStatus(TaskStatus.IN_PROGRESS);tasks.save(t);
        scanService.createTaskRescan(t,env);
    }

    private void submitManualRetest(RemediationTask t,TaskActionRequest req){
        String env=retestEnvironment(t);
        if(req==null||req.result()==null||(!req.result().equalsIgnoreCase("PASS")&&!req.result().equalsIgnoreCase("FAIL")))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Manual retest result must be PASS or FAIL");
        if(req.comment()==null||req.comment().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Manual retest evidence is required");
        boolean pass=req.result().equalsIgnoreCase("PASS");
        t.setLastRetestMode("MANUAL");t.setLastRetestResult(pass?"PASSED":"FAILED");
        t.setLastRetestComment(req.comment());t.setLastRetestedBy(currentUser.name());t.setLastRetestedAt(Instant.now());
        tasks.save(t);scanService.completeManualTaskRetest(t,env,pass,req.comment());
    }

    private String retestEnvironment(RemediationTask t){
        return switch(t.getStage()){
            case TEST_RESCAN -> "TEST";
            case PREPROD_RESCAN -> "PREPROD";
            case PROD_RESCAN -> "PROD";
            default -> throw new ResponseStatusException(HttpStatus.CONFLICT,"Task is not waiting for a retest");
        };
    }

    private void retry(RemediationTask t){
        if(t.getStatus()!=TaskStatus.BLOCKED)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Only a blocked patch stage can be retried");
        if(t.getStage()==TaskStage.TEST_PATCH)orchestration.startPatchRun(t,"TEST","Ring 0 · Test");
        else if(t.getStage()==TaskStage.PREPROD_PATCH&&approvedForProduction(t)){
            // Compatibility repair for tasks approved by older releases. The product
            // workflow is TEST -> release approval -> PROD; PREPROD was previously
            // inserted here even when the CMDB had no matching pre-production asset.
            t.setStage(TaskStage.PROD_PATCH);tasks.save(t);
            orchestration.startPatchRun(t,"PROD","Ring 0 · 5% → Ring 1 · 20% → Ring 2 · 75%");
        }
        else if(t.getStage()==TaskStage.PREPROD_PATCH)orchestration.startPatchRun(t,"PREPROD","Ring 0 · Pre-production");
        else if(t.getStage()==TaskStage.PROD_PATCH)orchestration.startPatchRun(t,"PROD","Ring 0 · 5% → Ring 1 · 20% → Ring 2 · 75%");
        else throw new ResponseStatusException(HttpStatus.CONFLICT,"Task cannot retry at current stage");
        t.setStatus(TaskStatus.IN_PROGRESS);
    }
    private boolean approvedForProduction(RemediationTask task){
        if(task.getApprovalId()==null)return false;
        return approvals.findById(task.getApprovalId()).map(approval ->
                approval.getStatus()==ApprovalStatus.APPROVED||approval.getStatus()==ApprovalStatus.IMPLEMENTING
                        ||approval.getStatus()==ApprovalStatus.CLOSED).orElse(false);
    }
    private void ensure(RemediationTask t,TaskStage... allowed){if(Arrays.stream(allowed).noneMatch(x->x==t.getStage()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Action is not valid for current stage");}
    private RemediationTask require(Long id){return tasks.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));}
}
