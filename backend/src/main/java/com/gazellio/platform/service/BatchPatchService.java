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
import java.util.stream.Collectors;

import static com.gazellio.platform.model.Enums.*;

@Service
@RequiredArgsConstructor
public class BatchPatchService {
    private static final int PREVIEW_LIMIT = 300;

    private final AssetRepository assets;
    private final PatchRepository patches;
    private final OrchestrationService orchestration;
    private final OrchestrationRunRepository runs;
    private final OrchestrationRunStepRepository runSteps;
    private final PatchDeploymentRepository deployments;
    private final DeploymentTargetRepository deploymentTargets;
    private final ChangeWorkOrderRepository changeOrders;
    private final RemediationTaskRepository remediationTasks;
    private final ViewService view;
    private final AuditService audit;
    private final CurrentUserService currentUser;
    private final SettingsService settings;

    public BatchScopePreview preview(BatchScopeRequest request) {
        Scope scope = resolve(request);
        int batchSize = bounded(request.batchSize(), settings.intValue("batchSize",20,1,500), 1, 500);
        int concurrency = bounded(request.concurrency(), Math.min(settings.intValue("batchConcurrency",10,1,200), batchSize), 1, Math.min(200, batchSize));
        double threshold = bounded(request.failureThreshold(), settings.intValue("autoRollbackThreshold",5,1,100), 0.1, 100.0);
        int batches = scope.targets.isEmpty() ? 0 : (int) Math.ceil(scope.targets.size() / (double) batchSize);
        List<String> warnings = new ArrayList<>();
        if (scope.offlineCount > 0) warnings.add(scope.offlineCount + " assets are offline");
        if (scope.excludedCount > 0) warnings.add(scope.excludedCount + " assets are excluded");
        if (scope.targets.size() > PREVIEW_LIMIT) warnings.add("Only the first " + PREVIEW_LIMIT + " assets are shown; all matched assets remain selected");
        if (scope.targets.stream().map(Asset::getEnvironment).distinct().count() > 1) warnings.add("The scope contains multiple environments");
        Map<String,Long> environments = scope.targets.stream().collect(Collectors.groupingBy(
                a -> a.getEnvironment().name(), LinkedHashMap::new, Collectors.counting()));
        Map<String,Long> types = scope.targets.stream().collect(Collectors.groupingBy(
                a -> value(a.getAssetType(), "UNKNOWN"), LinkedHashMap::new, Collectors.counting()));
        return new BatchScopePreview(scope.matchedCount, scope.applicableCount, scope.targets.size(),
                scope.excludedCount, scope.offlineCount,
                batchSize, concurrency, batches, threshold, scope.patch.getPatchId(), scope.patch.getProduct(),
                normalizedCidrs(request.cidrs()), environments, types,
                view.assetViews(scope.targets.stream().limit(PREVIEW_LIMIT).toList()), warnings);
    }

    @Transactional
    public BatchRunResult execute(BatchScopeRequest request) {
        Scope scope = resolve(request);
        if (scope.targets.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "No applicable online assets in the selected scope");
        int batchSize = bounded(request.batchSize(), settings.intValue("batchSize",20,1,500), 1, 500);
        int concurrency = bounded(request.concurrency(), Math.min(settings.intValue("batchConcurrency",10,1,200), batchSize), 1, Math.min(200, batchSize));
        double threshold = bounded(request.failureThreshold(), settings.intValue("autoRollbackThreshold",5,1,100), 0.1, 100.0);
        int totalBatches = (int) Math.ceil(scope.targets.size() / (double) batchSize);
        Set<EnvironmentType> environments = scope.targets.stream().map(Asset::getEnvironment).collect(Collectors.toCollection(LinkedHashSet::new));
        String environment = environments.size() == 1 ? environments.iterator().next().name() : "MIXED";
        String now = String.valueOf(System.currentTimeMillis());
        OrchestrationTemplate template = orchestration.resolveBoundTemplate("BATCH_PATCH");
        String cidrs = String.join(", ", normalizedCidrs(request.cidrs()));
        String planName = value(request.planName(), "CIDR batch patch");
        PatchDeployment deployment = deployments.save(PatchDeployment.builder()
                .deploymentNo("BDEP-" + now).taskId(null)
                .changeOrderId(scope.change == null ? null : scope.change.getId())
                .patchId(scope.patch.getId()).environment(environment)
                .ring(planName).status(DeploymentStatus.RUNNING).progress(1).targetCount(scope.targets.size())
                .selectionMode("CIDR").cidrScopes(cidrs).batchSize(batchSize).concurrency(concurrency)
                .failureThreshold(threshold).totalBatches(totalBatches)
                .scopeSummary(scope.targets.size()+" targets · "+totalBatches+" batches · concurrency "+concurrency+
                        " · window "+value(request.maintenanceWindow(), settings.value("maintenanceWindow", "not specified"))+
                        (scope.change == null ? "" : " · change "+scope.change.getChangeNo()))
                .startedAt(Instant.now()).build());
        OrchestrationRun run = runs.save(OrchestrationRun.builder()
                .runNo("BRUN-" + now).templateId(template.getId()).taskId(null).deploymentId(deployment.getId())
                .environment(environment).ring(planName+" · "+totalBatches+" batches")
                .status(RunStatus.RUNNING).currentStep(1).progress(1).startedAt(Instant.now()).build());
        deployment.setOrchestrationRunId(run.getId());
        deployments.save(deployment);

        List<DeploymentTarget> targetRows = new ArrayList<>();
        int maxRetries=settings.intValue("batchMaxRetries",2,0,10);
        for (int index = 0; index < scope.targets.size(); index++) {
            Asset asset = scope.targets.get(index);
            int batchNo = index / batchSize + 1;
            boolean first = batchNo == 1;
            targetRows.add(DeploymentTarget.builder().deploymentId(deployment.getId()).runId(run.getId())
                    .assetId(asset.getId()).batchNo(batchNo).status(first ? "RUNNING" : "WAITING")
                    .progress(first ? 1 : 0).startedAt(first ? Instant.now() : null)
                    .maxRetries(maxRetries)
                    .message(first ? "批次 1 · 前置检查" : "批次 " + batchNo + " · 等待执行").build());
        }
        deploymentTargets.saveAll(targetRows);

        List<OrchestrationRunStep> steps = new ArrayList<>();
        int order = 1;
        steps.add(step(run.getId(), order++, "SCOPE_LOCK", "锁定网段资产范围", "Lock CIDR asset scope", true));
        for (int batch = 1; batch <= totalBatches; batch++) {
            steps.add(step(run.getId(), order++, code(batch,"PRECHECK"), "批次 "+batch+" 前置检查", "Batch "+batch+" pre-check", false));
            steps.add(step(run.getId(), order++, code(batch,"INSTALL"), "批次 "+batch+" 安装补丁", "Batch "+batch+" install patch", false));
            steps.add(step(run.getId(), order++, code(batch,"VERIFY"), "批次 "+batch+" 健康检查", "Batch "+batch+" health verification", false));
        }
        steps.add(step(run.getId(), order, "EVIDENCE", "汇总结果与归档证据", "Aggregate results and archive evidence", false));
        runSteps.saveAll(steps);
        audit.log("BATCH_PATCH", deployment.getId(), "START",
                "按网段启动批量补丁："+scope.patch.getPatchId()+"，"+scope.targets.size()+"台资产，"+totalBatches+"个批次"+
                        (scope.change == null ? "" : "，变更单 "+scope.change.getChangeNo()),
                "CIDR batch patch started: "+scope.patch.getPatchId()+", "+scope.targets.size()+" assets in "+totalBatches+" batches"+
                        (scope.change == null ? "" : ", change "+scope.change.getChangeNo()),
                currentUser.name());
        return new BatchRunResult(run.getId(), run.getRunNo(), deployment.getId(), deployment.getDeploymentNo(), scope.targets.size(), totalBatches);
    }

    @Transactional
    public DeploymentTarget recordTargetResult(Long runId,Long targetId,String status,String resultCode,String message,String failureReason){
        DeploymentTarget target=deploymentTargets.findById(targetId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Deployment target not found"));
        if(!Objects.equals(target.getRunId(),runId))throw new ResponseStatusException(HttpStatus.CONFLICT,"Target does not belong to this run");
        String normalized=value(status,"").toUpperCase(Locale.ROOT);
        if(!Set.of("SUCCEEDED","FAILED").contains(normalized))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Target status must be SUCCEEDED or FAILED");
        target.setStatus(normalized);target.setResultCode(value(resultCode,null));target.setMessage(value(message,normalized));target.setFailureReason("FAILED".equals(normalized)?value(failureReason,message):null);
        target.setProgress("SUCCEEDED".equals(normalized)?100:Math.max(1,target.getProgress()==null?1:target.getProgress()));target.setCompletedAt(Instant.now());deploymentTargets.save(target);
        PatchDeployment deployment=deployments.findById(target.getDeploymentId()).orElseThrow();
        refreshCounts(deployment);
        if("FAILED".equals(normalized)){
            double ratio=deployment.getTargetCount()==null||deployment.getTargetCount()==0?100.0:deployment.getFailureCount()*100.0/deployment.getTargetCount();
            if(ratio>=Optional.ofNullable(deployment.getFailureThreshold()).orElse(5.0))pauseForFailure(deployment,runId,ratio);
        }
        audit.log("BATCH_TARGET",target.getId(),normalized,"批量补丁目标返回 "+normalized,"Batch patch target reported "+normalized,currentUser.name());
        return target;
    }

    @Transactional
    public DeploymentTarget retryTarget(Long runId,Long targetId){
        DeploymentTarget target=deploymentTargets.findById(targetId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Deployment target not found"));
        if(!Objects.equals(target.getRunId(),runId))throw new ResponseStatusException(HttpStatus.CONFLICT,"Target does not belong to this run");
        if(!"FAILED".equals(target.getStatus()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Only failed targets can be retried");
        int retries=Optional.ofNullable(target.getRetryCount()).orElse(0),max=Optional.ofNullable(target.getMaxRetries()).orElse(2);
        if(retries>=max)throw new ResponseStatusException(HttpStatus.CONFLICT,"Retry limit reached");
        target.setRetryCount(retries+1);target.setStatus("RUNNING");target.setProgress(1);target.setStartedAt(Instant.now());target.setCompletedAt(null);target.setFailureReason(null);target.setResultCode(null);target.setMessage("重试 "+(retries+1)+" / "+max);deploymentTargets.save(target);
        PatchDeployment deployment=deployments.findById(target.getDeploymentId()).orElseThrow();refreshCounts(deployment);
        OrchestrationRun run=runs.findById(runId).orElseThrow();if(run.getStatus()==RunStatus.PAUSED){run.setStatus(RunStatus.RUNNING);runs.save(run);}if(deployment.getStatus()==DeploymentStatus.PAUSED){deployment.setStatus(DeploymentStatus.RUNNING);deployments.save(deployment);}
        audit.log("BATCH_TARGET",target.getId(),"RETRY","重试批量补丁目标，第 "+(retries+1)+" 次","Retrying batch patch target, attempt "+(retries+1),currentUser.name());return target;
    }

    private void refreshCounts(PatchDeployment deployment){
        long success=deploymentTargets.countByDeploymentIdAndStatus(deployment.getId(),"SUCCEEDED"),failed=deploymentTargets.countByDeploymentIdAndStatus(deployment.getId(),"FAILED");
        deployment.setSuccessCount((int)success);deployment.setFailureCount((int)failed);deployments.save(deployment);
    }
    private void pauseForFailure(PatchDeployment deployment,Long runId,double ratio){
        deployment.setStatus(DeploymentStatus.PAUSED);deployments.save(deployment);OrchestrationRun run=runs.findById(runId).orElseThrow();run.setStatus(RunStatus.PAUSED);runs.save(run);
        for(DeploymentTarget row:deploymentTargets.findByRunIdOrderByAssetIdAsc(runId))if("WAITING".equals(row.getStatus())){row.setStatus("PAUSED");row.setMessage("失败率达到 "+String.format(Locale.ROOT,"%.1f",ratio)+"%，执行已暂停");deploymentTargets.save(row);}
        audit.log("BATCH_PATCH",deployment.getId(),"AUTO_PAUSE","失败率达到阈值，批量执行已暂停","Failure threshold reached; batch execution paused","System");
    }

    private OrchestrationRunStep step(Long runId, int order, String code, String zh, String en, boolean running) {
        return OrchestrationRunStep.builder().runId(runId).stepOrder(order).code(code).nameZh(zh).nameEn(en)
                .status(running ? RunStepStatus.RUNNING : RunStepStatus.WAITING)
                .startedAt(running ? Instant.now() : null).messageZh(running ? "正在执行" : "等待执行")
                .messageEn(running ? "Running" : "Waiting").build();
    }

    private String code(int batch, String action) { return "B"+String.format("%03d", batch)+"_"+action; }

    private Scope resolve(BatchScopeRequest request) {
        if (request == null || request.patchId() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Patch is required");
        Patch patch = patches.findById(request.patchId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patch not found"));
        List<Cidr> cidrs = parseCidrs(request.cidrs());
        Set<String> environments = normalizedSet(request.environments());
        Set<String> types = normalizedSet(request.assetTypes());
        Set<Long> excluded = request.excludedAssetIds() == null ? Set.of() : new HashSet<>(request.excludedAssetIds());
        String os = lower(request.osName());
        String service = lower(request.businessService());
        List<Asset> matched = assets.findByActiveTrueOrderByIpAddressAsc().stream()
                .filter(a -> cidrs.stream().anyMatch(c -> c.contains(a.getIpAddress())))
                .filter(a -> environments.isEmpty() || environments.contains(a.getEnvironment().name()))
                .filter(a -> types.isEmpty() || types.contains(value(a.getAssetType(), "UNKNOWN").toUpperCase(Locale.ROOT)))
                .filter(a -> os.isBlank() || lower(a.getOsName()).contains(os))
                .filter(a -> service.isBlank() || lower(a.getBusinessService()).contains(service))
                .toList();
        List<Asset> applicable = matched.stream().filter(a -> applicable(a, patch)).toList();
        long offline = applicable.stream().filter(a -> !"ONLINE".equalsIgnoreCase(a.getAgentStatus())).count();
        List<Asset> targets = applicable.stream()
                .filter(a -> !excluded.contains(a.getId()))
                .filter(a -> !request.onlineOnly() || "ONLINE".equalsIgnoreCase(a.getAgentStatus()))
                .toList();
        long excludedCount = applicable.stream().filter(a -> excluded.contains(a.getId())).count();
        boolean containsProduction = targets.stream().anyMatch(a -> a.getEnvironment() == EnvironmentType.PROD);
        ChangeWorkOrder change = null;
        if (containsProduction) {
            if (request.changeOrderId() == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "An approved change order is required for production assets");
            }
            change = changeOrders.findById(request.changeOrderId()).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "Change order not found"));
            if (change.getStatus() != ChangeStatus.APPROVED && change.getStatus() != ChangeStatus.IMPLEMENTING) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "The selected change order is not approved for implementation");
            }
            RemediationTask task = remediationTasks.findById(change.getRemediationTaskId()).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.CONFLICT, "The selected change order has no remediation task"));
            if (!Objects.equals(task.getPatchId(), patch.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "The change order does not authorize the selected patch");
            }
            if (change.getMaintenanceStart() == null || change.getMaintenanceEnd() == null ||
                    !change.getMaintenanceEnd().isAfter(change.getMaintenanceStart())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "The selected change order has no valid maintenance window");
            }
        }
        return new Scope(patch, matched.size(), applicable.size(), excludedCount, offline, targets, change);
    }

    private boolean applicable(Asset asset, Patch patch) {
        String product = lower(patch.getProduct()).replace("apache ", "").trim();
        String installed = lower(asset.getInstalledProducts()) + " " + lower(asset.getOsName());
        if (product.isBlank()) return true;
        if (product.contains("windows server")) return installed.contains("windows server");
        if (product.contains("red hat") || product.equals("rhel")) return installed.contains("red hat") || installed.contains("rocky");
        if (product.contains("php cgi")) return installed.contains("php ");
        if (product.contains("spring framework")) return installed.contains("spring framework") || installed.contains("spring boot");
        if (product.equals("mysql")) return installed.contains("mysql");
        return installed.contains(product) || (product.equals("tomcat") && installed.contains("apache tomcat"));
    }

    private List<Cidr> parseCidrs(List<String> raw) {
        if (raw == null || raw.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one CIDR is required");
        try { return normalizedCidrs(raw).stream().map(Cidr::parse).toList(); }
        catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage()); }
    }

    private List<String> normalizedCidrs(List<String> raw) {
        if (raw == null) return List.of();
        return raw.stream().filter(Objects::nonNull).flatMap(v -> Arrays.stream(v.split("[,\\s]+")))
                .map(String::trim).filter(v -> !v.isBlank()).distinct().toList();
    }

    private Set<String> normalizedSet(List<String> values) {
        if (values == null) return Set.of();
        return values.stream().filter(Objects::nonNull).map(v -> v.trim().toUpperCase(Locale.ROOT))
                .filter(v -> !v.isBlank() && !"ALL".equals(v)).collect(Collectors.toSet());
    }

    private static String value(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }
    private static String lower(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT); }
    private static int bounded(Integer value, int fallback, int min, int max) { return Math.max(min, Math.min(max, value == null ? fallback : value)); }
    private static double bounded(Double value, double fallback, double min, double max) { return Math.max(min, Math.min(max, value == null ? fallback : value)); }

    private record Scope(Patch patch, long matchedCount, long applicableCount, long excludedCount,
                         long offlineCount, List<Asset> targets, ChangeWorkOrder change) {}

    private record Cidr(long network, long mask) {
        static Cidr parse(String input) {
            String[] parts = input.split("/");
            if (parts.length != 2) throw new IllegalArgumentException("Invalid CIDR: " + input);
            int prefix = Integer.parseInt(parts[1]);
            if (prefix < 0 || prefix > 32) throw new IllegalArgumentException("Invalid CIDR prefix: " + input);
            long mask = prefix == 0 ? 0 : (0xffffffffL << (32 - prefix)) & 0xffffffffL;
            long address = ipv4(parts[0]);
            return new Cidr(address & mask, mask);
        }
        boolean contains(String ip) {
            try { return (ipv4(ip) & mask) == network; }
            catch (Exception ignored) { return false; }
        }
        private static long ipv4(String input) {
            String[] octets = input == null ? new String[0] : input.trim().split("\\.");
            if (octets.length != 4) throw new IllegalArgumentException("Invalid IPv4 address: " + input);
            long result = 0;
            for (String octet : octets) {
                int value = Integer.parseInt(octet);
                if (value < 0 || value > 255) throw new IllegalArgumentException("Invalid IPv4 address: " + input);
                result = (result << 8) | value;
            }
            return result;
        }
    }
}
