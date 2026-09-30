package com.gazellio.platform.service;

import com.gazellio.platform.dto.ApiDtos.*;
import com.gazellio.platform.model.*;
import com.gazellio.platform.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.gazellio.platform.model.Enums.FindingStatus.*;

/** Converts persistence entities into API views using batched relation lookups. */
@Service
@RequiredArgsConstructor
public class ViewService {
    private static final List<Enums.FindingStatus> CLOSED_FINDING_STATUSES =
            List.of(RESOLVED, FALSE_POSITIVE, EXEMPTED);

    private final AssetRepository assets;
    private final VulnerabilityDefinitionRepository vulns;
    private final FindingRepository findings;
    private final ScanJobRepository scans;
    private final PatchRepository patches;
    private final PatchCveRepository patchCves;
    private final RemediationTaskRepository tasks;
    private final SecurityIncidentRepository incidents;
    private final ChangeWorkOrderRepository changeOrders;
    private final ApprovalRequestRepository approvals;
    private final ApprovalStepRepository approvalSteps;
    private final OrchestrationTemplateRepository templates;
    private final OrchestrationTemplateStepRepository templateSteps;
    private final OrchestrationRunStepRepository runSteps;
    private final DeploymentTargetRepository deploymentTargets;

    private static String s(Object value) { return value == null ? null : String.valueOf(value); }

    private static <K, V> Map<K, V> index(Collection<V> values, Function<V, K> key) {
        return values.stream().collect(Collectors.toMap(key, Function.identity(), (left, right) -> left));
    }

    private static <K, V> Map<K, List<V>> group(Collection<V> values, Function<V, K> key) {
        return values.stream().collect(Collectors.groupingBy(key, LinkedHashMap::new, Collectors.toList()));
    }

    private static PatchCandidateView patchCandidate(Patch p) {
        return new PatchCandidateView(p.getId(), p.getPatchId(), p.getTitleZh(), p.getTitleEn(), p.getVersion(),
                p.getSignatureStatus(), p.isRebootRequired(), p.getStatus());
    }

    public List<AssetView> assetViews(List<Asset> rows) {
        if (rows.isEmpty()) return List.of();
        Map<Long, Long> openCounts = findings.countOpenByAsset(CLOSED_FINDING_STATUSES).stream()
                .collect(Collectors.toMap(FindingRepository.AssetOpenCount::getAssetId,
                        FindingRepository.AssetOpenCount::getTotal));
        return rows.stream().map(a -> new AssetView(
                a.getId(), a.getAssetCode(), a.getName(), a.getHostname(), a.getIpAddress(), a.getNetworkSegment(),
                a.getAssetType(), a.getZone(), Boolean.TRUE.equals(a.getInternetExposed()), a.getOsName(), a.getOsVersion(),
                s(a.getEnvironment()), a.getBusinessService(), a.getOwnerId(), a.getOwnerName(), a.getCriticality(),
                a.getAgentStatus(), a.getPatchBaseline(), a.getInstalledProducts(), a.getMaintenanceWindow(),
                a.getLastSeenAt(), openCounts.getOrDefault(a.getId(), 0L)
        )).toList();
    }

    public AssetView asset(Asset row) { return assetViews(List.of(row)).getFirst(); }

    public List<VulnerabilityView> vulnerabilityViews(List<VulnerabilityDefinition> rows) {
        if (rows.isEmpty()) return List.of();
        Set<String> cveIds = rows.stream().map(VulnerabilityDefinition::getCveId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, Long> affected = findings.countAffectedByCve(CLOSED_FINDING_STATUSES).stream()
                .collect(Collectors.toMap(FindingRepository.CveAffectedCount::getCveId,
                        FindingRepository.CveAffectedCount::getTotal));
        List<PatchCve> links = patchCves.findByCveIdIn(cveIds);
        Set<Long> patchIds = links.stream().map(PatchCve::getPatchId).collect(Collectors.toSet());
        Map<Long, Patch> patchById = patchIds.isEmpty() ? Map.of() : index(patches.findAllById(patchIds), Patch::getId);
        Map<String, List<String>> patchCodesByCve = new HashMap<>();
        Map<String, List<PatchCandidateView>> patchCandidatesByCve = new HashMap<>();
        for (PatchCve link : links) {
            Patch patch = patchById.get(link.getPatchId());
            if (patch != null) {
                patchCodesByCve.computeIfAbsent(link.getCveId(), ignored -> new ArrayList<>()).add(patch.getPatchId());
                patchCandidatesByCve.computeIfAbsent(link.getCveId(), ignored -> new ArrayList<>()).add(patchCandidate(patch));
            }
        }
        return rows.stream().map(v -> {
            List<PatchCandidateView> candidates=patchCandidatesByCve.getOrDefault(v.getCveId(), List.of());
            String fixed=candidates.isEmpty()?null:candidates.getFirst().version();
            String affectedRange=fixed==null?"Refer to the locally archived vendor applicability rule":"< "+fixed;
            String product=v.getProduct()==null?"component":v.getProduct();
            String remediationZh=candidates.isEmpty()
                    ?"暂无已批准补丁；建议先限制暴露面、启用访问控制并进入风险接受审批。"
                    :"安装本地补丁库中已校验的 "+candidates.getFirst().patchId()+"，完成应用健康检查和漏洞定向复测后关闭。";
            String remediationEn=candidates.isEmpty()
                    ?"No approved package is available. Restrict exposure, enforce access controls, and route the risk through exception approval."
                    :"Install verified package "+candidates.getFirst().patchId()+" from the internal patch library, validate application health, and close only after a targeted retest.";
            String productLower=product.toLowerCase(Locale.ROOT);
            boolean virtualPatch=List.of("tomcat","apache","nginx","jetty","struts","jira","php")
                    .stream().anyMatch(productLower::contains);
            String mitigationZh=virtualPatch
                    ?"补丁窗口前可在WAF部署针对该CVE的请求特征阻断规则，限制管理端口和非必要访问，并开启告警监测。"
                    :"补丁窗口前建议限制漏洞服务的网络暴露，通过ACL、访问控制或临时停用非必要服务降低风险。";
            String mitigationEn=virtualPatch
                    ?"Before the patch window, deploy a CVE-specific request blocking rule on the WAF, restrict administrative endpoints and unnecessary access, and enable alerting."
                    :"Before the patch window, restrict network exposure with ACLs and access controls, or temporarily disable the non-essential vulnerable service.";
            return new VulnerabilityView(
                    v.getCveId(), v.getTitleZh(), v.getTitleEn(), v.getVendor(), v.getProduct(),
                    v.getDescriptionZh(), v.getDescriptionEn(), v.getCvss(), s(v.getSeverity()), v.isKev(),
                    v.isRansomwareKnown(), !candidates.isEmpty(), v.getReferenceUrl(), s(v.getPublishedDate()),
                    s(v.getKevDueDate()), affected.getOrDefault(v.getCveId(), 0L),
                    patchCodesByCve.getOrDefault(v.getCveId(), List.of()), candidates,
                    "GZ-ADV-"+v.getCveId(),"NETWORK","LOW","NONE",affectedRange,
                    "通过认证扫描读取 "+product+" 的软件包、进程或服务指纹，核对版本区间并执行定向规则验证。",
                    "Use authenticated package, process, or service fingerprint collection for "+product+", compare the observed version with the affected range, and run the targeted detection rule.",
                    remediationZh,remediationEn,mitigationZh,mitigationEn,virtualPatch,
                    virtualPatch?"建议规则模板：匹配异常请求路径、参数和协议特征；先以观察模式发布，验证无误报后切换阻断。":"该类组件不适合使用WAF虚拟补丁，应使用网络隔离、ACL或主机级缓解措施。",
                    virtualPatch?"Suggested template: match abnormal request paths, parameters, and protocol characteristics; deploy in monitor mode first and switch to blocking after false-positive validation.":"This component is not suitable for a WAF virtual patch. Use network isolation, ACLs, or host-level mitigations."
            );
        }).toList();
    }

    public VulnerabilityView vulnerability(VulnerabilityDefinition row) { return vulnerabilityViews(List.of(row)).getFirst(); }

    public List<FindingView> findingViews(List<Finding> rows) {
        if (rows.isEmpty()) return List.of();
        Set<String> cveIds = rows.stream().map(Finding::getCveId).collect(Collectors.toSet());
        Set<Long> assetIds = rows.stream().map(Finding::getAssetId).collect(Collectors.toSet());
        Set<Long> scanIds = rows.stream().map(Finding::getScanJobId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, VulnerabilityDefinition> vulnerabilityById = index(vulns.findAllById(cveIds), VulnerabilityDefinition::getCveId);
        Map<Long, Asset> assetById = index(assets.findAllById(assetIds), Asset::getId);
        Map<Long, ScanJob> scanById = scanIds.isEmpty() ? Map.of() : index(scans.findAllById(scanIds), ScanJob::getId);

        List<PatchCve> links = patchCves.findByCveIdIn(cveIds);
        Set<Long> patchIds = links.stream().map(PatchCve::getPatchId).collect(Collectors.toSet());
        Map<Long, Patch> patchById = patchIds.isEmpty() ? Map.of() : index(patches.findAllById(patchIds), Patch::getId);
        Map<String, List<String>> patchCodesByCve = new HashMap<>();
        Map<String, List<PatchCandidateView>> patchCandidatesByCve = new HashMap<>();
        for (PatchCve link : links) {
            Patch patch = patchById.get(link.getPatchId());
            if (patch != null) {
                patchCodesByCve.computeIfAbsent(link.getCveId(), ignored -> new ArrayList<>()).add(patch.getPatchId());
                patchCandidatesByCve.computeIfAbsent(link.getCveId(), ignored -> new ArrayList<>()).add(patchCandidate(patch));
            }
        }
        return rows.stream().map(f -> {
            VulnerabilityDefinition v = vulnerabilityById.get(f.getCveId());
            Asset a = assetById.get(f.getAssetId());
            ScanJob scan = f.getScanJobId() == null ? null : scanById.get(f.getScanJobId());
            List<String> reasons=new ArrayList<>();
            if(v!=null&&v.isKev())reasons.add("KNOWN_EXPLOITED");
            if(v!=null&&v.getSeverity()==Enums.Severity.CRITICAL)reasons.add("CRITICAL_SEVERITY");
            if(a!=null&&Boolean.TRUE.equals(a.getInternetExposed()))reasons.add("INTERNET_EXPOSED");
            if(a!=null&&a.getCriticality()!=null&&a.getCriticality()>=5)reasons.add("CORE_ASSET");
            if(f.getExemptionExpiresAt()!=null)reasons.add("EXCEPTION_ACTIVE");
            return new FindingView(
                    f.getId(), f.getCveId(), v == null ? f.getCveId() : v.getTitleZh(),
                    v == null ? f.getCveId() : v.getTitleEn(), v == null ? null : v.getCvss(),
                    v == null ? null : s(v.getSeverity()), v != null && v.isKev(), f.getAssetId(),
                    a == null ? null : a.getAssetCode(), a == null ? null : a.getName(),
                    a == null ? null : s(a.getEnvironment()), a == null ? null : a.getBusinessService(),
                    f.getOwnerName(), a==null?null:a.getCriticality(), a!=null&&Boolean.TRUE.equals(a.getInternetExposed()), reasons,
                    s(f.getStatus()), f.getRiskScore(), f.getOccurrences(), f.getScanJobId(),
                    scan == null ? null : scan.getJobNo(), f.getRemediationTaskId(), s(f.getFirstSeenAt()),
                    s(f.getLastSeenAt()), f.getEvidence(), f.getFalsePositiveReason(), f.getExemptionReason(),
                    s(f.getExemptionExpiresAt()), f.getSecurityIncidentId(),
                    patchCodesByCve.getOrDefault(f.getCveId(), List.of()),
                    patchCandidatesByCve.getOrDefault(f.getCveId(), List.of())
            );
        }).toList();
    }

    public FindingView finding(Finding row) { return findingViews(List.of(row)).getFirst(); }

    public ScanJobView scan(ScanJob x) {
        return new ScanJobView(x.getId(), x.getJobNo(), x.getName(), x.getScanType(), x.getTargetType(),
                x.getTargetValue(), x.getCredentialType(), x.getTargetCve(), s(x.getStatus()), x.getProgress(),
                x.getFindingsCount(), x.getRequestedByName(), x.getRemediationTaskId(), x.getAutomationRunId(),
                s(x.getCreatedAt()), s(x.getStartedAt()), s(x.getCompletedAt()), x.getErrorMessage());
    }

    public AgentView agent(ScanAgent x) {
        return new AgentView(x.getId(), x.getAgentKey(), x.getHostname(), x.getIpAddress(), x.getOsName(),
                x.getVersion(), s(x.getStatus()), x.getAssetId(), s(x.getLastHeartbeatAt()));
    }

    public PatchServerView patchServer(PatchServer p) {
        return new PatchServerView(p.getId(), p.getName(), p.getAddress(), p.getRegion(), p.getOsSupport(),
                p.getStatus(), s(p.getLastSyncAt()), p.getCapacityGb(), p.getUsedGb());
    }

    public List<PatchView> patchViews(List<Patch> rows) {
        if (rows.isEmpty()) return List.of();
        Set<Long> ids = rows.stream().map(Patch::getId).collect(Collectors.toSet());
        Map<Long, List<PatchCve>> linksByPatch = group(patchCves.findByPatchIdIn(ids), PatchCve::getPatchId);
        Map<Long, Long> affected = patchCves.countAffectedAssets(ids, CLOSED_FINDING_STATUSES).stream()
                .collect(Collectors.toMap(PatchCveRepository.PatchAffectedCount::getPatchId,
                        PatchCveRepository.PatchAffectedCount::getTotal));
        Set<String> cveIds = linksByPatch.values().stream().flatMap(Collection::stream)
                .map(PatchCve::getCveId).collect(Collectors.toSet());
        Map<String, VulnerabilityDefinition> vulnerabilityById = cveIds.isEmpty()
                ? Map.of() : index(vulns.findAllById(cveIds), VulnerabilityDefinition::getCveId);
        return rows.stream().map(p -> new PatchView(
                p.getId(), p.getPatchId(), p.getVendor(), p.getProduct(), p.getVersion(), p.getTitleZh(),
                p.getTitleEn(), p.getDownloadUrl(), p.getChecksum(), p.getSizeMb(), p.isRebootRequired(),
                p.getStatus(), p.getSource(), s(p.getPublishedDate()),
                linksByPatch.getOrDefault(p.getId(), List.of()).stream().map(PatchCve::getCveId).toList(),
                affected.getOrDefault(p.getId(), 0L), p.getApplicabilityRule(), p.getApplicabilityRuleEn(), p.getSignatureStatus(),
                p.getSupersedes(), p.getReleaseNotesZh(), p.getReleaseNotesEn(), p.getSignatureIssuer(),
                p.getSignatureFingerprint(), s(p.getIntegrityVerifiedAt()), p.getVendorAdvisoryUrl(),
                p.getPrerequisites(), p.getPrerequisitesEn(), p.getInstallCommand(), p.getUninstallCommand(), p.getTestEvidence(),
                p.getKnownIssues(), p.getKnownIssuesEn(), linksByPatch.getOrDefault(p.getId(), List.of()).stream().map(link -> {
                    VulnerabilityDefinition v = vulnerabilityById.get(link.getCveId());
                    String fixed = p.getVersion() == null ? p.getPatchId() : p.getVersion();
                    String product = v == null ? p.getProduct() : v.getProduct();
                    String titleZh = v == null ? link.getCveId() : v.getTitleZh();
                    String titleEn = v == null ? link.getCveId() : v.getTitleEn();
                    return new PatchCveEvidenceView(link.getCveId(), titleZh, titleEn, product,
                            v == null ? null : v.getCvss(), v == null ? null : s(v.getSeverity()),
                            "< " + fixed, fixed, "GZ-" + link.getCveId(),
                            "漏洞目录与补丁清单已建立映射；安装后版本须达到 " + fixed + "，并以定向复测未检出作为关闭依据。",
                            "Catalog-to-patch mapping verified. The installed version must reach " + fixed
                                    + " and the targeted retest must return not detected before closure.");
                }).toList()
        )).toList();
    }

    public PatchView patch(Patch row) { return patchViews(List.of(row)).getFirst(); }

    public List<TaskView> taskViews(List<RemediationTask> rows) {
        if (rows.isEmpty()) return List.of();
        Set<Long> findingIds = rows.stream().map(RemediationTask::getFindingId).collect(Collectors.toSet());
        Set<Long> assetIds = rows.stream().map(RemediationTask::getAssetId).collect(Collectors.toSet());
        Set<Long> patchIds = rows.stream().map(RemediationTask::getPatchId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Finding> findingById = index(findings.findAllById(findingIds), Finding::getId);
        Set<String> cveIds = findingById.values().stream().map(Finding::getCveId).collect(Collectors.toSet());
        Map<String, VulnerabilityDefinition> vulnerabilityById = cveIds.isEmpty() ? Map.of() : index(vulns.findAllById(cveIds), VulnerabilityDefinition::getCveId);
        Map<Long, Asset> assetById = index(assets.findAllById(assetIds), Asset::getId);
        Map<Long, Patch> patchById = patchIds.isEmpty() ? Map.of() : index(patches.findAllById(patchIds), Patch::getId);
        return rows.stream().map(t -> {
            Finding finding = findingById.get(t.getFindingId());
            VulnerabilityDefinition v = finding == null ? null : vulnerabilityById.get(finding.getCveId());
            Asset asset = assetById.get(t.getAssetId());
            Patch patch = t.getPatchId() == null ? null : patchById.get(t.getPatchId());
            return new TaskView(
                    t.getId(), t.getTaskNo(), t.getFindingId(), finding == null ? null : finding.getCveId(),
                    v == null ? null : v.getTitleZh(), v == null ? null : v.getTitleEn(), t.getAssetId(),
                    asset == null ? null : asset.getAssetCode(), asset == null ? null : asset.getName(),
                    asset == null ? null : s(asset.getEnvironment()), asset == null ? null : asset.getBusinessService(),
                    t.getPatchId(), patch == null ? null : patch.getPatchId(), t.getOwnerName(), t.getPriority(),
                    s(t.getStage()), s(t.getStatus()), s(t.getChangeType()), t.getApprovalId(), t.getLatestRunId(),
                    t.getSecurityIncidentId(), t.getChangeOrderId(), s(t.getDueAt()), s(t.getCreatedAt()), s(t.getUpdatedAt()),
                    t.getLastRetestMode(), t.getLastRetestResult(), t.getLastRetestComment(),
                    t.getLastRetestedBy(), s(t.getLastRetestedAt())
            );
        }).toList();
    }

    public TaskView task(RemediationTask row) { return taskViews(List.of(row)).getFirst(); }

    public List<ApprovalView> approvalViews(List<ApprovalRequest> rows) {
        if (rows.isEmpty()) return List.of();
        Set<Long> taskIds = rows.stream().map(ApprovalRequest::getTaskId).collect(Collectors.toSet());
        Set<Long> approvalIds = rows.stream().map(ApprovalRequest::getId).collect(Collectors.toSet());
        Set<Long> changeIds = rows.stream().map(ApprovalRequest::getChangeOrderId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, RemediationTask> taskById = index(tasks.findAllById(taskIds), RemediationTask::getId);
        Set<Long> findingIds = taskById.values().stream().map(RemediationTask::getFindingId).collect(Collectors.toSet());
        Set<Long> assetIds = taskById.values().stream().map(RemediationTask::getAssetId).collect(Collectors.toSet());
        Map<Long, Finding> findingById = findingIds.isEmpty() ? Map.of() : index(findings.findAllById(findingIds), Finding::getId);
        Map<Long, Asset> assetById = assetIds.isEmpty() ? Map.of() : index(assets.findAllById(assetIds), Asset::getId);
        Map<Long, ChangeWorkOrder> changeById = changeIds.isEmpty() ? Map.of() : index(changeOrders.findAllById(changeIds), ChangeWorkOrder::getId);
        Map<Long, List<ApprovalStep>> stepsByApproval = group(
                approvalSteps.findByApprovalIdInOrderByApprovalIdAscStepOrderAsc(approvalIds), ApprovalStep::getApprovalId);
        return rows.stream().map(a -> {
            RemediationTask task = taskById.get(a.getTaskId());
            Finding finding = task == null ? null : findingById.get(task.getFindingId());
            Asset asset = task == null ? null : assetById.get(task.getAssetId());
            ChangeWorkOrder change = a.getChangeOrderId() == null ? null : changeById.get(a.getChangeOrderId());
            List<ApprovalStepView> steps = stepsByApproval.getOrDefault(a.getId(), List.of()).stream()
                    .map(x -> new ApprovalStepView(x.getId(), x.getStepOrder(), x.getRoleNameZh(), x.getRoleNameEn(),
                            x.getApproverName(), s(x.getStatus()), x.getComment(), s(x.getActedAt()))).toList();
            return new ApprovalView(
                    a.getId(), a.getApprovalNo(), a.getTaskId(), task == null ? null : task.getTaskNo(),
                    finding == null ? null : finding.getCveId(), asset == null ? null : asset.getName(),
                    s(a.getChangeType()), s(a.getStatus()), a.getCurrentStep(), a.getRequestedByName(),
                    s(a.getSubmittedAt()), s(a.getCompletedAt()), a.getReason(), a.getRollbackPlan(),
                    a.getChangeOrderId(), change == null ? null : change.getChangeNo(), steps
            );
        }).toList();
    }

    public ApprovalView approval(ApprovalRequest row) { return approvalViews(List.of(row)).getFirst(); }

    public List<SecurityIncidentView> incidentViews(List<SecurityIncident> rows) {
        if (rows.isEmpty()) return List.of();
        Set<Long> findingIds = rows.stream().map(SecurityIncident::getFindingId).collect(Collectors.toSet());
        Set<Long> assetIds = rows.stream().map(SecurityIncident::getAssetId).collect(Collectors.toSet());
        Set<Long> taskIds = rows.stream().map(SecurityIncident::getRemediationTaskId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> changeIds = rows.stream().map(SecurityIncident::getChangeOrderId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Finding> findingById = index(findings.findAllById(findingIds), Finding::getId);
        Set<String> cveIds = findingById.values().stream().map(Finding::getCveId).collect(Collectors.toSet());
        Map<String, VulnerabilityDefinition> vulnerabilityById = index(vulns.findAllById(cveIds), VulnerabilityDefinition::getCveId);
        Map<Long, Asset> assetById = index(assets.findAllById(assetIds), Asset::getId);
        Map<Long, RemediationTask> taskById = taskIds.isEmpty() ? Map.of() : index(tasks.findAllById(taskIds), RemediationTask::getId);
        Map<Long, ChangeWorkOrder> changeById = changeIds.isEmpty() ? Map.of() : index(changeOrders.findAllById(changeIds), ChangeWorkOrder::getId);
        List<PatchCve> links = patchCves.findByCveIdIn(cveIds);
        Set<Long> patchIds = links.stream().map(PatchCve::getPatchId).collect(Collectors.toSet());
        Map<Long, Patch> patchById = patchIds.isEmpty() ? Map.of() : index(patches.findAllById(patchIds), Patch::getId);
        Map<String, List<PatchCandidateView>> candidatesByCve = new HashMap<>();
        for (PatchCve link : links) {
            Patch patch = patchById.get(link.getPatchId());
            if (patch != null) candidatesByCve.computeIfAbsent(link.getCveId(), ignored -> new ArrayList<>()).add(patchCandidate(patch));
        }
        return rows.stream().map(i -> {
            Finding finding = findingById.get(i.getFindingId());
            VulnerabilityDefinition vulnerability = finding == null ? null : vulnerabilityById.get(finding.getCveId());
            Asset asset = assetById.get(i.getAssetId());
            RemediationTask task = i.getRemediationTaskId() == null ? null : taskById.get(i.getRemediationTaskId());
            ChangeWorkOrder change = i.getChangeOrderId() == null ? null : changeById.get(i.getChangeOrderId());
            String cve = finding == null ? null : finding.getCveId();
            return new SecurityIncidentView(
                    i.getId(), i.getIncidentNo(), i.getFindingId(), cve,
                    vulnerability == null ? cve : vulnerability.getTitleZh(),
                    vulnerability == null ? cve : vulnerability.getTitleEn(),
                    vulnerability == null ? null : s(vulnerability.getSeverity()),
                    vulnerability != null && vulnerability.isKev(), i.getAssetId(),
                    asset == null ? null : asset.getAssetCode(), asset == null ? null : asset.getName(),
                    asset == null ? null : s(asset.getEnvironment()), asset == null ? null : asset.getBusinessService(),
                    i.getPriority(), s(i.getStatus()), i.getOwnerId(), i.getOwnerName(), i.getRemediationTaskId(),
                    task == null ? null : task.getTaskNo(), i.getChangeOrderId(), change == null ? null : change.getChangeNo(),
                    s(i.getDueAt()), i.getSyncStatus(), i.getExternalTicketNo(), i.getDecisionReason(),
                    s(i.getCreatedAt()), s(i.getUpdatedAt()), candidatesByCve.getOrDefault(cve, List.of())
            );
        }).toList();
    }

    public SecurityIncidentView incident(SecurityIncident row) { return incidentViews(List.of(row)).getFirst(); }

    public List<ChangeWorkOrderView> changeViews(List<ChangeWorkOrder> rows) {
        if (rows.isEmpty()) return List.of();
        Set<Long> incidentIds = rows.stream().map(ChangeWorkOrder::getIncidentId).collect(Collectors.toSet());
        Set<Long> taskIds = rows.stream().map(ChangeWorkOrder::getRemediationTaskId).collect(Collectors.toSet());
        Set<Long> approvalIds = rows.stream().map(ChangeWorkOrder::getApprovalId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, SecurityIncident> incidentById = index(incidents.findAllById(incidentIds), SecurityIncident::getId);
        Map<Long, RemediationTask> taskById = index(tasks.findAllById(taskIds), RemediationTask::getId);
        Map<Long, ApprovalRequest> approvalById = approvalIds.isEmpty() ? Map.of() : index(approvals.findAllById(approvalIds), ApprovalRequest::getId);
        Set<Long> findingIds = incidentById.values().stream().map(SecurityIncident::getFindingId).collect(Collectors.toSet());
        Set<Long> assetIds = incidentById.values().stream().map(SecurityIncident::getAssetId).collect(Collectors.toSet());
        Set<Long> patchIds = taskById.values().stream().map(RemediationTask::getPatchId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Finding> findingById = index(findings.findAllById(findingIds), Finding::getId);
        Map<Long, Asset> assetById = index(assets.findAllById(assetIds), Asset::getId);
        Map<Long, Patch> patchById = patchIds.isEmpty() ? Map.of() : index(patches.findAllById(patchIds), Patch::getId);
        return rows.stream().map(c -> {
            SecurityIncident incident = incidentById.get(c.getIncidentId());
            RemediationTask task = taskById.get(c.getRemediationTaskId());
            ApprovalRequest approval = c.getApprovalId() == null ? null : approvalById.get(c.getApprovalId());
            Finding finding = incident == null ? null : findingById.get(incident.getFindingId());
            Asset asset = incident == null ? null : assetById.get(incident.getAssetId());
            Patch patch = task == null || task.getPatchId() == null ? null : patchById.get(task.getPatchId());
            return new ChangeWorkOrderView(
                    c.getId(), c.getChangeNo(), c.getIncidentId(), incident == null ? null : incident.getIncidentNo(),
                    c.getRemediationTaskId(), task == null ? null : task.getTaskNo(), c.getApprovalId(),
                    approval == null ? null : approval.getApprovalNo(), s(c.getChangeType()), s(c.getStatus()),
                    c.getSummary(), finding == null ? null : finding.getCveId(), asset == null ? null : asset.getName(),
                    asset == null ? null : asset.getBusinessService(), task == null ? null : task.getPatchId(),
                    patch == null ? null : patch.getPatchId(), task == null ? null : task.getLatestRunId(),
                    c.getRiskAssessment(), c.getImplementationPlan(), c.getRollbackPlan(),
                    s(c.getMaintenanceStart()), s(c.getMaintenanceEnd()), c.getSyncStatus(), c.getExternalChangeNo(),
                    s(c.getCreatedAt()), s(c.getUpdatedAt()), s(c.getClosedAt())
            );
        }).toList();
    }

    public ChangeWorkOrderView change(ChangeWorkOrder row) { return changeViews(List.of(row)).getFirst(); }

    public List<TemplateView> templateViews(List<OrchestrationTemplate> rows) {
        if (rows.isEmpty()) return List.of();
        Set<Long> ids = rows.stream().map(OrchestrationTemplate::getId).collect(Collectors.toSet());
        Map<Long, List<OrchestrationTemplateStep>> stepsByTemplate = group(
                templateSteps.findByTemplateIdInOrderByTemplateIdAscStepOrderAsc(ids), OrchestrationTemplateStep::getTemplateId);
        return rows.stream().map(t -> new TemplateView(
                t.getId(), t.getCode(), t.getNameZh(), t.getNameEn(), t.getType(), t.isEnabled(), t.getVersion(),
                stepsByTemplate.getOrDefault(t.getId(), List.of()).stream()
                        .map(x -> new TemplateStepView(x.getId(), x.getStepOrder(), x.getCode(), x.getNameZh(),
                                x.getNameEn(), x.getStepType(), x.isRollbackPoint())).toList()
        )).toList();
    }

    public TemplateView template(OrchestrationTemplate row) { return templateViews(List.of(row)).getFirst(); }

    public List<RunView> runViews(List<OrchestrationRun> rows) {
        if (rows.isEmpty()) return List.of();
        Set<Long> templateIds = rows.stream().map(OrchestrationRun::getTemplateId).collect(Collectors.toSet());
        Set<Long> taskIds = rows.stream().map(OrchestrationRun::getTaskId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> runIds = rows.stream().map(OrchestrationRun::getId).collect(Collectors.toSet());
        Set<Long> deploymentIds = rows.stream().map(OrchestrationRun::getDeploymentId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, OrchestrationTemplate> templateById = index(templates.findAllById(templateIds), OrchestrationTemplate::getId);
        Map<Long, RemediationTask> taskById = taskIds.isEmpty() ? Map.of() : index(tasks.findAllById(taskIds), RemediationTask::getId);
        Map<Long, List<OrchestrationRunStep>> stepsByRun = group(
                runSteps.findByRunIdInOrderByRunIdAscStepOrderAsc(runIds), OrchestrationRunStep::getRunId);
        Map<Long, List<DeploymentTargetView>> targetsByDeployment = deploymentTargetViews(deploymentIds);
        Map<Long, List<DeploymentTargetView>> targetsByRun = runTargetViews(runIds);
        return rows.stream().map(r -> {
            OrchestrationTemplate template = templateById.get(r.getTemplateId());
            RemediationTask task = r.getTaskId() == null ? null : taskById.get(r.getTaskId());
            List<RunStepView> steps = stepsByRun.getOrDefault(r.getId(), List.of()).stream()
                    .map(x -> new RunStepView(x.getId(), x.getStepOrder(), x.getCode(), x.getNameZh(), x.getNameEn(),
                            s(x.getStatus()), s(x.getStartedAt()), s(x.getCompletedAt()), x.getMessageZh(), x.getMessageEn()))
                    .toList();
            List<DeploymentTargetView> runTargets = targetsByRun.get(r.getId());
            if (runTargets == null) {
                runTargets = r.getDeploymentId() == null
                        ? List.of()
                        : targetsByDeployment.getOrDefault(r.getDeploymentId(), List.of());
            }
            return new RunView(
                    r.getId(), r.getRunNo(), r.getTemplateId(), template == null ? null : template.getCode(),
                    template == null ? null : template.getNameZh(), template == null ? null : template.getNameEn(),
                    r.getTaskId(), task == null ? null : task.getTaskNo(), r.getDeploymentId(), r.getEnvironment(),
                    r.getRing(), s(r.getStatus()), r.getCurrentStep(), r.getProgress(), s(r.getCreatedAt()),
                    s(r.getStartedAt()), s(r.getCompletedAt()), r.getFailureReason(), steps, runTargets
            );
        }).toList();
    }

    public RunView run(OrchestrationRun row) { return runViews(List.of(row)).getFirst(); }

    public List<DeploymentView> deploymentViews(List<PatchDeployment> rows) {
        if (rows.isEmpty()) return List.of();
        Set<Long> taskIds = rows.stream().map(PatchDeployment::getTaskId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> patchIds = rows.stream().map(PatchDeployment::getPatchId).collect(Collectors.toSet());
        Set<Long> deploymentIds = rows.stream().map(PatchDeployment::getId).collect(Collectors.toSet());
        Map<Long, RemediationTask> taskById = taskIds.isEmpty()?Map.of():index(tasks.findAllById(taskIds), RemediationTask::getId);
        Map<Long, Patch> patchById = index(patches.findAllById(patchIds), Patch::getId);
        Map<Long, List<DeploymentTargetView>> targetsByDeployment = deploymentTargetViews(deploymentIds);
        return rows.stream().map(d -> {
            RemediationTask task = taskById.get(d.getTaskId());
            Patch patch = patchById.get(d.getPatchId());
            return new DeploymentView(
                    d.getId(), d.getDeploymentNo(), d.getTaskId(), task == null ? null : task.getTaskNo(),
                    d.getPatchId(), patch == null ? null : patch.getPatchId(), d.getEnvironment(), d.getRing(),
                    s(d.getStatus()), d.getProgress(), d.getOrchestrationRunId(), d.getTargetCount(),
                    d.getSuccessCount(), d.getFailureCount(), s(d.getCreatedAt()), s(d.getStartedAt()),
                    s(d.getCompletedAt()), d.getSelectionMode(), d.getCidrScopes(), d.getBatchSize(), d.getConcurrency(),
                    d.getFailureThreshold(), d.getTotalBatches(), d.getScopeSummary(),
                    targetsByDeployment.getOrDefault(d.getId(), List.of())
            );
        }).toList();
    }

    public DeploymentView deployment(PatchDeployment row) { return deploymentViews(List.of(row)).getFirst(); }

    private Map<Long, List<DeploymentTargetView>> deploymentTargetViews(Set<Long> deploymentIds) {
        if (deploymentIds.isEmpty()) return Map.of();
        List<DeploymentTarget> rows = deploymentTargets.findByDeploymentIdInOrderByDeploymentIdAscAssetIdAsc(deploymentIds);
        Set<Long> assetIds = rows.stream().map(DeploymentTarget::getAssetId).collect(Collectors.toSet());
        Map<Long, Asset> assetById = assetIds.isEmpty() ? Map.of() : index(assets.findAllById(assetIds), Asset::getId);
        return rows.stream().map(target -> {
            Asset asset = assetById.get(target.getAssetId());
            return new AbstractMap.SimpleEntry<>(target.getDeploymentId(), new DeploymentTargetView(
                    target.getId(), target.getAssetId(), asset == null ? null : asset.getAssetCode(),
                    asset == null ? null : asset.getName(), asset == null ? null : s(asset.getEnvironment()),
                    target.getBatchNo(), target.getStatus(), target.getProgress(), s(target.getStartedAt()), s(target.getCompletedAt()),
                    target.getMessage()
            ));
        }).collect(Collectors.groupingBy(Map.Entry::getKey, LinkedHashMap::new,
                Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
    }

    private Map<Long, List<DeploymentTargetView>> runTargetViews(Set<Long> runIds) {
        if (runIds.isEmpty()) return Map.of();
        List<DeploymentTarget> rows = deploymentTargets.findByRunIdInOrderByRunIdAscAssetIdAsc(runIds);
        Set<Long> assetIds = rows.stream().map(DeploymentTarget::getAssetId).collect(Collectors.toSet());
        Map<Long, Asset> assetById = assetIds.isEmpty() ? Map.of() : index(assets.findAllById(assetIds), Asset::getId);
        return rows.stream().map(target -> {
            Asset asset = assetById.get(target.getAssetId());
            return new AbstractMap.SimpleEntry<>(target.getRunId(), new DeploymentTargetView(
                    target.getId(), target.getAssetId(), asset == null ? null : asset.getAssetCode(),
                    asset == null ? null : asset.getName(), asset == null ? null : s(asset.getEnvironment()),
                    target.getBatchNo(), target.getStatus(), target.getProgress(), s(target.getStartedAt()), s(target.getCompletedAt()),
                    target.getMessage()
            ));
        }).collect(Collectors.groupingBy(Map.Entry::getKey, LinkedHashMap::new,
                Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
    }

    public AuditView audit(AuditEvent a) {
        return new AuditView(a.getId(), a.getEntityType(), a.getEntityId(), a.getAction(), a.getMessageZh(),
                a.getMessageEn(), a.getActor(), s(a.getCreatedAt()));
    }
}
