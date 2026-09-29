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
public class TaskService {
    private final RemediationTaskRepository tasks;
    private final FindingRepository findings;
    private final SecurityIncidentRepository incidents;
    private final AssetRepository assets;
    private final PatchRepository patches;
    private final ViewService view;
    private final OrchestrationService orchestration;
    private final ScanService scanService;
    private final AuditService audit;
    private final CurrentUserService currentUser;

    public List<TaskView> list(){return view.taskViews(tasks.findTop200ByOrderByUpdatedAtDesc());}
    public TaskView get(Long id){return view.task(require(id));}

    @Transactional
    public TaskView action(Long id,String action,TaskActionRequest req){
        RemediationTask t=require(id);
        switch(action.toLowerCase(Locale.ROOT)){
            case "start-test" -> { ensure(t,TaskStage.ASSIGNED,TaskStage.TEST_PATCH); t.setStage(TaskStage.TEST_PATCH);t.setStatus(TaskStatus.IN_PROGRESS);tasks.save(t);orchestration.startPatchRun(t,"TEST","Ring 0 · Test"); }
            case "verify-test" -> verifyAndRescan(t,TaskStage.APP_VERIFY,TaskStage.TEST_RESCAN,"TEST",req);
            case "verify-preprod" -> verifyAndRescan(t,TaskStage.PREPROD_VERIFY,TaskStage.PREPROD_RESCAN,"PREPROD",req);
            case "verify-prod" -> verifyAndRescan(t,TaskStage.PROD_VERIFY,TaskStage.PROD_RESCAN,"PROD",req);
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

    private void verifyAndRescan(RemediationTask t,TaskStage expected,TaskStage rescanStage,String env,TaskActionRequest req){
        ensure(t,expected); boolean pass=req==null||req.result()==null||!req.result().equalsIgnoreCase("FAIL");
        if(!pass){ t.setStage(env.equals("TEST")?TaskStage.TEST_PATCH:env.equals("PREPROD")?TaskStage.PREPROD_PATCH:TaskStage.PROD_PATCH);t.setStatus(TaskStatus.BLOCKED);tasks.save(t);return; }
        t.setStage(rescanStage);t.setStatus(TaskStatus.IN_PROGRESS);tasks.save(t);scanService.createTaskRescan(t,env);
    }

    private void retry(RemediationTask t){
        if(t.getStage()==TaskStage.TEST_PATCH)orchestration.startPatchRun(t,"TEST","Ring 0 · Test");
        else if(t.getStage()==TaskStage.PREPROD_PATCH)orchestration.startPatchRun(t,"PREPROD","Ring 0 · Pre-production");
        else if(t.getStage()==TaskStage.PROD_PATCH)orchestration.startPatchRun(t,"PROD","Ring 0 · 5%");
        else throw new ResponseStatusException(HttpStatus.CONFLICT,"Task cannot retry at current stage");
        t.setStatus(TaskStatus.IN_PROGRESS);
    }
    private void ensure(RemediationTask t,TaskStage... allowed){if(Arrays.stream(allowed).noneMatch(x->x==t.getStage()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Action is not valid for current stage");}
    private RemediationTask require(Long id){return tasks.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));}
}
