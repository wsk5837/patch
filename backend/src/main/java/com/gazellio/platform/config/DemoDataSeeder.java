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
        upgradeFindingEvidence();
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
        var ownerOps=users.findByUsername("ops").orElseThrow();
        var ownerApp=users.findByUsername("appowner").orElseThrow();
        List<CustomerAssetSpec> catalog=customerAssetCatalog();
        migrateLegacyAssets(catalog);
        upsertCustomerAssets(catalog,ownerOps,ownerApp);
    }

    private List<CustomerAssetSpec> customerAssetCatalog(){
        return List.of(
                new CustomerAssetSpec("MYSQL","Oracle MySQL","DATABASE","数据库服务","Red Hat Enterprise Linux","9.4","Oracle MySQL 8.4, OpenSSH, OpenSSL",10),
                new CustomerAssetSpec("TOMCAT","Apache Tomcat","MIDDLEWARE","Web 与中间件","Rocky Linux","9.4","Apache Tomcat 11.0.12, OpenSSH, OpenSSL",20),
                new CustomerAssetSpec("OPENSSH","Open SSH","SECURITY_COMPONENT","基础组件服务","Red Hat Enterprise Linux","9.4","OpenSSH 9.8p1, OpenSSL",30),
                new CustomerAssetSpec("SPRINGBOOT","Spring Boot","APPLICATION_PLATFORM","应用运行平台","Red Hat Enterprise Linux","9.4","Spring Boot 3.3.4, Spring Framework 6.1.14, OpenSSH, OpenSSL",20),
                new CustomerAssetSpec("PHP","PHP","APPLICATION_RUNTIME","应用运行时","Ubuntu Server","24.04","PHP 8.3.8, OpenSSH, OpenSSL",30),
                new CustomerAssetSpec("HTTPD","Apache HTTP Server","MIDDLEWARE","Web 与中间件","Ubuntu Server","24.04","Apache HTTP Server 2.4.62, OpenSSH, OpenSSL",20),
                new CustomerAssetSpec("JETTY","Eclipse Jetty","MIDDLEWARE","Web 与中间件","Rocky Linux","9.4","Eclipse Jetty 12.0.12, OpenSSH, OpenSSL",20),
                new CustomerAssetSpec("OPENSSL","OpenSSL","SECURITY_COMPONENT","基础组件服务","Red Hat Enterprise Linux","9.4","OpenSSL 3.5.2, OpenSSH",30),
                new CustomerAssetSpec("STRUTS2","Apache Struts2","APPLICATION_PLATFORM","应用运行平台","Red Hat Enterprise Linux","9.4","Apache Struts2 6.4.0, OpenSSH, OpenSSL",20),
                new CustomerAssetSpec("REDIS","Redis","DATABASE","数据库服务","Rocky Linux","9.4","Redis 7.4, OpenSSH, OpenSSL",10),
                new CustomerAssetSpec("PYTHON","Python","APPLICATION_RUNTIME","应用运行时","Ubuntu Server","24.04","Python 3.12.6, OpenSSH, OpenSSL",30),
                new CustomerAssetSpec("MARIADB","MariaDB","DATABASE","数据库服务","Red Hat Enterprise Linux","9.4","MariaDB 11.4, OpenSSH, OpenSSL",10),
                new CustomerAssetSpec("GRAFANA","Grafana","OBSERVABILITY","可观测性平台","Ubuntu Server","24.04","Grafana 11.2, OpenSSH, OpenSSL",40),
                new CustomerAssetSpec("JIRA","Atlassian Jira","COLLABORATION","协作服务","Red Hat Enterprise Linux","9.4","Atlassian Jira 9.17, OpenSSH, OpenSSL",40),
                new CustomerAssetSpec("SAMBA","Samba","FILE_SERVICE","文件服务","Red Hat Enterprise Linux","9.4","Samba 4.20, OpenSSH, OpenSSL",30),
                new CustomerAssetSpec("ELASTIC","Elasticsearch","SEARCH_PLATFORM","搜索与数据平台","Rocky Linux","9.4","Elasticsearch 8.15, OpenSSH, OpenSSL",40),
                new CustomerAssetSpec("RUBY","Ruby","APPLICATION_RUNTIME","应用运行时","Ubuntu Server","24.04","Ruby 3.3.5, OpenSSH, OpenSSL",30),
                new CustomerAssetSpec("POSTGRES","PostgreSQL","DATABASE","数据库服务","Red Hat Enterprise Linux","9.4","PostgreSQL 16.4, OpenSSH, OpenSSL",10),
                new CustomerAssetSpec("SHIRO","Shiro","APPLICATION_PLATFORM","应用运行平台","Red Hat Enterprise Linux","9.4","Apache Shiro 2.0.1, OpenSSH, OpenSSL",20),
                new CustomerAssetSpec("MONGODB","MongoDB","DATABASE","数据库服务","Rocky Linux","9.4","MongoDB 7.0, OpenSSH, OpenSSL",10)
        );
    }

    private void migrateLegacyAssets(List<CustomerAssetSpec> catalog){
        List<Asset> rows=new ArrayList<>(assets.findAll());
        if(rows.isEmpty())return;
        // Upgrade only Gazellio's previous demo records. Future CMDB-imported CIs keep their
        // original identity and are never rewritten just because their code is customer-defined.
        List<Asset> legacy=rows.stream().filter(a->!isCustomerCode(a.getAssetCode(),catalog)&&isLegacyDemoAsset(a)).toList();
        for(Asset asset:legacy)asset.setAssetCode("LEGACY-"+asset.getId()+"-"+UUID.randomUUID().toString().substring(0,8));
        if(!legacy.isEmpty())assets.saveAllAndFlush(legacy);
        int fallback=0;
        for(Asset asset:rows){
            if(!isCustomerCode(asset.getAssetCode(),catalog)&&!legacy.contains(asset))continue;
            CustomerAssetSpec spec=specFor(asset,catalog,fallback++);
            if(legacy.contains(asset))asset.setAssetCode(spec.code()+"-"+asset.getEnvironment().name()+"-M"+asset.getId());
            applyCustomerProfile(asset,spec);
        }
        assets.saveAll(rows);
    }

    private boolean isCustomerCode(String code,List<CustomerAssetSpec> catalog){
        if(code==null)return false;
        return catalog.stream().anyMatch(spec->code.matches("^"+spec.code()+"-(DEV|TEST|PREPROD|PROD)(-M\\d+)?$"));
    }

    private boolean isLegacyDemoAsset(Asset asset){
        String code=text(asset.getAssetCode()).toUpperCase(Locale.ROOT);
        String name=text(asset.getName());
        return code.matches("^(APP|WEB|WIN|JENKINS|TOMCAT|DB|VPN|ADC|DEVOPS|CMDB-PAY|CMDB-WEB|CMDB-WIN|CMDB-DB).*")
                ||name.contains("支付")||name.contains("互联网门户")||name.contains("核心 Windows")
                ||name.contains("客户服务")||name.contains("远程接入")||name.contains("入口网关")||name.contains("研发平台");
    }

    private CustomerAssetSpec specFor(Asset asset,List<CustomerAssetSpec> catalog,int fallback){
        String current=(text(asset.getAssetCode())+" "+text(asset.getName())+" "+text(asset.getInstalledProducts())).toLowerCase(Locale.ROOT);
        return catalog.stream().filter(spec->current.contains(spec.code().toLowerCase(Locale.ROOT))||current.contains(spec.product().toLowerCase(Locale.ROOT)))
                .findFirst().orElse(catalog.get(fallback%catalog.size()));
    }

    private void applyCustomerProfile(Asset asset,CustomerAssetSpec spec){
        asset.setName(spec.product());asset.setHostname(asset.getAssetCode().toLowerCase(Locale.ROOT));
        asset.setNetworkSegment(segmentFor(asset.getIpAddress()));asset.setAssetType(spec.assetType());
        asset.setZone(zoneFor(asset.getEnvironment()));
        asset.setInternetExposed(asset.getEnvironment()==EnvironmentType.PROD&&isExposedProduct(spec.code()));
        asset.setOsName(spec.osName());asset.setOsVersion(spec.osVersion());asset.setBusinessService(spec.businessService());
        asset.setInstalledProducts(spec.installedProducts());asset.setPatchBaseline(spec.code()+"-2026Q3");
        if(asset.getMaintenanceWindow()==null)asset.setMaintenanceWindow(windowFor(asset.getEnvironment()));
        if(asset.getAgentStatus()==null)asset.setAgentStatus("ONLINE");
        asset.setActive(true);
    }

    private void upsertCustomerAssets(List<CustomerAssetSpec> catalog,UserAccount ownerOps,UserAccount ownerApp){
        List<Asset> rows=new ArrayList<>();
        List<EnvironmentType> environments=List.of(EnvironmentType.TEST,EnvironmentType.PREPROD,EnvironmentType.PROD);
        for(int index=0;index<catalog.size();index++){
            CustomerAssetSpec spec=catalog.get(index);
            for(EnvironmentType environment:environments){
                String code=spec.code()+"-"+environment.name();
                String subnet="10."+switch(environment){case PROD->"60";case PREPROD->"65";default->"70";}+"."+spec.networkGroup();
                String ip=subnet+"."+(index+10);UserAccount owner=environment==EnvironmentType.PROD?ownerOps:ownerApp;
                Asset asset=assets.findByAssetCode(code).orElseGet(Asset::new);
                asset.setAssetCode(code);asset.setIpAddress(ip);asset.setEnvironment(environment);
                asset.setOwnerId(owner.getId());asset.setOwnerName(owner.getDisplayName());
                asset.setCriticality(environment==EnvironmentType.PROD?5:environment==EnvironmentType.PREPROD?4:3);
                asset.setAgentStatus((index+environment.ordinal())%17==0?"OFFLINE":"ONLINE");
                asset.setMaintenanceWindow(windowFor(environment));asset.setLastSeenAt(Instant.now().minusSeconds(index*17L));
                applyCustomerProfile(asset,spec);rows.add(asset);
            }
        }
        assets.saveAll(rows);
    }

    private String zoneFor(EnvironmentType environment){
        return switch(environment){case PROD->"生产数据中心";case PREPROD->"预生产资源区";case TEST->"测试资源区";default->"开发资源区";};
    }

    private String windowFor(EnvironmentType environment){return environment==EnvironmentType.PROD?"周日 01:00-05:00":"周三 20:00-23:00";}
    private boolean isExposedProduct(String code){return Set.of("TOMCAT","SPRINGBOOT","PHP","HTTPD","JETTY","STRUTS2","GRAFANA","JIRA","SHIRO").contains(code);}
    private String text(String value){return value==null?"":value;}

    private record CustomerAssetSpec(String code,String product,String assetType,String businessService,
                                     String osName,String osVersion,String installedProducts,int networkGroup){}

    private String segmentFor(String ip){
        if(ip==null||!ip.matches("\\d+\\.\\d+\\.\\d+\\.\\d+"))return null;
        String[] p=ip.split("\\.");return p[0]+"."+p[1]+"."+p[2]+".0/24";
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
        addPatch("fortios-7.4.8","Fortinet","FortiOS","7.4.8","FortiOS 安全固件更新","FortiOS security firmware update",640.0,true,List.of("CVE-2024-21762","CVE-2023-27997"));
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
        addPatch("screenconnect-23.9.8","ConnectWise","ScreenConnect","23.9.8","ScreenConnect 紧急安全更新","ScreenConnect emergency security update",180.0,true,List.of("CVE-2024-1709","CVE-2024-1708"));
        addPatch("ivanti-connect-secure-22.7R2.5","Ivanti","Connect Secure","22.7R2.5","Ivanti Connect Secure 安全累积更新","Ivanti Connect Secure cumulative security update",870.0,true,List.of("CVE-2023-46805","CVE-2024-21887","CVE-2024-21893"));
        addPatch("cisco-iosxe-17.9.4a","Cisco","IOS XE","17.9.4a","Cisco IOS XE Web UI 安全更新","Cisco IOS XE Web UI security update",620.0,true,List.of("CVE-2023-20198","CVE-2023-20273"));
        addPatch("checkpoint-r81.20-jumbo-take-65","Check Point","Security Gateway","R81.20 Take 65","Check Point Quantum Gateway 安全热修复","Check Point Quantum Gateway security hotfix",760.0,true,List.of("CVE-2024-24919"));
        addPatch("moveit-2023.0.4","Progress","MOVEit Transfer","2023.0.4","MOVEit Transfer 安全更新","MOVEit Transfer security update",420.0,true,List.of("CVE-2023-34362"));
        addPatch("ivanti-epmm-11.12.0.1","Ivanti","Endpoint Manager Mobile","11.12.0.1","Ivanti EPMM 安全更新","Ivanti EPMM security update",510.0,true,List.of("CVE-2023-35078"));
        addPatch("zyxel-zld-5.36-patch2","Zyxel","ZLD Firewall","5.36 Patch 2","Zyxel 防火墙 ZLD 安全更新","Zyxel firewall ZLD security update",310.0,true,List.of("CVE-2023-28771"));
        addPatch("vcenter-7.0u3p","VMware","vCenter Server","7.0 U3p","VMware vCenter Server 安全更新","VMware vCenter Server security update",6800.0,true,List.of("CVE-2021-21972"));
        addPatch("bigip-17.1.1.3","F5","BIG-IP","17.1.1.3","F5 BIG-IP iControl REST 安全更新","F5 BIG-IP iControl REST security update",1450.0,true,List.of("CVE-2022-1388"));
        addPatch("confluence-8.5.15-hf","Atlassian","Confluence","8.5.15 HF","Confluence 权限提升安全热修复","Confluence privilege escalation security hotfix",990.0,true,List.of("CVE-2023-22515"));
        // Keep imported catalog rows accurate without issuing one relation query per vulnerability.
        Set<String> supportedCves=new HashSet<>();
        patchCves.findAll().forEach(link->supportedCves.add(link.getCveId()));
        List<VulnerabilityDefinition> corrected=new ArrayList<>();
        for(VulnerabilityDefinition v:vulns.findAll()){
            boolean changed=false,available=supportedCves.contains(v.getCveId());
            if(v.isPatchAvailable()!=available){v.setPatchAvailable(available);changed=true;}
            if(v.getCvss()==null&&v.getSeverity()!=Severity.UNKNOWN){v.setSeverity(Severity.UNKNOWN);changed=true;}
            if(changed){v.setUpdatedAt(Instant.now());corrected.add(v);}
        }
        if(!corrected.isEmpty())vulns.saveAll(corrected);
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
        upsertScan("SCN-260929-001","生产数据库与中间件认证扫描","AUTHENTICATED","ENVIRONMENT","PROD","AGENT",ScanStatus.COMPLETED,100,18,120,70);
        upsertScan("SCN-260929-002","测试环境补丁效果复测","TARGETED_RESCAN","ENVIRONMENT","TEST","AGENT",ScanStatus.COMPLETED,100,3,60,35);
        upsertScan("SCN-260929-003","公网暴露应用服务扫描","NETWORK","CIDR","10.60.20.0/24","NONE",ScanStatus.RUNNING,64,5,22,null);
    }

    private void upsertScan(String jobNo,String name,String scanType,String targetType,String targetValue,String credential,
                            ScanStatus status,int progress,int findingCount,int startedMinutes,Integer completedMinutes){
        ScanJob job=scans.findByJobNo(jobNo).orElseGet(ScanJob::new);
        job.setJobNo(jobNo);job.setName(name);job.setScanType(scanType);job.setTargetType(targetType);job.setTargetValue(targetValue);
        job.setCredentialType(credential);job.setStatus(status);job.setProgress(progress);job.setFindingsCount(findingCount);job.setRequestedByName("王卫嘉");
        if(job.getStartedAt()==null)job.setStartedAt(Instant.now().minus(Duration.ofMinutes(startedMinutes)));
        if(completedMinutes!=null&&job.getCompletedAt()==null)job.setCompletedAt(Instant.now().minus(Duration.ofMinutes(completedMinutes)));
        scans.save(job);
    }

    private void seedFindingsTasksApprovals(){
        if(findings.count()>0) return;
        Map<String,Asset> a=new HashMap<>(); assets.findAll().forEach(x->a.put(x.getAssetCode(),x));
        ScanJob scan=scans.findTop100ByOrderByCreatedAtDesc().stream().filter(s->s.getStatus()==ScanStatus.COMPLETED).findFirst().orElseThrow();
        String[][] rows={
            {"OPENSSH-PROD","CVE-2024-6387","IN_REMEDIATION"},{"OPENSSH-PREPROD","CVE-2024-6386","CONFIRMED"},{"OPENSSL-TEST","CVE-2024-5535","IN_REMEDIATION"},
            {"OPENSSL-PROD","CVE-2023-0465","CONFIRMED"},{"TOMCAT-PROD","CVE-2025-24813","IN_REMEDIATION"},{"TOMCAT-TEST","CVE-2025-24813","CONFIRMED"},
            {"PHP-PROD","CVE-2024-4577","IN_REMEDIATION"},{"PHP-TEST","CVE-2024-4577","RESOLVED"},{"SPRINGBOOT-PREPROD","CVE-2024-38812","IN_REMEDIATION"},
            {"SPRINGBOOT-TEST","CVE-2022-22965","NEW"},{"HTTPD-PROD","CVE-2021-41773","IN_REMEDIATION"},{"HTTPD-TEST","CVE-2021-42013","EXEMPTED"},
            {"MYSQL-PROD","CVE-2022-0778","NEW"},{"REDIS-PROD","CVE-2023-0465","FALSE_POSITIVE"},{"JIRA-PROD","CVE-2024-6387","NEW"}
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
        createTask("OPENSSH-PROD","CVE-2024-6387","RHEL-RHSA-2026:7211",TaskStage.ASSIGNED,"P1",ChangeType.EMERGENCY);
        createTask("OPENSSL-TEST","CVE-2024-5535","openssl-3.5.2",TaskStage.APP_VERIFY,"P2",ChangeType.NORMAL);
        createTask("TOMCAT-PROD","CVE-2025-24813","apache-tomcat-11.0.12",TaskStage.RELEASE_APPROVAL,"P1",ChangeType.MAJOR);
        createTask("PHP-PROD","CVE-2024-4577","php-8.3.8",TaskStage.PROD_PATCH,"P1",ChangeType.EMERGENCY);
        createTask("SPRINGBOOT-PREPROD","CVE-2024-38812","spring-6.1.14",TaskStage.ASSIGNED,"P1",ChangeType.NORMAL);
        createTask("HTTPD-PROD","CVE-2021-41773","httpd-2.4.62",TaskStage.PREPROD_VERIFY,"P1",ChangeType.MAJOR);
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
        String[][] rows={{"OPENSSH-TEST","CVE-2024-6386"},{"OPENSSL-PREPROD","CVE-2023-0465"},{"OPENSSL-TEST","CVE-2022-0778"},{"HTTPD-PREPROD","CVE-2021-41773"}};
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

    private void upgradeFindingEvidence(){
        Map<Long,Asset> assetById=new HashMap<>();assets.findAll().forEach(a->assetById.put(a.getId(),a));
        Map<String,VulnerabilityDefinition> vulnById=new HashMap<>();vulns.findAll().forEach(v->vulnById.put(v.getCveId(),v));
        Map<Long,ScanJob> scanById=new HashMap<>();scans.findAll().forEach(s->scanById.put(s.getId(),s));
        List<Finding> changed=new ArrayList<>();
        for(Finding finding:findings.findAll()){
            Asset asset=assetById.get(finding.getAssetId());VulnerabilityDefinition vulnerability=vulnById.get(finding.getCveId());
            ScanJob scan=finding.getScanJobId()==null?null:scanById.get(finding.getScanJobId());
            if(asset==null||vulnerability==null)continue;
            String product=vulnerability.getProduct()==null?"Unknown component":vulnerability.getProduct();
            String observed=observedVersion(product,asset);
            String method=asset.getOsName()!=null&&asset.getOsName().toLowerCase(Locale.ROOT).contains("windows")
                    ?"Registry + signed package inventory + service fingerprint"
                    :"Authenticated package inventory + process fingerprint + version rule";
            String proof="SCAN EVIDENCE / 扫描证据\n"
                    +"evidence_id: EV-"+finding.getCveId()+"-"+asset.getAssetCode()+"\n"
                    +"scan_job: "+(scan==null?"AUTH-BASELINE":scan.getJobNo())+"\n"
                    +"scanner: Gazellio Agent 1.6.0\n"
                    +"policy: Authenticated Vulnerability Baseline v2026.09\n"
                    +"target: "+asset.getHostname()+" ("+asset.getIpAddress()+")\n"
                    +"asset_ci: "+asset.getAssetCode()+"\n"
                    +"transport: mTLS agent channel\n"
                    +"detection_rule: GZ-"+finding.getCveId()+"\n"
                    +"method: "+method+"\n"
                    +"component: "+product+"\n"
                    +"observed_version: "+observed+"\n"
                    +"installed_inventory: "+asset.getInstalledProducts()+"\n"
                    +"rule_result: observed version matched affected range\n"
                    +"service_state: running\n"
                    +"confidence: HIGH\n"
                    +"result: VULNERABLE\n"
                    +"collected_at: "+finding.getLastSeenAt()+"\n"
                    +"evidence_sha256: "+UUID.nameUUIDFromBytes((finding.getCveId()+asset.getAssetCode()).getBytes(StandardCharsets.UTF_8)).toString().replace("-","");
            double prioritized=Math.min(10.0,(vulnerability.getCvss()==null?5.0:vulnerability.getCvss())
                    +(asset.getCriticality()-3)*0.25+(vulnerability.isKev()?0.5:0)+(Boolean.TRUE.equals(asset.getInternetExposed())?0.75:0));
            boolean update=!proof.equals(finding.getEvidence())||!Objects.equals(finding.getRiskScore(),prioritized);
            if(update){finding.setEvidence(proof);finding.setRiskScore(prioritized);changed.add(finding);}
        }
        if(!changed.isEmpty())findings.saveAll(changed);
    }

    private String observedVersion(String product,Asset asset){
        String p=product.toLowerCase(Locale.ROOT);
        if(p.contains("openssh"))return "8.7p1-38.el9";
        if(p.contains("openssl"))return "3.0.7-28.el9";
        if(p.contains("tomcat"))return "9.0.86";
        if(p.contains("nginx"))return "1.24.0";
        if(p.contains("windows"))return "10.0.20348.2527";
        if(p.contains("redis"))return "7.2.4";
        if(p.contains("mysql"))return "8.0.36";
        return asset.getOsVersion()==null?"inventory match":asset.getOsVersion();
    }

    private void seedWorkOrders(){
        for(Finding f:findings.findAll()){
            Asset asset=assets.findById(f.getAssetId()).orElse(null);
            VulnerabilityDefinition vulnerability=vulns.findById(f.getCveId()).orElse(null);
            if(asset==null||vulnerability==null)continue;
            RemediationTask task=tasks.findByFindingId(f.getId()).orElse(null);
            // A scan finding remains in the confirmation queue. Older demo
            // versions created incidents at scan time; remove only those empty
            // legacy shells so the event appears after explicit confirmation.
            if((f.getStatus()==FindingStatus.NEW||f.getStatus()==FindingStatus.REOPENED)&&task==null){
                incidents.findByFindingId(f.getId()).ifPresent(incident->{
                    if(incident.getRemediationTaskId()==null&&incident.getChangeOrderId()==null){
                        incidents.delete(incident);
                        f.setSecurityIncidentId(null);
                        findings.save(f);
                    }
                });
                continue;
            }
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
                change.setSummary(f.getCveId()+" · "+asset.getBusinessService()+" 生产补丁发布");
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
