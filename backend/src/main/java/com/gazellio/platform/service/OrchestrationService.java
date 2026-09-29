package com.gazellio.platform.service;

import com.gazellio.platform.dto.ApiDtos.*;
import com.gazellio.platform.model.*;
import com.gazellio.platform.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

import static com.gazellio.platform.model.Enums.*;

@Service
@RequiredArgsConstructor
public class OrchestrationService {
    private final OrchestrationTemplateRepository templates;
    private final OrchestrationTemplateStepRepository templateSteps;
    private final OrchestrationRunRepository runs;
    private final OrchestrationRunStepRepository runSteps;
    private final PatchDeploymentRepository deployments;
    private final RemediationTaskRepository tasks;
    private final FindingRepository findings;
    private final AssetRepository assets;
    private final AssetPatchStateRepository assetPatchStates;
    private final PatchRepository patches;
    private final ViewService view;
    private final AuditService audit;
    private final CurrentUserService currentUser;

    public List<TemplateView> templates(){
        return templates.findByEnabledTrueOrderByNameZhAsc().stream().map(view::template).toList();
    }

    public TemplateView template(Long id){
        return view.template(templates.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    public List<RunView> runs(){
        return runs.findTop200ByOrderByCreatedAtDesc().stream().map(view::run).toList();
    }

    public RunView run(Long id){
        return view.run(runs.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    public List<DeploymentView> deployments(){
        return deployments.findTop200ByOrderByCreatedAtDesc().stream().map(view::deployment).toList();
    }

    @Transactional
    public RunView startPatchRun(RemediationTask task, String environment, String ring){
        EnvironmentType env;
        try {
            env = EnvironmentType.valueOf(environment.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid environment");
        }

        findings.findById(task.getFindingId()).orElseThrow();
        Asset source = assets.findById(task.getAssetId()).orElseThrow();
        if (task.getPatchId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No patch selected");
        }
        Patch patch = patches.findById(task.getPatchId()).orElseThrow();

        List<Asset> targets = assets.findByBusinessServiceAndEnvironment(source.getBusinessService(), env);
        if (targets.isEmpty() && source.getEnvironment() == env) {
            targets = List.of(source);
        }
        if (targets.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No mapped " + environment + " asset for business service");
        }

        String templateCode = task.getChangeType() == ChangeType.EMERGENCY
                ? "PATCH-EMERGENCY"
                : "PATCH-STANDARD";
        OrchestrationTemplate tpl = templates.findByCode(templateCode).orElseThrow();

        PatchDeployment dep = deployments.save(PatchDeployment.builder()
                .deploymentNo("DEP-" + System.currentTimeMillis())
                .taskId(task.getId())
                .patchId(patch.getId())
                .environment(env.name())
                .ring(ring)
                .status(DeploymentStatus.RUNNING)
                .progress(1)
                .targetCount(targets.size())
                .startedAt(Instant.now())
                .build());

        OrchestrationRun run = runs.save(OrchestrationRun.builder()
                .runNo("RUN-" + System.currentTimeMillis())
                .templateId(tpl.getId())
                .taskId(task.getId())
                .deploymentId(dep.getId())
                .environment(env.name())
                .ring(ring)
                .status(RunStatus.RUNNING)
                .currentStep(1)
                .progress(1)
                .startedAt(Instant.now())
                .build());

        dep.setOrchestrationRunId(run.getId());
        deployments.save(dep);

        for (OrchestrationTemplateStep s : templateSteps.findByTemplateIdOrderByStepOrderAsc(tpl.getId())) {
            boolean first = s.getStepOrder() == 1;
            runSteps.save(OrchestrationRunStep.builder()
                    .runId(run.getId())
                    .stepOrder(s.getStepOrder())
                    .code(s.getCode())
                    .nameZh(s.getNameZh())
                    .nameEn(s.getNameEn())
                    .status(first ? RunStepStatus.RUNNING : RunStepStatus.WAITING)
                    .startedAt(first ? Instant.now() : null)
                    .messageZh(first ? "正在执行" : "等待执行")
                    .messageEn(first ? "Running" : "Waiting")
                    .build());
        }

        task.setLatestRunId(run.getId());
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setUpdatedAt(Instant.now());
        tasks.save(task);

        audit.log("RUN", run.getId(), "START",
                "启动补丁自动化执行 " + run.getRunNo() + " · " + env.name(),
                "Started patch automation " + run.getRunNo() + " · " + env.name(),
                currentUser.name());
        return view.run(run);
    }

    @Scheduled(fixedDelay = 4500)
    @Transactional
    public void advanceRuns(){
        for (OrchestrationRun run : runs.findByStatusIn(List.of(RunStatus.RUNNING))) {
            List<OrchestrationRunStep> steps = runSteps.findByRunIdOrderByStepOrderAsc(run.getId());
            OrchestrationRunStep current = steps.stream()
                    .filter(s -> s.getStatus() == RunStepStatus.RUNNING)
                    .findFirst()
                    .orElse(null);

            if (current == null && !steps.isEmpty()) {
                OrchestrationRunStep waiting = steps.stream()
                        .filter(s -> s.getStatus() == RunStepStatus.WAITING)
                        .findFirst()
                        .orElse(null);
                if (waiting != null) {
                    waiting.setStatus(RunStepStatus.RUNNING);
                    waiting.setStartedAt(Instant.now());
                    runSteps.save(waiting);
                }
                continue;
            }

            if (current == null) {
                completeRun(run, steps);
                continue;
            }

            current.setStatus(RunStepStatus.SUCCEEDED);
            current.setCompletedAt(Instant.now());
            current.setMessageZh("执行成功");
            current.setMessageEn("Succeeded");
            runSteps.save(current);

            final int currentStepOrder = current.getStepOrder();
            OrchestrationRunStep next = steps.stream()
                    .filter(s -> s.getStepOrder() > currentStepOrder
                            && s.getStatus() == RunStepStatus.WAITING)
                    .findFirst()
                    .orElse(null);

            if (next == null) {
                completeRun(run, steps);
                continue;
            }

            next.setStatus(RunStepStatus.RUNNING);
            next.setStartedAt(Instant.now());
            next.setMessageZh("正在执行");
            next.setMessageEn("Running");
            runSteps.save(next);

            run.setCurrentStep(next.getStepOrder());
            run.setProgress(Math.min(96,
                    (int) Math.round((currentStepOrder * 100.0) / steps.size())));
            runs.save(run);

            deployments.findById(run.getDeploymentId()).ifPresent(d -> {
                d.setProgress(run.getProgress());
                deployments.save(d);
            });
        }
    }

    private void completeRun(OrchestrationRun run, List<OrchestrationRunStep> steps){
        run.setStatus(RunStatus.SUCCEEDED);
        run.setProgress(100);
        run.setCompletedAt(Instant.now());
        run.setCurrentStep(steps.size());
        runs.save(run);

        PatchDeployment dep = deployments.findById(run.getDeploymentId()).orElse(null);
        RemediationTask task = run.getTaskId() == null
                ? null
                : tasks.findById(run.getTaskId()).orElse(null);

        if (dep != null) {
            dep.setStatus(DeploymentStatus.SUCCEEDED);
            dep.setProgress(100);
            dep.setSuccessCount(dep.getTargetCount());
            dep.setCompletedAt(Instant.now());
            deployments.save(dep);
        }

        if (task != null) {
            Asset source = assets.findById(task.getAssetId()).orElseThrow();
            EnvironmentType env = EnvironmentType.valueOf(run.getEnvironment());
            List<Asset> targetAssets = assets.findByBusinessServiceAndEnvironment(source.getBusinessService(), env);
            if (targetAssets.isEmpty() && source.getEnvironment() == env) {
                targetAssets = List.of(source);
            }

            for (Asset a : targetAssets) {
                AssetPatchState st = assetPatchStates
                        .findByAssetIdAndPatchId(a.getId(), task.getPatchId())
                        .orElseGet(AssetPatchState::new);
                st.setAssetId(a.getId());
                st.setPatchId(task.getPatchId());
                st.setInstalled(true);
                st.setVerified(false);
                st.setInstalledAt(Instant.now());
                st.setDeploymentNo(dep == null ? null : dep.getDeploymentNo());
                assetPatchStates.save(st);
            }

            task.setStage(switch (env) {
                case TEST -> TaskStage.APP_VERIFY;
                case PREPROD -> TaskStage.PREPROD_VERIFY;
                case PROD -> TaskStage.PROD_VERIFY;
                default -> task.getStage();
            });
            task.setUpdatedAt(Instant.now());
            tasks.save(task);

            audit.log("TASK", task.getId(), "PATCH_SUCCEEDED",
                    env.name() + " 环境补丁执行完成，进入应用验证",
                    "Patch execution completed in " + env.name() + "; application verification required",
                    "Gazellio Automation");
        }
    }

    @Transactional
    public RunView pause(Long id){
        OrchestrationRun r = requireRun(id);
        r.setStatus(RunStatus.PAUSED);
        runs.save(r);
        deployments.findById(r.getDeploymentId()).ifPresent(d -> {
            d.setStatus(DeploymentStatus.PAUSED);
            deployments.save(d);
        });
        return view.run(r);
    }

    @Transactional
    public RunView resume(Long id){
        OrchestrationRun r = requireRun(id);
        if (r.getStatus() != RunStatus.PAUSED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
        r.setStatus(RunStatus.RUNNING);
        runs.save(r);
        deployments.findById(r.getDeploymentId()).ifPresent(d -> {
            d.setStatus(DeploymentStatus.RUNNING);
            deployments.save(d);
        });
        return view.run(r);
    }

    @Transactional
    public RunView rollback(Long id){
        OrchestrationRun r = requireRun(id);
        r.setStatus(RunStatus.ROLLED_BACK);
        r.setCompletedAt(Instant.now());
        runs.save(r);

        RemediationTask task = tasks.findById(r.getTaskId()).orElse(null);
        if (task != null) {
            Asset source = assets.findById(task.getAssetId()).orElse(null);
            if (source != null) {
                EnvironmentType env = EnvironmentType.valueOf(r.getEnvironment());
                for (Asset a : assets.findByBusinessServiceAndEnvironment(source.getBusinessService(), env)) {
                    assetPatchStates.findByAssetIdAndPatchId(a.getId(), task.getPatchId()).ifPresent(st -> {
                        st.setInstalled(false);
                        st.setVerified(false);
                        assetPatchStates.save(st);
                    });
                }
            }

            task.setStage(switch (r.getEnvironment()) {
                case "TEST" -> TaskStage.TEST_PATCH;
                case "PREPROD" -> TaskStage.PREPROD_PATCH;
                default -> TaskStage.PROD_PATCH;
            });
            task.setStatus(TaskStatus.BLOCKED);
            task.setUpdatedAt(Instant.now());
            tasks.save(task);
        }

        deployments.findById(r.getDeploymentId()).ifPresent(d -> {
            d.setStatus(DeploymentStatus.ROLLED_BACK);
            d.setCompletedAt(Instant.now());
            deployments.save(d);
        });

        for (OrchestrationRunStep s : runSteps.findByRunIdOrderByStepOrderAsc(r.getId())) {
            if (s.getStatus() == RunStepStatus.RUNNING || s.getStatus() == RunStepStatus.SUCCEEDED) {
                s.setStatus(RunStepStatus.ROLLED_BACK);
                s.setCompletedAt(Instant.now());
                s.setMessageZh("已回滚");
                s.setMessageEn("Rolled back");
                runSteps.save(s);
            }
        }

        audit.log("RUN", id, "ROLLBACK",
                "自动化执行已回滚",
                "Automation run rolled back",
                currentUser.name());
        return view.run(r);
    }

    private OrchestrationRun requireRun(Long id){
        return runs.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
