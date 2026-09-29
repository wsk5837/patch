package com.gazellio.platform.config;

import com.gazellio.platform.model.*;
import com.gazellio.platform.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

import static com.gazellio.platform.model.Enums.*;

@Component
@RequiredArgsConstructor
public class DemoDataSeeder implements CommandLineRunner {
    private final UserAccountRepository users;
    private final AssetRepository assets;
    private final VulnerabilityDefinitionRepository vulns;
    private final ScanAgentRepository agents;
    private final ScanJobRepository scans;
    private final FindingRepository findings;
    private final PatchRepository patches;
    private final PatchServerRepository patchServers;
    private final PatchCveRepository patchCves;
    private final RemediationTaskRepository tasks;
    private final ApprovalRequestRepository approvals;
    private final ApprovalStepRepository approvalSteps;
    private final OrchestrationTemplateRepository templates;
    private final OrchestrationTemplateStepRepository templateSteps;
    private final OrchestrationRunRepository runs;
    private final OrchestrationRunStepRepository runSteps;
    private final PatchDeploymentRepository deployments;
    private final DeploymentTargetRepository deploymentTargets;
    private final SecurityIncidentRepository incidents;
    private final ChangeWorkOrderRepository changeOrders;
    private final SystemSettingRepository settings;
    private final PasswordEncoder encoder;

    @Value("${app.seed-demo-data:true}") private boolean seed;
    @Value("${ADMIN_INITIAL_PASSWORD:}") private String adminPassword;
    @Value("${RENDER:false}") private boolean renderEnvironment;

    @Override @Transactional
    public void run(String... args) throws Exception {
        if (!seed) return;
        seedUsers();
        seedAssets();
        seedVulnerabilities();
        seedPatches();
        seedPatchServers();
        seedAgentsAndScans();
        seedFindingsTasksApprovals();
        seedLowerSeverityFindings();
        seedWorkOrders();
        seedTemplatesAndRuns();
        seedDeploymentTargets();
        seedSettings();
    }

    private void seedUsers() {
        users.findByUsername("admin").orElseGet(() -> users.save(UserAccount.builder()
                .username("admin")
                .passwordHash(encoder.encode(bootstrapAdminPassword()))
                .displayName("Gazellio Admin")
                .email("admin@gazellio.local")
                .role(UserRole.ADMIN)
                .build()));
        saveUserIfMissing("security", "王卫嘉", "security@gazellio.local", UserRole.SECURITY);
        saveUserIfMissing("ops", "曾卫平", "ops@gazellio.local", UserRole.OPS);
        saveUserIfMissing("appowner", "应用负责人", "appowner@gazellio.local", UserRole.APP_OWNER);
        saveUserIfMissing("approver", "发布审批人", "approver@gazellio.local", UserRole.APPROVER);
    }

    private String bootstrapAdminPassword() {
        if (adminPassword != null && !adminPassword.isBlank()) return adminPassword;
        if (renderEnvironment) {
            throw new IllegalStateException("ADMIN_INITIAL_PASSWORD must be configured on Render");
        }
        return "Gazellio@2026";
    }

    private void saveUserIfMissing(String username,String name,String email,UserRole role){
        users.findByUsername(username).orElseGet(() -> users.save(UserAccount.builder()
                .username(username)
                .passwordHash(encoder.encode(UUID.randomUUID().toString()))
                .displayName(name)
                .email(email)
                .role(role)
                .build()));
    }

    private void seedAssets() {
        if (assets.count() > 0) return;
        var ownerOps=users.findByUsername("ops").orElseThrow();
        var ownerApp=users.findByUsername("appowner").orElseThrow();
        List<Asset> list=List.of(
          asset("APP-PROD-01","支付应用节点 01","10.20.10.11","Red Hat Enterprise Linux","9.4",EnvironmentType.PROD,"支付服务",ownerOps,5,"Linux-2026Q3"),
          asset("APP-PROD-02","支付应用节点 02","10.20.10.12","Red Hat Enterprise Linux","9.4",EnvironmentType.PROD,"支付服务",ownerOps,5,"Linux-2026Q3"),
          asset("APP-TEST-01","支付测试节点","10.30.10.21","Red Hat Enterprise Linux","9.4",EnvironmentType.TEST,"支付服务",ownerApp,4,"Linux-2026Q3"),
          asset("APP-UAT-01","支付预生产节点","10.25.10.31","Red Hat Enterprise Linux","9.4",EnvironmentType.PREPROD,"支付服务",ownerApp,4,"Linux-2026Q3"),
          asset("WEB-PROD-01","互联网门户 Web 01","10.20.20.11","Ubuntu Server","24.04",EnvironmentType.PROD,"互联网门户",ownerOps,5,"Ubuntu-2026-09"),
          asset("WEB-TEST-01","互联网门户测试 Web","10.30.20.21","Ubuntu Server","24.04",EnvironmentType.TEST,"互联网门户",ownerApp,3,"Ubuntu-2026-09"),
          asset("WIN-PROD-01","核心 Windows 应用 01","10.20.30.11","Windows Server","2022",EnvironmentType.PROD,"核心业务",ownerOps,5,"MS-2026-09"),
          asset("WIN-UAT-01","核心 Windows 预生产","10.25.30.21","Windows Server","2022",EnvironmentType.PREPROD,"核心业务",ownerApp,4,"MS-2026-09"),
          asset("WIN-TEST-01","核心 Windows 测试","10.30.30.31","Windows Server","2022",EnvironmentType.TEST,"核心业务",ownerApp,4,"MS-2026-09"),
          asset("JENKINS-01","Jenkins CI","10.20.40.10","Ubuntu Server","22.04",EnvironmentType.PROD,"研发平台",ownerOps,4,"Ubuntu-2026-09"),
          asset("TOMCAT-01","客户服务 Tomcat","10.20.50.15","Rocky Linux","9.4",EnvironmentType.PROD,"客户服务",ownerOps,4,"Linux-2026Q3"),
          asset("DB-PROD-01","业务数据库 01","10.20.60.10","Red Hat Enterprise Linux","9.4",EnvironmentType.PROD,"数据服务",ownerOps,5,"Linux-2026Q3"),
          asset("VPN-EDGE-01","远程接入网关","172.16.1.10","FortiOS","7.4",EnvironmentType.PROD,"远程接入",ownerOps,5,"Network-2026-09"),
          asset("ADC-EDGE-01","应用交付控制器","172.16.2.10","NetScaler ADC","14.1",EnvironmentType.PROD,"入口网关",ownerOps,5,"Network-2026-09"),
          asset("DEVOPS-01","TeamCity 构建平台","10.20.70.20","Ubuntu Server","22.04",EnvironmentType.PROD,"研发平台",ownerOps,4,"Ubuntu-2026-09"),
          asset("DEVOPS-TEST-01","研发平台测试节点","10.30.70.21","Ubuntu Server","22.04",EnvironmentType.TEST,"研发平台",ownerApp,3,"Ubuntu-2026-09"),
          asset("DEVOPS-UAT-01","研发平台预生产节点","10.25.70.31","Ubuntu Server","22.04",EnvironmentType.PREPROD,"研发平台",ownerApp,3,"Ubuntu-2026-09"),
          asset("TOMCAT-TEST-01","客户服务测试节点","10.30.50.21","Rocky Linux","9.4",EnvironmentType.TEST,"客户服务",ownerApp,3,"Linux-2026Q3"),
          asset("TOMCAT-UAT-01","客户服务预生产节点","10.25.50.31","Rocky Linux","9.4",EnvironmentType.PREPROD,"客户服务",ownerApp,3,"Linux-2026Q3"),
          asset("ADC-TEST-01","入口网关测试设备","10.30.80.21","NetScaler ADC","14.1",EnvironmentType.TEST,"入口网关",ownerApp,3,"Network-2026-09"),
          asset("ADC-UAT-01","入口网关预生产设备","10.25.80.31","NetScaler ADC","14.1",EnvironmentType.PREPROD,"入口网关",ownerApp,4,"Network-2026-09"),
          asset("VPN-TEST-01","远程接入测试网关","10.30.81.21","FortiOS","7.4",EnvironmentType.TEST,"远程接入",ownerApp,3,"Network-2026-09"),
          asset("VPN-UAT-01","远程接入预生产网关","10.25.81.31","FortiOS","7.4",EnvironmentType.PREPROD,"远程接入",ownerApp,4,"Network-2026-09")
        );
        assets.saveAll(list);
    }

    private Asset asset(String code,String name,String ip,String os,String ver,EnvironmentType env,String service,UserAccount owner,int crit,String baseline){
        return Asset.builder().assetCode(code).name(name).ipAddress(ip).osName(os).osVersion(ver).environment(env).businessService(service)
                .ownerId(owner.getId()).ownerName(owner.getDisplayName()).criticality(crit).agentStatus("ONLINE").patchBaseline(baseline).lastSeenAt(Instant.now()).build();
    }

    private void seedVulnerabilities() throws IOException {
        List<VulnerabilityDefinition> rows=new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new ClassPathResource("seed-vulnerabilities.csv").getInputStream(), StandardCharsets.UTF_8))) {
            br.readLine(); String line;
            while((line=br.readLine())!=null){
                String[] x=line.split(",",9);
                if(x.length<9) continue;
                if(vulns.existsById(x[0])) continue;
                double cvss=Double.parseDouble(x[3]);
                rows.add(VulnerabilityDefinition.builder().cveId(x[0]).vendor(x[1]).product(x[2]).cvss(cvss).severity(Severity.valueOf(x[4]))
                    .kev(Boolean.parseBoolean(x[5])).patchAvailable(Boolean.parseBoolean(x[6])).titleZh(x[7]).titleEn(x[8])
                    .descriptionZh(x[7]+"，建议结合资产暴露面、业务重要度与厂商补丁状态进行处置。")
                    .descriptionEn(x[8]+". Prioritize remediation using asset exposure, business criticality and vendor patch status.")
                    .publishedDate(LocalDate.now().minusDays((long)(Math.random()*900))).referenceUrl("https://nvd.nist.gov/vuln/detail/"+x[0]).build());
            }
        }
        vulns.saveAll(rows);
    }

    private void seedPatches(){
        addPatch("KB5072180","Microsoft","Windows Server","2026-09","Windows Server 2022 九月安全更新","Windows Server 2022 September security update",740.0,true,List.of("CVE-2025-29824","CVE-2025-33053"));
        addPatch("RHEL-RHSA-2026:7211","Red Hat","OpenSSH","9.4p2","RHEL OpenSSH 安全更新","RHEL OpenSSH security update",18.5,false,List.of("CVE-2024-6387","CVE-2024-6386"));
        addPatch("openssl-3.5.2","OpenSSL","OpenSSL","3.5.2","OpenSSL 3.5.2 安全更新","OpenSSL 3.5.2 security update",9.2,false,List.of("CVE-2024-5535","CVE-2023-0465","CVE-2022-0778"));
        addPatch("apache-tomcat-11.0.12","Apache","Tomcat","11.0.12","Apache Tomcat 安全版本更新","Apache Tomcat security release",14.7,true,List.of("CVE-2025-24813"));
        addPatch("jenkins-2.479.3","Jenkins","Jenkins","2.479.3","Jenkins LTS 安全更新","Jenkins LTS security update",92.0,true,List.of("CVE-2024-23897"));
        addPatch("fortios-7.4.8","Fortinet","FortiOS","7.4.8","FortiOS 安全固件更新","FortiOS security firmware update",640.0,true,List.of("CVE-2024-21762"));
        addPatch("netscaler-14.1-29.72","Citrix","NetScaler ADC","14.1-29.72","NetScaler ADC 安全构建","NetScaler ADC security build",512.0,true,List.of("CVE-2023-4966","CVE-2023-3519"));
        addPatch("php-8.3.8","PHP","PHP CGI","8.3.8","PHP 8.3.8 安全更新","PHP 8.3.8 security update",31.0,false,List.of("CVE-2024-4577"));
        addPatch("spring-6.1.14","VMware","Spring Framework","6.1.14","Spring Framework 安全更新","Spring Framework security update",7.5,false,List.of("CVE-2024-38812","CVE-2022-22965"));
        addPatch("curl-8.10.1","cURL","curl","8.10.1","curl 安全更新","curl security update",4.8,false,List.of("CVE-2023-38545"));
        addPatch("confluence-8.5.15","Atlassian","Confluence","8.5.15","Confluence LTS 安全更新","Confluence LTS security update",980.0,true,List.of("CVE-2023-22518","CVE-2022-26134"));
        addPatch("linux-kernel-6.8.0-52","Linux Kernel","Kernel","6.8.0-52","Linux Kernel 安全更新","Linux Kernel security update",136.0,true,List.of("CVE-2024-1086"));
        addPatch("KB5074122","Microsoft","Windows Server","2026-10","Windows TCP/IP 与 LDAP 累积安全更新","Windows TCP/IP and LDAP cumulative security update",812.0,true,List.of("CVE-2024-49112","CVE-2024-38063","CVE-2024-49138","CVE-2024-43451"));
        addPatch("sharepoint-se-16.0.10417","Microsoft","SharePoint","16.0.10417","SharePoint Server 紧急安全更新","SharePoint Server emergency security update",1280.0,true,List.of("CVE-2025-53770","CVE-2025-53771"));
        addPatch("xz-5.6.1-3","Red Hat","xz Utils","5.6.1-3","xz Utils 安全回退更新","xz Utils security rollback update",3.4,false,List.of("CVE-2024-3094"));
        addPatch("panos-11.1.2-h3","Palo Alto Networks","PAN-OS","11.1.2-h3","PAN-OS GlobalProtect 热修复","PAN-OS GlobalProtect hotfix",950.0,true,List.of("CVE-2024-3400"));
        addPatch("teamcity-2024.03.3","JetBrains","TeamCity","2024.03.3","TeamCity 安全版本更新","TeamCity security release",1100.0,true,List.of("CVE-2024-27198","CVE-2023-42793"));
        addPatch("log4j-core-2.23.1","Apache","Log4j","2.23.1","Log4j Core 安全更新","Log4j Core security update",3.2,false,List.of("CVE-2021-44228"));
        addPatch("httpd-2.4.62","Apache","HTTP Server","2.4.62","Apache HTTP Server 安全更新","Apache HTTP Server security update",12.8,true,List.of("CVE-2021-41773","CVE-2021-42013"));
        addPatch("exchange-se-2026-09","Microsoft","Exchange Server","2026-09","Exchange Server 安全更新","Exchange Server security update",1620.0,true,List.of("CVE-2021-26855","CVE-2022-41040","CVE-2022-41082"));
        // The library flag must describe an actual drill-down result, not a disconnected demo label.
        for(VulnerabilityDefinition v:vulns.findAll()){
            boolean available=!patchCves.findByCveId(v.getCveId()).isEmpty();
            if(v.isPatchAvailable()!=available){v.setPatchAvailable(available);v.setUpdatedAt(Instant.now());vulns.save(v);}
        }
    }

    private void addPatch(String code,String vendor,String product,String version,String zh,String en,double size,boolean reboot,List<String> cves){
        Patch p=patches.findByPatchId(code).orElseGet(Patch::new);
        p.setPatchId(code);p.setVendor(vendor);p.setProduct(product);p.setVersion(version);p.setTitleZh(zh);p.setTitleEn(en);
        p.setSizeMb(size);p.setRebootRequired(reboot);p.setSource("Vendor");p.setStatus("AVAILABLE");
        if(p.getPublishedDate()==null)p.setPublishedDate(LocalDate.now().minusDays(5));
        if(p.getChecksum()==null)p.setChecksum("sha256:"+UUID.randomUUID().toString().replace("-","")+UUID.randomUUID().toString().replace("-",""));
        p.setSignatureStatus("VERIFIED");
        p.setApplicabilityRule(product+" "+version+"；安装前校验操作系统、产品版本、架构与现有补丁替代关系。");
        p.setApplicabilityRuleEn(product+" "+version+"; validate the operating system, product version, architecture and supersedence before installation.");
        p.setDownloadUrl("https://patch.gazellio.local/vendor/"+code.replace(":","-").toLowerCase(Locale.ROOT));
        p.setReleaseNotesZh("包含安全修复、安装前检查、完整性校验、失败回滚与重启策略。建议先在测试和预生产环境验证。");
        p.setReleaseNotesEn("Includes security fixes, pre-checks, integrity validation, rollback and restart policy. Validate in test and pre-production first.");
        p.setSignatureIssuer(vendor+" Code Signing CA");
        p.setSignatureFingerprint("SHA256:"+UUID.nameUUIDFromBytes((code+vendor).getBytes(StandardCharsets.UTF_8)).toString().replace("-","").toUpperCase(Locale.ROOT));
        p.setIntegrityVerifiedAt(Instant.now().minus(Duration.ofHours(2)));
        p.setVendorAdvisoryUrl(vendorAdvisory(vendor,cves.getFirst()));
        p.setPrerequisites("Agent 1.6.0+；磁盘可用空间不少于补丁包大小的 3 倍；已生成回退点；业务健康探针可用；维护窗口已确认。");
        p.setPrerequisitesEn("Agent 1.6.0+; free disk space at least three times the package size; rollback point created; health probe available; maintenance window confirmed.");
        p.setInstallCommand("gazellio-agent patch install --package \""+code+"\" --verify-signature --rollback-point auto");
        p.setUninstallCommand("gazellio-agent patch rollback --package \""+code+"\" --restore-point latest");
        p.setTestEvidence("PACKAGE_INTEGRITY_VERIFIED");
        p.setKnownIssues(reboot?"安装完成后需要在维护窗口内重启；集群节点须按批次滚动执行。":"未发现阻断性已知问题；安装前仍需确认进程占用和依赖版本。");
        p.setKnownIssuesEn(reboot?"A maintenance-window restart is required; clustered nodes must be rolled out by ring.":"No blocking known issues; confirm process locks and dependency versions before installation.");
        p.setUpdatedAt(Instant.now());p=patches.save(p);
        for(String c:cves){
            if(!patchCves.existsByPatchIdAndCveId(p.getId(),c))patchCves.save(PatchCve.builder().patchId(p.getId()).cveId(c).build());
            vulns.findById(c).ifPresent(v->{v.setPatchAvailable(true);v.setUpdatedAt(Instant.now());vulns.save(v);});
        }
    }

    private String vendorAdvisory(String vendor,String cve){
        String encoded=cve==null?"":cve;
        if("Microsoft".equalsIgnoreCase(vendor))return "https://msrc.microsoft.com/update-guide/vulnerability/"+encoded;
        if("Red Hat".equalsIgnoreCase(vendor))return "https://access.redhat.com/security/cve/"+encoded;
        if("Apache".equalsIgnoreCase(vendor))return "https://security.apache.org/";
        return "https://nvd.nist.gov/vuln/detail/"+encoded;
    }

    private void seedPatchServers(){
        if(patchServers.count()>0)return;
        patchServers.save(PatchServer.builder().name("PCH-SH-01").address("https://patch-sh.internal:8443").region("Primary").osSupport("Windows / Linux").status("ONLINE").lastSyncAt(Instant.now().minusSeconds(380)).capacityGb(2048.0).usedGb(684.0).build());
        patchServers.save(PatchServer.builder().name("PCH-BJ-01").address("https://patch-bj.internal:8443").region("Secondary").osSupport("Windows / Linux / Network").status("ONLINE").lastSyncAt(Instant.now().minusSeconds(620)).capacityGb(2048.0).usedGb(731.0).build());
        patchServers.save(PatchServer.builder().name("PCH-DR-01").address("https://patch-dr.internal:8443").region("DR").osSupport("Windows / Linux").status("STANDBY").lastSyncAt(Instant.now().minusSeconds(1800)).capacityGb(1024.0).usedGb(342.0).build());
    }

    private void seedAgentsAndScans(){
        if(agents.count()==0){
            int i=1; for(Asset a:assets.findAll().stream().limit(9).toList()) agents.save(ScanAgent.builder().agentKey("AGENT-"+String.format("%03d",i++)).hostname(a.getName()).ipAddress(a.getIpAddress()).osName(a.getOsName()).version("1.6.0").status(AgentStatus.ONLINE).assetId(a.getId()).lastHeartbeatAt(Instant.now().minusSeconds((long)(Math.random()*300))).build());
        }
        if(scans.count()==0){
            scans.save(ScanJob.builder().jobNo("SCN-260929-001").name("生产服务器认证扫描").scanType("AUTHENTICATED").targetType("ENVIRONMENT").targetValue("PROD").credentialType("AGENT").status(ScanStatus.COMPLETED).progress(100).findingsCount(18).requestedByName("王卫嘉").startedAt(Instant.now().minus(Duration.ofHours(2))).completedAt(Instant.now().minus(Duration.ofMinutes(70))).build());
            scans.save(ScanJob.builder().jobNo("SCN-260929-002").name("测试环境补丁复测").scanType("TARGETED_RESCAN").targetType("ENVIRONMENT").targetValue("TEST").credentialType("AGENT").status(ScanStatus.COMPLETED).progress(100).findingsCount(3).requestedByName("王卫嘉").startedAt(Instant.now().minus(Duration.ofHours(1))).completedAt(Instant.now().minus(Duration.ofMinutes(35))).build());
            scans.save(ScanJob.builder().jobNo("SCN-260929-003").name("互联网暴露面快速扫描").scanType("NETWORK").targetType("SERVICE").targetValue("互联网门户").credentialType("NONE").status(ScanStatus.RUNNING).progress(64).findingsCount(5).requestedByName("王卫嘉").startedAt(Instant.now().minus(Duration.ofMinutes(22))).build());
        }
    }

    private void seedFindingsTasksApprovals(){
        if(findings.count()>0) return;
        Map<String,Asset> a=new HashMap<>(); assets.findAll().forEach(x->a.put(x.getAssetCode(),x));
        ScanJob scan=scans.findTop100ByOrderByCreatedAtDesc().stream().filter(s->s.getStatus()==ScanStatus.COMPLETED).findFirst().orElseThrow();
        String[][] rows={
            {"APP-PROD-01","CVE-2024-6387","IN_REMEDIATION"},{"APP-PROD-02","CVE-2024-6387","CONFIRMED"},{"APP-TEST-01","CVE-2024-5535","IN_REMEDIATION"},
            {"WEB-PROD-01","CVE-2023-38545","NEW"},{"WIN-PROD-01","CVE-2025-29824","IN_REMEDIATION"},{"WIN-UAT-01","CVE-2025-33053","CONFIRMED"},
            {"JENKINS-01","CVE-2024-23897","IN_REMEDIATION"},{"TOMCAT-01","CVE-2025-24813","IN_REMEDIATION"},{"DB-PROD-01","CVE-2024-1086","NEW"},
            {"VPN-EDGE-01","CVE-2024-21762","CONFIRMED"},{"ADC-EDGE-01","CVE-2023-4966","IN_REMEDIATION"},{"DEVOPS-01","CVE-2024-27198","NEW"},
            {"WEB-TEST-01","CVE-2024-4577","RESOLVED"},{"WIN-TEST-01","CVE-2024-49138","FALSE_POSITIVE"},{"VPN-TEST-01","CVE-2024-21762","EXEMPTED"}
        };
        int n=1;
        for(String[] r:rows){
            Asset x=a.get(r[0]); VulnerabilityDefinition v=vulns.findById(r[1]).orElseThrow();
            double risk=Math.min(10.0,(v.getCvss()==null?5:v.getCvss())+(x.getCriticality()-3)*0.25+(v.isKev()?0.5:0));
            Finding f=findings.save(Finding.builder().assetId(x.getId()).cveId(v.getCveId()).scanJobId(scan.getId()).status(FindingStatus.valueOf(r[2])).riskScore(risk).ownerId(x.getOwnerId()).ownerName(x.getOwnerName()).evidence("agent-package-match:"+v.getProduct()).firstSeenAt(Instant.now().minus(Duration.ofDays(12+n))).lastSeenAt(Instant.now().minus(Duration.ofHours(n))).build());
            if(f.getStatus()==FindingStatus.RESOLVED) f.setResolvedAt(Instant.now().minus(Duration.ofDays(2)));
            if(f.getStatus()==FindingStatus.FALSE_POSITIVE) f.setFalsePositiveReason("版本指纹误识别，人工验证后确认不受影响。");
            if(f.getStatus()==FindingStatus.EXEMPTED){f.setExemptionReason("测试环境设备将在下一个维护周期统一升级，当前风险已批准临时接受。");f.setExemptedAt(Instant.now().minus(Duration.ofDays(2)));f.setExemptionExpiresAt(Instant.now().plus(Duration.ofDays(28)));}
            findings.save(f); n++;
        }
        createTask("APP-PROD-01","CVE-2024-6387","RHEL-RHSA-2026:7211",TaskStage.ASSIGNED,"P1",ChangeType.EMERGENCY);
        createTask("APP-TEST-01","CVE-2024-5535","openssl-3.5.2",TaskStage.APP_VERIFY,"P2",ChangeType.NORMAL);
        createTask("WIN-PROD-01","CVE-2025-29824","KB5072180",TaskStage.RELEASE_APPROVAL,"P1",ChangeType.MAJOR);
        createTask("JENKINS-01","CVE-2024-23897","jenkins-2.479.3",TaskStage.PROD_PATCH,"P1",ChangeType.EMERGENCY);
        createTask("TOMCAT-01","CVE-2025-24813","apache-tomcat-11.0.12",TaskStage.ASSIGNED,"P1",ChangeType.NORMAL);
        createTask("ADC-EDGE-01","CVE-2023-4966","netscaler-14.1-29.72",TaskStage.PREPROD_VERIFY,"P1",ChangeType.MAJOR);
    }

    private void createTask(String assetCode,String cve,String patchCode,TaskStage stage,String priority,ChangeType type){
        Asset a=assets.findByAssetCode(assetCode).orElseThrow(); Finding f=findings.findByAssetIdAndCveId(a.getId(),cve).orElseThrow(); Patch p=patches.findByPatchId(patchCode).orElseThrow();
        RemediationTask t=tasks.save(RemediationTask.builder().taskNo("RMD-"+String.format("%06d",tasks.count()+1)).findingId(f.getId()).patchId(p.getId()).assetId(a.getId()).ownerId(a.getOwnerId()).ownerName(a.getOwnerName()).priority(priority).stage(stage).status(stage==TaskStage.CLOSED?TaskStatus.COMPLETED:TaskStatus.IN_PROGRESS).changeType(type).dueAt(Instant.now().plus(Duration.ofDays(priority.equals("P1")?2:7))).build());
        f.setRemediationTaskId(t.getId()); f.setStatus(stage==TaskStage.CLOSED?FindingStatus.RESOLVED:FindingStatus.IN_REMEDIATION); findings.save(f);
        if(stage==TaskStage.RELEASE_APPROVAL){
            ApprovalRequest ar=approvals.save(ApprovalRequest.builder().approvalNo("APR-"+String.format("%06d",approvals.count()+1)).taskId(t.getId()).changeType(type).status(ApprovalStatus.PENDING).currentStep(1).requestedByName("王卫嘉").reason("测试环境验证与复测通过，申请进入生产发布。 ").rollbackPlan("失败自动暂停；恢复快照或回退补丁版本。 ").build());
            approvalSteps.save(ApprovalStep.builder().approvalId(ar.getId()).stepOrder(1).roleNameZh("运维负责人").roleNameEn("Operations Lead").approverName("曾卫平").status(ApprovalStepStatus.PENDING).build());
            approvalSteps.save(ApprovalStep.builder().approvalId(ar.getId()).stepOrder(2).roleNameZh("安全负责人").roleNameEn("Security Lead").approverName("王卫嘉").status(ApprovalStepStatus.WAITING).build());
            t.setApprovalId(ar.getId()); tasks.save(t);
        }
    }

    private void seedLowerSeverityFindings(){
        ScanJob scan=scans.findTop100ByOrderByCreatedAtDesc().stream().filter(s->s.getStatus()==ScanStatus.COMPLETED).findFirst().orElse(null);
        if(scan==null)return;
        String[][] rows={{"APP-PROD-02","CVE-2024-6386"},{"WIN-UAT-01","CVE-2024-43451"},{"WEB-TEST-01","CVE-2023-0465"},{"APP-TEST-01","CVE-2022-0778"}};
        for(String[] row:rows){
            Asset asset=assets.findByAssetCode(row[0]).orElse(null);VulnerabilityDefinition vulnerability=vulns.findById(row[1]).orElse(null);
            if(asset==null||vulnerability==null||findings.findByAssetIdAndCveId(asset.getId(),row[1]).isPresent())continue;
            double score=Math.min(10.0,(vulnerability.getCvss()==null?4.0:vulnerability.getCvss())+(asset.getCriticality()-3)*0.25);
            findings.save(Finding.builder().assetId(asset.getId()).cveId(row[1]).scanJobId(scan.getId()).status(FindingStatus.CONFIRMED)
                    .riskScore(score).ownerId(asset.getOwnerId()).ownerName(asset.getOwnerName())
                    .evidence("authenticated-package-version:"+vulnerability.getProduct()).firstSeenAt(Instant.now().minus(Duration.ofDays(8)))
                    .lastSeenAt(Instant.now().minus(Duration.ofHours(6))).build());
        }
    }

    private void seedWorkOrders(){
        for(Finding f:findings.findAll()){
            Asset asset=assets.findById(f.getAssetId()).orElse(null);
            VulnerabilityDefinition vulnerability=vulns.findById(f.getCveId()).orElse(null);
            if(asset==null||vulnerability==null)continue;
            RemediationTask task=tasks.findByFindingId(f.getId()).orElse(null);
            IncidentStatus incidentStatus;
            if(f.getStatus()==FindingStatus.RESOLVED)incidentStatus=IncidentStatus.CLOSED;
            else if(f.getStatus()==FindingStatus.FALSE_POSITIVE)incidentStatus=IncidentStatus.FALSE_POSITIVE;
            else if(f.getStatus()==FindingStatus.EXEMPTED)incidentStatus=IncidentStatus.EXEMPTED;
            else if(task==null)incidentStatus=IncidentStatus.ASSIGNED;
            else if(task.getStage()==TaskStage.RELEASE_APPROVAL)incidentStatus=IncidentStatus.PENDING_CHANGE;
            else if(List.of(TaskStage.PREPROD_PATCH,TaskStage.PREPROD_VERIFY,TaskStage.PREPROD_RESCAN,TaskStage.PROD_PATCH,TaskStage.PROD_VERIFY,TaskStage.PROD_RESCAN).contains(task.getStage()))incidentStatus=IncidentStatus.IMPLEMENTING;
            else incidentStatus=IncidentStatus.IN_REMEDIATION;
            String priority=(vulnerability.isKev()||vulnerability.getSeverity()==Severity.CRITICAL)?"P1":
                    (vulnerability.getSeverity()==Severity.HIGH||asset.getCriticality()>=5)?"P2":
                            vulnerability.getSeverity()==Severity.MEDIUM?"P3":"P4";
            SecurityIncident incident=incidents.findByFindingId(f.getId()).orElseGet(()->incidents.save(SecurityIncident.builder()
                    .incidentNo("SEC-MIG-"+String.format("%06d",f.getId())).externalTicketNo("AITSM-SEC-"+String.format("%06d",f.getId()))
                    .findingId(f.getId()).assetId(f.getAssetId()).priority(priority).ownerId(f.getOwnerId()).ownerName(f.getOwnerName())
                    .dueAt(task!=null&&task.getDueAt()!=null?task.getDueAt():Instant.now().plus(Duration.ofDays(priority.equals("P1")?3:priority.equals("P2")?7:priority.equals("P3")?30:90))).build()));
            incident.setStatus(incidentStatus);incident.setRemediationTaskId(task==null?null:task.getId());incident.setUpdatedAt(Instant.now());
            if(f.getStatus()==FindingStatus.EXEMPTED)incident.setDecisionReason(f.getExemptionReason());
            if(f.getStatus()==FindingStatus.FALSE_POSITIVE)incident.setDecisionReason(f.getFalsePositiveReason());
            if(f.getStatus()==FindingStatus.RESOLVED){incident.setResolvedAt(f.getResolvedAt());incident.setClosedAt(f.getResolvedAt());}
            incidents.save(incident);f.setSecurityIncidentId(incident.getId());findings.save(f);
            if(task!=null){task.setSecurityIncidentId(incident.getId());tasks.save(task);}

            if(task!=null&&List.of(TaskStage.RELEASE_APPROVAL,TaskStage.PREPROD_PATCH,TaskStage.PREPROD_VERIFY,TaskStage.PREPROD_RESCAN,TaskStage.PROD_PATCH,TaskStage.PROD_VERIFY,TaskStage.PROD_RESCAN,TaskStage.CLOSED).contains(task.getStage())){
                ChangeWorkOrder change=changeOrders.findByIncidentId(incident.getId()).orElseGet(()->changeOrders.save(ChangeWorkOrder.builder()
                        .changeNo("CHG-MIG-"+String.format("%06d",incident.getId())).externalChangeNo("AITSM-CHG-"+String.format("%06d",incident.getId())).incidentId(incident.getId()).remediationTaskId(task.getId())
                        .approvalId(task.getApprovalId()).changeType(task.getChangeType()==null?ChangeType.NORMAL:task.getChangeType())
                        .status(task.getStage()==TaskStage.RELEASE_APPROVAL?ChangeStatus.PENDING_APPROVAL:task.getStage()==TaskStage.CLOSED?ChangeStatus.CLOSED:ChangeStatus.IMPLEMENTING)
                        .summary(f.getCveId()+" · "+asset.getBusinessService()+" 生产补丁发布")
                        .riskAssessment("基于漏洞严重度、KEV 状态、资产重要度、影响范围与重启要求评估。")
                        .implementationPlan("测试复测通过后，按 Ring 0/1/2 分批执行生产补丁并进行应用验证。")
                        .rollbackPlan("失败时暂停后续批次，恢复快照或回退补丁版本，并重新验证服务健康状态。")
                        .maintenanceStart(Instant.now().plus(Duration.ofDays(1))).maintenanceEnd(Instant.now().plus(Duration.ofDays(1)).plus(Duration.ofHours(4)))
                        .build()));
                if(change.getExternalChangeNo()==null)change.setExternalChangeNo("AITSM-CHG-"+String.format("%06d",incident.getId()));
                changeOrders.save(change);
                incident.setChangeOrderId(change.getId());incidents.save(incident);task.setChangeOrderId(change.getId());tasks.save(task);
                if(task.getApprovalId()!=null)approvals.findById(task.getApprovalId()).ifPresent(a->{a.setChangeOrderId(change.getId());approvals.save(a);});
            }
        }
    }

    private void seedTemplatesAndRuns(){
        if(templates.count()==0){
            OrchestrationTemplate standard=templates.save(OrchestrationTemplate.builder().code("PATCH-STANDARD").nameZh("标准补丁发布编排").nameEn("Standard Patch Rollout").type("PATCH").version(3).build());
            addTemplateSteps(standard,List.of(
                new String[]{"PRECHECK","执行前检查","Pre-check"},new String[]{"SNAPSHOT","快照与回退点","Snapshot & rollback point"},new String[]{"DOWNLOAD","下载补丁包","Download package"},
                new String[]{"VERIFY","校验签名与适用性","Verify signature & applicability"},new String[]{"INSTALL","安装补丁","Install patch"},new String[]{"RESTART","服务重启/主机重启","Service/host restart"},
                new String[]{"HEALTH","应用健康检查","Application health check"},new String[]{"RESCAN","漏洞定向复测","Targeted vulnerability rescan"},new String[]{"EVIDENCE","回写执行证据","Write back evidence"}));
            OrchestrationTemplate emergency=templates.save(OrchestrationTemplate.builder().code("PATCH-EMERGENCY").nameZh("紧急漏洞修复编排").nameEn("Emergency Vulnerability Remediation").type("PATCH").version(2).build());
            addTemplateSteps(emergency,List.of(new String[]{"PRECHECK","紧急前置检查","Emergency pre-check"},new String[]{"DOWNLOAD","下载已批准补丁","Download approved patch"},new String[]{"INSTALL","安装补丁","Install patch"},new String[]{"HEALTH","关键探针验证","Critical probe validation"},new String[]{"RESCAN","漏洞定向复测","Targeted vulnerability rescan"},new String[]{"EVIDENCE","回写执行证据","Write back evidence"}));
            OrchestrationTemplate lifecycle=templates.save(OrchestrationTemplate.builder().code("VULN-PATCH-CLOSED-LOOP").nameZh("漏洞与补丁闭环编排").nameEn("Vulnerability & Patch Closed-loop Workflow").type("WORKFLOW").version(1).build());
            addTemplateSteps(lifecycle,List.of(
                new String[]{"SCAN","执行漏洞扫描","Run vulnerability scan"},
                new String[]{"PATCH_MATCH","匹配漏洞与补丁","Match vulnerability and patch"},
                new String[]{"CMDB_MATCH","关联 CMDB 资产","Correlate CMDB assets"},
                new String[]{"OWNER_ASSIGN","同步责任人与任务分派","Sync owner and assign task"},
                new String[]{"TEST_PATCH","测试环境补丁执行","Patch test environment"},
                new String[]{"TEST_VERIFY","测试应用验证","Validate test application"},
                new String[]{"TEST_RESCAN","测试环境漏洞复测","Rescan test environment"},
                new String[]{"RELEASE_GATE","生产发布审批","Production release approval"},
                new String[]{"PREPROD_PATCH","预生产环境补丁执行","Patch pre-production"},
                new String[]{"PREPROD_VERIFY","预生产应用验证","Validate pre-production"},
                new String[]{"PREPROD_RESCAN","预生产环境漏洞复测","Rescan pre-production"},
                new String[]{"PROD_PATCH","生产环境分批补丁执行","Progressive production patching"},
                new String[]{"PROD_VERIFY","生产应用验证","Validate production application"},
                new String[]{"PROD_RESCAN","生产环境漏洞复测","Rescan production"},
                new String[]{"EVIDENCE_CLOSE","关闭发布并归档证据","Close release and archive evidence"},
                new String[]{"CMDB_UPDATE","回写 CMDB 补丁状态","Write patch state back to CMDB"},
                new String[]{"VULN_CLOSE","关闭漏洞实例","Close vulnerability finding"}
            ));
        }
        ensureExecutionTemplate("PATCH-STANDARD","标准补丁安装编排","Standard Patch Installation","PATCH",4,List.of(
                new String[]{"PRECHECK","执行前检查","Pre-check"},new String[]{"SNAPSHOT","快照与回退点","Snapshot & rollback point"},
                new String[]{"DOWNLOAD","获取补丁包","Acquire package"},new String[]{"VERIFY","校验签名与适用性","Verify signature & applicability"},
                new String[]{"INSTALL","安装补丁","Install patch"},new String[]{"RESTART","服务/主机重启","Service/host restart"},
                new String[]{"HEALTH","应用健康检查","Application health check"},new String[]{"EVIDENCE","回写安装证据","Write installation evidence"}));
        ensureExecutionTemplate("PATCH-EMERGENCY","紧急补丁安装编排","Emergency Patch Installation","PATCH",3,List.of(
                new String[]{"PRECHECK","紧急前置检查","Emergency pre-check"},new String[]{"DOWNLOAD","获取已批准补丁","Acquire approved package"},
                new String[]{"VERIFY","校验签名与适用性","Verify signature & applicability"},new String[]{"INSTALL","安装补丁","Install patch"},
                new String[]{"HEALTH","关键探针验证","Critical probe validation"},new String[]{"EVIDENCE","回写安装证据","Write installation evidence"}));
        ensureExecutionTemplate("PATCH-RETEST","补丁效果复测","Patch Effect Retest","RETEST",1,List.of(
                new String[]{"CONNECT","连接目标与读取基线","Connect target and read baseline"},
                new String[]{"INSTALL_STATE","校验补丁安装状态","Validate installed patch state"},
                new String[]{"VERSION_PROBE","校验版本与修复标识","Validate version and remediation marker"},
                new String[]{"VULN_PROBE","执行漏洞定向探测","Run targeted vulnerability probe"},
                new String[]{"EFFECT_CHECK","验证应用健康与修复效果","Validate application health and remediation effect"},
                new String[]{"EVIDENCE","归档复测证据","Archive retest evidence"}));
        if(runs.count()==0){
            OrchestrationTemplate tpl=templates.findByCode("PATCH-STANDARD").orElseThrow(); RemediationTask task=tasks.findTop200ByOrderByUpdatedAtDesc().stream().filter(t->t.getStage()==TaskStage.PROD_PATCH).findFirst().orElse(null);
            if(task!=null){
                PatchDeployment dep=deployments.save(PatchDeployment.builder().deploymentNo("DEP-260929-001").taskId(task.getId()).patchId(task.getPatchId()).environment("PROD").ring("Ring 1 · 20%").status(DeploymentStatus.RUNNING).progress(56).targetCount(2).successCount(1).startedAt(Instant.now().minus(Duration.ofMinutes(18))).build());
                OrchestrationRun run=runs.save(OrchestrationRun.builder().runNo("RUN-260929-001").templateId(tpl.getId()).taskId(task.getId()).deploymentId(dep.getId()).environment("PROD").ring("Ring 1 · 20%").status(RunStatus.RUNNING).currentStep(5).progress(56).startedAt(Instant.now().minus(Duration.ofMinutes(18))).build());
                dep.setOrchestrationRunId(run.getId()); deployments.save(dep); task.setLatestRunId(run.getId()); tasks.save(task);
                var steps=templateSteps.findByTemplateIdOrderByStepOrderAsc(tpl.getId());
                for(var s:steps){
                    RunStepStatus st=s.getStepOrder()<5?RunStepStatus.SUCCEEDED:s.getStepOrder()==5?RunStepStatus.RUNNING:RunStepStatus.WAITING;
                    runSteps.save(OrchestrationRunStep.builder().runId(run.getId()).stepOrder(s.getStepOrder()).code(s.getCode()).nameZh(s.getNameZh()).nameEn(s.getNameEn()).status(st).startedAt(st!=RunStepStatus.WAITING?Instant.now().minus(Duration.ofMinutes(20-s.getStepOrder()*2L)):null).completedAt(st==RunStepStatus.SUCCEEDED?Instant.now().minus(Duration.ofMinutes(18-s.getStepOrder()*2L)):null).messageZh(st==RunStepStatus.SUCCEEDED?"执行成功":st==RunStepStatus.RUNNING?"正在执行":"等待执行").messageEn(st==RunStepStatus.SUCCEEDED?"Succeeded":st==RunStepStatus.RUNNING?"Running":"Waiting").build());
                }
            }
        }
    }

    private void ensureExecutionTemplate(String code,String zh,String en,String type,int version,List<String[]> steps){
        OrchestrationTemplate template=templates.findByCode(code).orElseGet(OrchestrationTemplate::new);
        boolean replace=template.getId()==null||template.getVersion()==null||template.getVersion()<version;
        template.setCode(code);template.setNameZh(zh);template.setNameEn(en);template.setType(type);template.setEnabled(true);template.setVersion(version);template.setUpdatedAt(Instant.now());template=templates.save(template);
        if(replace){
            if(template.getId()!=null){templateSteps.deleteByTemplateId(template.getId());templateSteps.flush();}
            addTemplateSteps(template,steps);
        }
    }

    private void addTemplateSteps(OrchestrationTemplate t,List<String[]> steps){
        int i=1;
        for(String[] s:steps){
            templateSteps.save(OrchestrationTemplateStep.builder().templateId(t.getId()).stepOrder(i).code(s[0]).nameZh(s[1]).nameEn(s[2]).rollbackPoint(i==2).build());
            i++;
        }
    }

    private void seedDeploymentTargets(){
        for(PatchDeployment deployment:deployments.findTop200ByOrderByCreatedAtDesc()){
            if(!deploymentTargets.findByDeploymentIdOrderByAssetIdAsc(deployment.getId()).isEmpty())continue;
            RemediationTask task=tasks.findById(deployment.getTaskId()).orElse(null);if(task==null)continue;
            Asset source=assets.findById(task.getAssetId()).orElse(null);if(source==null)continue;
            EnvironmentType environment;try{environment=EnvironmentType.valueOf(deployment.getEnvironment());}catch(Exception e){continue;}
            List<Asset> targets=assets.findByBusinessServiceAndEnvironment(source.getBusinessService(),environment);
            if(targets.isEmpty()&&source.getEnvironment()==environment)targets=List.of(source);
            for(Asset target:targets){
                deploymentTargets.save(DeploymentTarget.builder().deploymentId(deployment.getId()).runId(deployment.getOrchestrationRunId()).assetId(target.getId())
                        .status(deployment.getStatus().name()).progress(deployment.getProgress()).startedAt(deployment.getStartedAt())
                        .completedAt(deployment.getCompletedAt()).message(deployment.getStatus()==DeploymentStatus.RUNNING?"正在执行自动化补丁节点":"已同步部署结果").build());
            }
        }
    }

    private void seedSettings(){
        if(settings.count()>0) return;
        settings.save(SystemSetting.builder().settingKey("defaultLanguage").settingValue("zh-CN").build());
        settings.save(SystemSetting.builder().settingKey("scanPolicyProd").settingValue("0 0 2 * * SAT").build());
        settings.save(SystemSetting.builder().settingKey("scanPolicyTest").settingValue("0 0 */6 * * *").build());
        settings.save(SystemSetting.builder().settingKey("maintenanceWindow").settingValue("Sat 00:00-04:00").build());
        settings.save(SystemSetting.builder().settingKey("autoRollbackThreshold").settingValue("5").build());
    }
}
