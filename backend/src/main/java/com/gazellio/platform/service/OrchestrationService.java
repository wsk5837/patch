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
    private static final LinkedHashMap<String,String> BINDING_KEYS=new LinkedHashMap<>();
    private static final Map<String,String> FALLBACK_CODES=Map.of(
            "STANDARD_PATCH","PATCH-STANDARD","EMERGENCY_PATCH","PATCH-EMERGENCY",
            "RETEST","PATCH-RETEST","BATCH_PATCH","PATCH-STANDARD");
    static {
        BINDING_KEYS.put("STANDARD_PATCH","automation.template.standard");
        BINDING_KEYS.put("EMERGENCY_PATCH","automation.template.emergency");
        BINDING_KEYS.put("RETEST","automation.template.retest");
        BINDING_KEYS.put("BATCH_PATCH","automation.template.batch");
    }
    private final OrchestrationTemplateRepository templates;
    private final OrchestrationTemplateStepRepository templateSteps;
    private final OrchestrationRunRepository runs;
    private final OrchestrationRunStepRepository runSteps;
    private final PatchDeploymentRepository deployments;
    private final DeploymentTargetRepository deploymentTargets;
    private final RemediationTaskRepository tasks;
    private final FindingRepository findings;
    private final AssetRepository assets;
    private final AssetPatchStateRepository assetPatchStates;
    private final PatchRepository patches;
    private final SystemSettingRepository settings;
    private final ViewService view;
    private final AuditService audit;
    private final CurrentUserService currentUser;
    private final AssetEligibilityPolicy eligibility;

    public List<TemplateView> templates(){
        return view.templateViews(templates.findAllByOrderByNameZhAsc());
    }

    public List<AutomationBindingView> bindings(){
        List<AutomationBindingView> result=new ArrayList<>();
        for(String scenario:BINDING_KEYS.keySet()){
            OrchestrationTemplate template=resolveBoundTemplate(scenario);
            result.add(new AutomationBindingView(scenario,template.getId(),template.getCode(),template.getNameZh(),
                    template.getNameEn(),template.getType(),template.getVersion(),template.isEnabled()));
        }
        return result;
    }

    @Transactional
    public List<AutomationBindingView> updateBindings(AutomationBindingsUpdateRequest request){
        if(request==null||request.bindings()==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Bindings are required");
        for(var entry:request.bindings().entrySet()){
            String scenario=entry.getKey()==null?"":entry.getKey().trim().toUpperCase(Locale.ROOT);
            String settingKey=BINDING_KEYS.get(scenario);
            if(settingKey==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported automation scenario: "+scenario);
            OrchestrationTemplate template=templates.findById(entry.getValue()).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND,"Automation template not found"));
            if(!template.isEnabled())throw new ResponseStatusException(HttpStatus.CONFLICT,"Disabled template cannot be activated");
            String expected="RETEST".equals(scenario)?"RETEST":"PATCH";
            if(!expected.equalsIgnoreCase(template.getType()))throw new ResponseStatusException(HttpStatus.CONFLICT,
                    scenario+" requires a "+expected+" template");
            SystemSetting setting=settings.findById(settingKey).orElseGet(() -> SystemSetting.builder().settingKey(settingKey).build());
            setting.setSettingValue(String.valueOf(template.getId()));setting.setUpdatedAt(Instant.now());settings.save(setting);
        }
        audit.log("AUTOMATION_BINDING","runtime","UPDATE","自动化执行场景绑定已生效","Automation runtime bindings activated",currentUser.name());
        return bindings();
    }

    public OrchestrationTemplate resolveBoundTemplate(String scenario){
        String normalized=scenario==null?"":scenario.trim().toUpperCase(Locale.ROOT);
        String settingKey=BINDING_KEYS.get(normalized);
        if(settingKey==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported automation scenario");
        OrchestrationTemplate selected=settings.findById(settingKey).map(SystemSetting::getSettingValue)
                .filter(v->v!=null&&!v.isBlank()).flatMap(v->{try{return templates.findById(Long.parseLong(v));}catch(Exception ignored){return Optional.empty();}})
                .filter(OrchestrationTemplate::isEnabled).orElse(null);
        if(selected==null)selected=templates.findByCode(FALLBACK_CODES.get(normalized)).filter(OrchestrationTemplate::isEnabled).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.CONFLICT,"No active orchestration template is bound to "+normalized));
        return selected;
    }

    public TemplateView template(Long id){
        return view.template(templates.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    @Transactional
    public TemplateView createTemplate(TemplateSaveRequest request){
        String code=normalizeTemplateCode(request.code());
        if(templates.findByCode(code).isPresent())throw new ResponseStatusException(HttpStatus.CONFLICT,"Template code already exists");
        OrchestrationTemplate template=templates.save(OrchestrationTemplate.builder().code(code).nameZh(request.nameZh().trim())
                .nameEn(request.nameEn().trim()).type(request.type().trim().toUpperCase(Locale.ROOT)).enabled(request.enabled()).version(1).updatedAt(Instant.now()).build());
        replaceTemplateSteps(template.getId(),request.steps());
        audit.log("ORCHESTRATION_TEMPLATE",template.getId(),"CREATE","创建自动化编排模板："+template.getNameZh(),"Created orchestration template: "+template.getNameEn(),currentUser.name());
        return view.template(template);
    }

    @Transactional
    public TemplateView updateTemplate(Long id,TemplateSaveRequest request){
        OrchestrationTemplate template=templates.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        List<String> boundScenarios=boundScenarios(id);
        if(!boundScenarios.isEmpty()&&!request.enabled())throw new ResponseStatusException(HttpStatus.CONFLICT,"Active runtime template cannot be disabled");
        for(String scenario:boundScenarios){String expected="RETEST".equals(scenario)?"RETEST":"PATCH";if(!expected.equalsIgnoreCase(request.type()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Active "+scenario+" template must remain type "+expected);}
        String code=normalizeTemplateCode(request.code());
        templates.findByCode(code).filter(other->!other.getId().equals(id)).ifPresent(other->{throw new ResponseStatusException(HttpStatus.CONFLICT,"Template code already exists");});
        template.setCode(code);template.setNameZh(request.nameZh().trim());template.setNameEn(request.nameEn().trim());
        template.setType(request.type().trim().toUpperCase(Locale.ROOT));template.setEnabled(request.enabled());
        template.setVersion((template.getVersion()==null?0:template.getVersion())+1);template.setUpdatedAt(Instant.now());templates.save(template);
        replaceTemplateSteps(id,request.steps());
        audit.log("ORCHESTRATION_TEMPLATE",id,"UPDATE","更新自动化编排模板："+template.getNameZh(),"Updated orchestration template: "+template.getNameEn(),currentUser.name());
        return view.template(template);
    }

    @Transactional
    public void deleteTemplate(Long id){
        OrchestrationTemplate template=templates.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        boolean bound=BINDING_KEYS.values().stream().map(settings::findById).flatMap(Optional::stream)
                .anyMatch(s->String.valueOf(id).equals(s.getSettingValue()));
        if(bound)throw new ResponseStatusException(HttpStatus.CONFLICT,"Template is active in runtime settings and cannot be deleted");
        if(runs.existsByTemplateId(id)){
            template.setEnabled(false);template.setUpdatedAt(Instant.now());templates.save(template);
            audit.log("ORCHESTRATION_TEMPLATE",id,"DISABLE","模板已有执行历史，已停用："+template.getNameZh(),"Template has run history and was disabled: "+template.getNameEn(),currentUser.name());
            return;
        }
        templateSteps.deleteByTemplateId(id);templates.delete(template);
        audit.log("ORCHESTRATION_TEMPLATE",id,"DELETE","删除自动化编排模板："+template.getNameZh(),"Deleted orchestration template: "+template.getNameEn(),currentUser.name());
    }

    private List<String> boundScenarios(Long templateId){
        return BINDING_KEYS.entrySet().stream().filter(entry->settings.findById(entry.getValue())
                .map(SystemSetting::getSettingValue).filter(String.valueOf(templateId)::equals).isPresent())
                .map(Map.Entry::getKey).toList();
    }

    private void replaceTemplateSteps(Long templateId,List<TemplateStepSaveRequest> steps){
        if(steps==null||steps.isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"At least one orchestration step is required");
        LinkedHashSet<String> codes=new LinkedHashSet<>();
        for(TemplateStepSaveRequest step:steps){
            String code=normalizeTemplateCode(step.code());
            if(!codes.add(code))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Duplicate step code: "+code);
        }
        templateSteps.deleteByTemplateId(templateId);templateSteps.flush();
        int order=1;
        for(TemplateStepSaveRequest step:steps){
            templateSteps.save(OrchestrationTemplateStep.builder().templateId(templateId).stepOrder(order++)
                    .code(normalizeTemplateCode(step.code())).nameZh(step.nameZh().trim()).nameEn(step.nameEn().trim())
                    .stepType(step.stepType().trim().toUpperCase(Locale.ROOT)).rollbackPoint(step.rollbackPoint()).build());
        }
    }

    private String normalizeTemplateCode(String value){
        String code=value==null?"":value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_-]+","-");
        if(code.isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid template or step code");
        return code;
    }

    public List<RunView> runs(){
        return view.runViews(runs.findTop200ByOrderByCreatedAtDesc());
    }

    public RunView run(Long id){
        return view.run(runs.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    public List<DeploymentView> deployments(){
        return view.deploymentViews(deployments.findTop200ByOrderByCreatedAtDesc());
    }

    @Transactional
    public RunView startPatchRun(RemediationTask task, String environment, String ring){
        return startPatchRun(task,environment,ring,null);
    }

    public List<AssetView> deploymentCandidates(RemediationTask task,String environment){
        EnvironmentType env;
        try {
            env = EnvironmentType.valueOf(environment.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid environment");
        }

        Asset source = assets.findById(task.getAssetId()).orElseThrow();
        if (task.getPatchId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No patch selected");
        }
        Patch patch = patches.findById(task.getPatchId()).orElseThrow();
        List<Asset> candidates=new ArrayList<>(assets.findByActiveTrueOrderByNameAsc().stream()
                .filter(a->a.getEnvironment()==env)
                .filter(eligibility::executionReachable)
                .filter(a->Objects.equals(source.getBusinessService(),a.getBusinessService())||patchApplicable(a,patch))
                .sorted(Comparator.comparing((Asset a)->!Objects.equals(source.getBusinessService(),a.getBusinessService()))
                        .thenComparing(Asset::getName,String.CASE_INSENSITIVE_ORDER))
                .toList());
        // Some CMDBs do not classify any CI as TEST/PREPROD. In that case expose the
        // source CI only as an isolated validation baseline. The executor creates a
        // disposable validation context from its fingerprint; the source CI is not modified.
        if(candidates.isEmpty()&&env!=EnvironmentType.PROD&&eligibility.scannable(source))candidates.add(source);
        return view.assetViews(candidates);
    }

    @Transactional
    public RunView startPatchRun(RemediationTask task, String environment, String ring,Long selectedAssetId){
        EnvironmentType env;
        try {
            env = EnvironmentType.valueOf(environment.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid environment");
        }

        findings.findById(task.getFindingId()).orElseThrow();
        Asset source = assets.findById(task.getAssetId()).orElseThrow();
        if (task.getPatchId() == null) throw new ResponseStatusException(HttpStatus.CONFLICT,"No patch selected");
        Patch patch = patches.findById(task.getPatchId()).orElseThrow();

        List<Asset> targets;
        boolean isolatedValidation=false;
        if(selectedAssetId!=null){
            Asset selected=assets.findById(selectedAssetId).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND,"Selected validation asset not found"));
            isolatedValidation=selected.getEnvironment()!=env;
            boolean isolatedSource=isolatedValidation&&env!=EnvironmentType.PROD&&Objects.equals(selected.getId(),source.getId())&&eligibility.scannable(selected);
            boolean directTarget=!isolatedValidation&&eligibility.executionReachable(selected);
            if((!isolatedSource&&!directTarget)||
                    (!Objects.equals(source.getBusinessService(),selected.getBusinessService())&&!patchApplicable(selected,patch)))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Selected validation target is not reachable or patch-compatible");
            targets=List.of(selected);
        }else targets = assets.findByBusinessServiceAndEnvironment(source.getBusinessService(), env).stream()
                .filter(eligibility::executionReachable).toList();
        if (targets.isEmpty() && source.getEnvironment() == env) {
            targets = List.of(source);
        }
        if (targets.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A compatible " + environment + " validation asset must be selected");
        }

        OrchestrationTemplate tpl = resolveBoundTemplate(task.getChangeType() == ChangeType.EMERGENCY
                ? "EMERGENCY_PATCH" : "STANDARD_PATCH");

        PatchDeployment dep = deployments.save(PatchDeployment.builder()
                .deploymentNo("DEP-" + System.currentTimeMillis())
                .taskId(task.getId())
                .patchId(patch.getId())
                .environment(env.name())
                .ring(ring)
                .status(DeploymentStatus.RUNNING)
                .progress(1)
                .targetCount(targets.size())
                .selectionMode(isolatedValidation?"ISOLATED_VALIDATION":"TASK")
                .scopeSummary(isolatedValidation?"根据源配置项指纹创建一次性隔离验证环境，源配置项不会被修改":null)
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

        for (Asset target : targets) {
            deploymentTargets.save(DeploymentTarget.builder()
                    .deploymentId(dep.getId()).runId(run.getId()).assetId(target.getId()).status("RUNNING").progress(1)
                    .startedAt(Instant.now()).message(isolatedValidation
                            ?"隔离验证环境已排队，源配置项不会被修改"
                            :"执行通道已连接，等待前置检查").build());
        }

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

    private boolean patchApplicable(Asset asset,Patch patch){
        String product=normalizeProduct(patch.getProduct());
        String inventory=(value(asset.getInstalledProducts())+" "+value(asset.getName())+" "+
                value(asset.getBusinessService())+" "+value(asset.getCmdbClassName())+" "+value(asset.getOsName())).toLowerCase(Locale.ROOT);
        if(product.isBlank())return true;
        if(product.contains("windows server"))return inventory.contains("windows server");
        if(product.contains("red hat")||product.equals("rhel"))return inventory.contains("red hat")||inventory.contains("rocky");
        if(product.contains("php cgi"))return inventory.contains("php");
        if(product.contains("spring framework"))return inventory.contains("spring framework")||inventory.contains("spring boot");
        return inventory.contains(product)||(product.equals("tomcat")&&inventory.contains("apache tomcat"));
    }

    private String normalizeProduct(String value){return value(value).toLowerCase(Locale.ROOT).replace("apache ","").trim();}
    private String value(String value){return value==null?"":value;}

    @Transactional
    public RunView startRetestRun(RemediationTask task,String environment){
        EnvironmentType env;
        try{env=EnvironmentType.valueOf(environment.toUpperCase(Locale.ROOT));}
        catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid environment");}
        Asset source=assets.findById(task.getAssetId()).orElseThrow();
        List<Asset> targets=assets.findByBusinessServiceAndEnvironment(source.getBusinessService(),env);
        if(targets.isEmpty()&&source.getEnvironment()==env)targets=List.of(source);
        if(targets.isEmpty())throw new ResponseStatusException(HttpStatus.CONFLICT,"No mapped retest target");
        OrchestrationTemplate template=resolveBoundTemplate("RETEST");
        OrchestrationRun run=runs.save(OrchestrationRun.builder().runNo("RET-"+System.currentTimeMillis())
                .templateId(template.getId()).taskId(task.getId()).environment(env.name())
                .ring("Verification only").status(RunStatus.RUNNING).currentStep(1).progress(1).startedAt(Instant.now()).build());
        for(Asset target:targets){
            deploymentTargets.save(DeploymentTarget.builder().runId(run.getId()).assetId(target.getId())
                    .status("RUNNING").progress(1).startedAt(Instant.now()).message("读取补丁安装状态与验证基线").build());
        }
        for(OrchestrationTemplateStep step:templateSteps.findByTemplateIdOrderByStepOrderAsc(template.getId())){
            boolean first=step.getStepOrder()==1;
            runSteps.save(OrchestrationRunStep.builder().runId(run.getId()).stepOrder(step.getStepOrder()).code(step.getCode())
                    .nameZh(step.getNameZh()).nameEn(step.getNameEn()).status(first?RunStepStatus.RUNNING:RunStepStatus.WAITING)
                    .startedAt(first?Instant.now():null).messageZh(first?"正在读取验证基线":"等待复测")
                    .messageEn(first?"Reading validation baseline":"Waiting for retest").build());
        }
        task.setLatestRunId(run.getId());task.setUpdatedAt(Instant.now());tasks.save(task);
        audit.log("RUN",run.getId(),"RETEST_START",env.name()+" 环境补丁效果复测已启动",
                "Patch effect retest started for "+env.name(),currentUser.name());
        return view.run(run);
    }

    @Scheduled(fixedDelay = 4500, initialDelayString = "${app.scheduler.initial-delay-ms:60000}")
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

            if(run.getDeploymentId()!=null)deployments.findById(run.getDeploymentId()).ifPresent(d -> {
                d.setProgress(run.getProgress());deployments.save(d);
            });
            PatchDeployment activeDeployment=run.getDeploymentId()==null?null:deployments.findById(run.getDeploymentId()).orElse(null);
            Integer activeBatch=batchNumber(next.getCode());
            for (DeploymentTarget target : targetsForRun(run)) {
                if("FAILED".equals(target.getStatus())||"RETRY_PENDING".equals(target.getStatus()))continue;
                if(activeDeployment!=null&&"CIDR".equalsIgnoreCase(activeDeployment.getSelectionMode())&&activeBatch!=null){
                    int targetBatch=target.getBatchNo()==null?1:target.getBatchNo();
                    if(targetBatch<activeBatch){
                        target.setStatus("SUCCEEDED");target.setProgress(100);target.setCompletedAt(Instant.now());
                        target.setMessage("批次 "+targetBatch+" · 已完成");
                    }else if(targetBatch==activeBatch){
                        target.setStatus("RUNNING");target.setStartedAt(target.getStartedAt()==null?Instant.now():target.getStartedAt());
                        target.setProgress(batchProgress(next.getCode()));target.setMessage(next.getNameZh());
                    }else{
                        target.setStatus("WAITING");target.setProgress(0);target.setMessage("批次 "+targetBatch+" · 等待执行");
                    }
                }else{
                    target.setStatus("RUNNING");target.setProgress(run.getProgress());
                    target.setMessage(next.getNameZh() + " · " + run.getProgress() + "%");
                }
                deploymentTargets.save(target);
            }
        }
    }

    private void completeRun(OrchestrationRun run, List<OrchestrationRunStep> steps){
        run.setStatus(RunStatus.SUCCEEDED);
        run.setProgress(100);
        run.setCompletedAt(Instant.now());
        run.setCurrentStep(steps.size());
        runs.save(run);

        OrchestrationTemplate template=templates.findById(run.getTemplateId()).orElse(null);
        boolean retest=template!=null&&"RETEST".equalsIgnoreCase(template.getType());

        PatchDeployment dep = run.getDeploymentId() == null
                ? null
                : deployments.findById(run.getDeploymentId()).orElse(null);
        RemediationTask task = run.getTaskId() == null
                ? null
                : tasks.findById(run.getTaskId()).orElse(null);

        if (dep != null) {
            List<DeploymentTarget> deploymentRows=targetsForRun(run);
            long failures=deploymentRows.stream().filter(t->"FAILED".equals(t.getStatus())).count();
            if(failures>0){run.setStatus(RunStatus.FAILED);runs.save(run);}
            dep.setStatus(failures>0?DeploymentStatus.FAILED:DeploymentStatus.SUCCEEDED);
            dep.setProgress(100);
            dep.setSuccessCount((int)deploymentRows.stream().filter(t->!"FAILED".equals(t.getStatus())).count());
            dep.setFailureCount((int)failures);
            dep.setCompletedAt(Instant.now());
            deployments.save(dep);
            for (DeploymentTarget target : deploymentRows) {
                if("FAILED".equals(target.getStatus()))continue;
                target.setStatus("SUCCEEDED"); target.setProgress(100); target.setCompletedAt(Instant.now());
                target.setMessage("补丁安装、健康检查与证据回写完成"); deploymentTargets.save(target);
            }
            if(task==null&&"CIDR".equalsIgnoreCase(dep.getSelectionMode())){
                for(DeploymentTarget target:deploymentRows){
                    if("FAILED".equals(target.getStatus()))continue;
                    AssetPatchState state=assetPatchStates.findByAssetIdAndPatchId(target.getAssetId(),dep.getPatchId())
                            .orElseGet(AssetPatchState::new);
                    state.setAssetId(target.getAssetId());state.setPatchId(dep.getPatchId());state.setInstalled(true);
                    state.setVerified(true);state.setInstalledAt(Instant.now());state.setVerifiedAt(Instant.now());
                    state.setDeploymentNo(dep.getDeploymentNo());assetPatchStates.save(state);
                }
            }
        }

        if(retest){
            for(DeploymentTarget target:targetsForRun(run)){
                target.setStatus("SUCCEEDED");target.setProgress(100);target.setCompletedAt(Instant.now());
                target.setMessage("补丁安装状态、版本标识与应用健康验证完成，等待定向漏洞扫描");deploymentTargets.save(target);
            }
            audit.log("RUN",run.getId(),"RETEST_VALIDATION_READY","补丁安装状态与效果校验完成，等待定向漏洞扫描结果",
                    "Patch state and effect validation completed; waiting for the targeted vulnerability scan","Gazellio Scanner");
            return;
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
        if(r.getDeploymentId()!=null)deployments.findById(r.getDeploymentId()).ifPresent(d -> {
            d.setStatus(DeploymentStatus.PAUSED);
            deployments.save(d);
        });
        targetsForRun(r).forEach(t->{t.setStatus("PAUSED");t.setMessage("执行已暂停");deploymentTargets.save(t);});
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
        if(r.getDeploymentId()!=null)deployments.findById(r.getDeploymentId()).ifPresent(d -> {
            d.setStatus(DeploymentStatus.RUNNING);
            deployments.save(d);
        });
        targetsForRun(r).forEach(t->{t.setStatus("RUNNING");t.setMessage("执行已恢复");deploymentTargets.save(t);});
        return view.run(r);
    }

    @Transactional
    public RunView rollback(Long id){
        OrchestrationRun r = requireRun(id);
        OrchestrationTemplate template=templates.findById(r.getTemplateId()).orElse(null);
        if(template!=null&&"RETEST".equalsIgnoreCase(template.getType())){
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Retest runs do not install packages and cannot be rolled back");
        }
        r.setStatus(RunStatus.ROLLED_BACK);
        r.setCompletedAt(Instant.now());
        runs.save(r);

        RemediationTask task = r.getTaskId()==null?null:tasks.findById(r.getTaskId()).orElse(null);
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

        if(r.getDeploymentId()!=null)deployments.findById(r.getDeploymentId()).ifPresent(d -> {
            d.setStatus(DeploymentStatus.ROLLED_BACK);
            d.setCompletedAt(Instant.now());
            deployments.save(d);
        });
        targetsForRun(r).forEach(t->{t.setStatus("ROLLED_BACK");t.setCompletedAt(Instant.now());t.setMessage("已恢复至回退点");deploymentTargets.save(t);});

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

    private List<DeploymentTarget> targetsForRun(OrchestrationRun run){
        List<DeploymentTarget> targets=deploymentTargets.findByRunIdOrderByAssetIdAsc(run.getId());
        if(targets.isEmpty()&&run.getDeploymentId()!=null)targets=deploymentTargets.findByDeploymentIdOrderByAssetIdAsc(run.getDeploymentId());
        return targets;
    }

    private Integer batchNumber(String code){
        if(code==null||!code.matches("B\\d{3}_.*"))return null;
        try{return Integer.parseInt(code.substring(1,4));}catch(Exception ignored){return null;}
    }

    private int batchProgress(String code){
        if(code==null)return 1;
        if(code.endsWith("PRECHECK"))return 20;
        if(code.endsWith("INSTALL"))return 70;
        if(code.endsWith("VERIFY"))return 95;
        return 1;
    }
}
