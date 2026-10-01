package com.gazellio.platform.config;

import com.gazellio.platform.model.*;
import com.gazellio.platform.repository.*;
import com.gazellio.platform.service.AccessControlService;
import com.gazellio.platform.service.PermissionCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

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
    private final PatchScheduleRepository patchSchedules;
    private final DeploymentTargetRepository deploymentTargets;
    private final SecurityIncidentRepository incidents;
    private final ChangeWorkOrderRepository changeOrders;
    private final SystemSettingRepository settings;
    private final AccessRoleRepository accessRoles;
    private final RolePermissionRepository rolePermissions;
    private final PasswordEncoder encoder;
    private final CmdbProperties cmdbProperties;

    @Value("${app.seed-demo-data:true}") private boolean seed;

    @Override
    public void run(String... args) throws Exception {
        if (!seed) return;
        seedUsers();
        seedAccessControl();
        seedVulnerabilities();
        seedPatches();
        enrichVulnerabilityKnowledge();
        seedPatchServers();
        // A CMDB-enabled deployment uses company configuration items as the operational
        // asset inventory. Do not rewrite historical demo assets/findings during every
        // rolling deployment: the old instance may still be advancing scans at this point.
        if(cmdbProperties.isEnabled()){
            seedTemplatesAndRuns();
            seedPatchSchedules();
            seedSettings();
            return;
        }
        seedAssets();
        seedAgentsAndScans();
        seedFindingsTasksApprovals();
        seedLowerSeverityFindings();
        upgradeFindingEvidence();
        seedWorkOrders();
        reconcileOperationalAssetReferences();
        seedTemplatesAndRuns();
        seedDeploymentTargets();
        seedPatchSchedules();
        seedSettings();
    }

    private void seedUsers() {
        upsertDemoUser("admin", "Gazellio Admin", "admin@gazellio.local", "平台管理部", "SYS-0001", UserRole.ADMIN);
        upsertDemoUser("security", "王卫嘉", "security@gazellio.local", "信息安全部", "SEC-0001", UserRole.SECURITY);
        upsertDemoUser("ops", "曾卫平", "ops@gazellio.local", "基础设施运维部", "OPS-0001", UserRole.OPS);
        upsertDemoUser("appowner", "陈佳宁", "appowner@gazellio.local", "应用管理部", "APP-0001", UserRole.APP_OWNER);
        upsertDemoUser("approver", "李明远", "approver@gazellio.local", "变更管理委员会", "CAB-0001", UserRole.APPROVER);
    }

    private void upsertDemoUser(String username,String name,String email,String department,String employeeNo,UserRole role){
        UserAccount user=users.findByUsername(username).orElse(null);
        boolean initializePassword=user==null||user.getAccessRoleId()==null;
        if(user==null)user=UserAccount.builder().username(username).createdAt(Instant.now()).build();
        user.setDisplayName(name);user.setEmail(email);user.setDepartment(department);user.setEmployeeNo(employeeNo);user.setRole(role);user.setEnabled(true);
        if(user.getFailedLoginAttempts()==null)user.setFailedLoginAttempts(0);if(user.getAccountType()==null)user.setAccountType("LOCAL");user.setUpdatedAt(Instant.now());
        if(initializePassword||user.getPasswordHash()==null||user.getPasswordHash().isBlank()){user.setPasswordHash(encoder.encode(AccessControlService.INITIAL_PASSWORD));user.setPasswordChangedAt(Instant.now());}
        users.save(user);
    }

    private void seedAccessControl(){
        Map<String,String[]> names=Map.of(
                "ADMIN",new String[]{"系统管理员","Administrator","管理系统配置、用户、角色与全部安全运营能力。","Manages system settings, users, roles and all security operations."},
                "SECURITY",new String[]{"安全管理员","Security Administrator","负责漏洞库、扫描、风险研判、安全事件与处置闭环。","Owns vulnerability intelligence, scanning, triage, incidents and remediation oversight."},
                "OPS",new String[]{"运维人员","Operations","负责补丁资产范围、自动化执行、回滚与生产复测。","Operates patch scopes, automated execution, rollback and production retesting."},
                "APP_OWNER",new String[]{"应用负责人","Application Owner","负责所属应用的影响确认、测试验证与例外说明。","Validates application impact, test results and exception justification for owned services."},
                "APPROVER",new String[]{"发布审批人","Release Approver","审核生产发布的风险、实施、回退与维护窗口。","Reviews production release risk, implementation, rollback and maintenance windows."});
        for(var entry:names.entrySet()){
            AccessRole role=accessRoles.findByCode(entry.getKey()).orElseGet(AccessRole::new);
            role.setCode(entry.getKey());role.setNameZh(entry.getValue()[0]);role.setNameEn(entry.getValue()[1]);
            if(role.getDescriptionZh()==null||role.getDescriptionZh().isBlank())role.setDescriptionZh(entry.getValue()[2]);
            if(role.getDescriptionEn()==null||role.getDescriptionEn().isBlank())role.setDescriptionEn(entry.getValue()[3]);
            role.setSystemRole(true);role.setEnabled(true);if(role.getDataScope()==null)role.setDataScope("ALL");role.setUpdatedAt(Instant.now());role=accessRoles.save(role);
            Set<String> existingPermissions=rolePermissions.findByRoleId(role.getId()).stream().map(RolePermission::getPermissionCode).collect(java.util.stream.Collectors.toSet());
            for(String permission:PermissionCatalog.defaults(role.getCode()))if(!existingPermissions.contains(permission))rolePermissions.save(RolePermission.builder().roleId(role.getId()).permissionCode(permission).build());
            Map<String,String> usernames=Map.of("ADMIN","admin","SECURITY","security","OPS","ops","APP_OWNER","appowner","APPROVER","approver");
            AccessRole assignedRole=role;users.findByUsername(usernames.get(role.getCode())).ifPresent(user->{user.setAccessRoleId(assignedRole.getId());users.save(user);});
        }
    }

    private void seedAssets() {
        var ownerOps=users.findByUsername("ops").orElseThrow();
        var ownerApp=users.findByUsername("appowner").orElseThrow();
        List<CustomerAssetSpec> catalog=customerAssetCatalog();
        migrateLegacyAssets(catalog);
        upsertCustomerAssets(catalog,ownerOps,ownerApp);
        retireGeneratedLegacyAssets();
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

    private void retireGeneratedLegacyAssets(){
        List<Asset> retired=assets.findAll().stream()
                .filter(a->text(a.getAssetCode()).matches(".*-(DEV|TEST|PREPROD|PROD)-M\\d+$"))
                .filter(Asset::isActive)
                .peek(a->a.setActive(false)).toList();
        if(!retired.isEmpty())assets.saveAll(retired);
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
        retireInvalidDemoPatches();
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

    private void retireInvalidDemoPatches(){
        List<Patch> changed=patches.findAll().stream()
                .filter(p->Set.of("1213","1231").contains(text(p.getPatchId()))
                        || (text(p.getTitleZh()).equals("1231")&&text(p.getVendor()).equals("123")))
                .peek(p->p.setStatus("RETIRED")).toList();
        if(!changed.isEmpty())patches.saveAll(changed);
    }

    /**
     * Builds the offline vulnerability knowledge base used by the vulnerability-library detail page.
     * Finding details remain instance evidence; these fields describe the CVE itself and are persisted so
     * the UI never has to invent generic guidance at request time.
     */
    private void enrichVulnerabilityKnowledge(){
        List<VulnerabilityDefinition> rows=vulns.findAll();
        if(rows.isEmpty())return;
        Map<Long,Patch> patchById=new HashMap<>();
        patches.findAll().forEach(p->patchById.put(p.getId(),p));
        Map<String,Patch> patchByCve=new HashMap<>();
        for(PatchCve link:patchCves.findAll()){
            Patch patch=patchById.get(link.getPatchId());
            if(patch!=null)patchByCve.putIfAbsent(link.getCveId(),patch);
        }
        Instant analyzedAt=Instant.now();
        for(VulnerabilityDefinition v:rows){
            Patch patch=patchByCve.get(v.getCveId());
            String product=text(v.getProduct()).isBlank()?"目标组件":v.getProduct();
            String titleEn=text(v.getTitleEn()).toLowerCase(Locale.ROOT);
            String titleZh=text(v.getTitleZh());
            String cwe=cweFor(titleEn);
            String vector=attackVectorFor(titleEn,product);
            String complexity=titleEn.contains("race")||titleEn.contains("signal handling")?"HIGH":"LOW";
            String privileges=titleEn.contains("privilege")||titleEn.contains("elevation")?"LOW":"NONE";
            String interaction=titleEn.contains("hash disclosure")?"REQUIRED":"NONE";
            String impactKind=impactKind(titleEn);
            String componentZh=componentZh(product,titleEn);
            String componentEn=componentEn(product,titleEn);
            String fixed=patch==null?null:patch.getVersion();
            boolean web=webMitigationSupported(product,titleEn);
            String affectedRange=fixed==null
                    ?"以资产指纹、厂商公告及扫描规则的受影响版本条件为准；当前尚无已验证修复版本。"
                    :"低于已验证修复版本 "+fixed+" 的受支持分支需按适用性规则核验；分支回移补丁以补丁详情为准。";
            String affectedRangeEn=fixed==null
                    ?"Evaluate the installed fingerprint against the archived vendor advisory and the local detection rule; no verified fixed build is currently recorded."
                    :"Supported branches below verified fixed build "+fixed+" require evaluation against the local applicability rule; consult patch details for backported builds.";
            String impactZh=impactZh(impactKind,product);
            String impactEn=impactEn(impactKind,product);
            String mechanismZh=mechanismZh(v.getCveId(),product,titleEn);
            String mechanismEn=mechanismEn(v.getCveId(),product,titleEn);
            String patchCode=patch==null?null:patch.getPatchId();

            v.setCweId(cwe);
            v.setAttackVector(vector);
            v.setAttackComplexity(complexity);
            v.setPrivilegesRequired(privileges);
            v.setUserInteraction(interaction);
            v.setCvssVector(cvssVector(vector,complexity,privileges,interaction,impactKind));
            v.setExploitMaturity(v.isKev()?"ACTIVE_EXPLOIT":(v.getCvss()!=null&&v.getCvss()>=9?"PUBLIC_TECHNICAL_DETAILS":"NO_CONFIRMED_EXPLOIT"));
            v.setAffectedComponentsZh(componentZh);
            v.setAffectedComponentsEn(componentEn);
            v.setAffectedVersionRangeZh(affectedRange);
            v.setAffectedVersionRangeEn(affectedRangeEn);
            v.setFixedVersion(fixed);
            v.setImpactZh(impactZh);
            v.setImpactEn(impactEn);
            v.setScannerRuleId("GZ-VULN-"+v.getCveId().replace("CVE-", ""));
            v.setDescriptionZh(titleZh+"。"+mechanismZh+"。受影响位置为 "+componentZh+"。利用成功后，"+impactZh+"。 ");
            v.setDescriptionEn(v.getTitleEn()+". "+mechanismEn+". The affected area is the "+componentEn+". If exploitation succeeds, "+impactEn+".");
            v.setDetectionGuidanceZh("1. 通过认证扫描采集 "+product+" 的软件包版本、进程参数、服务端口和组件指纹。\n2. 使用规则 "+v.getScannerRuleId()+" 将观测版本与受影响版本条件比对，并检查漏洞相关配置或接口是否可达。\n3. 保存原始探测结果、资产CI、采集时间和证据摘要；仅有端口开放不能直接判定漏洞成立。 ");
            v.setDetectionGuidanceEn("1. Use authenticated collection for the "+product+" package version, process arguments, service port, and component fingerprint.\n2. Apply rule "+v.getScannerRuleId()+" to compare the observed build with the affected-version condition and test whether the vulnerable configuration or interface is reachable.\n3. Retain raw probe output, asset CI, collection time, and evidence digest; an open port alone is not sufficient proof.");
            v.setRemediationGuidanceZh(patch==null
                    ?"当前内部补丁库尚无已验证修复包。先执行临时缓解，登记风险例外和到期时间，持续监控厂商修复版本；补丁入库后必须先在测试环境验证。"
                    :"使用内部补丁 "+patchCode+"（修复版本 "+fixed+"）执行灰度修复。安装前校验适用性和回退点，测试环境通过应用验证与定向复测后，再按审批窗口分批进入生产。 ");
            v.setRemediationGuidanceEn(patch==null
                    ?"No verified package is available in the internal repository. Apply temporary controls, record a time-bound exception, and monitor for the vendor fix; validate any new package in test first."
                    :"Deploy internal patch "+patchCode+" (fixed build "+fixed+") by release ring. Validate applicability and a rollback point before installation; require application validation and a targeted test retest before approved production rollout.");
            v.setMitigationZh(web
                    ?"补丁窗口前，在WAF或反向代理按该CVE的请求路径、参数和协议特征部署观察规则；确认无误报后切换阻断，同时限制管理接口来源并加强异常请求告警。"
                    :"补丁窗口前，通过ACL或主机防火墙限制受影响服务来源，关闭非必要接口与功能，收紧最小权限并对异常进程、崩溃和访问日志启用告警。 ");
            v.setMitigationEn(web
                    ?"Before patching, deploy a CVE-specific monitor rule on the WAF or reverse proxy for request paths, parameters, and protocol indicators; switch to blocking after false-positive review, restrict management sources, and alert on abnormal requests."
                    :"Before patching, restrict the vulnerable service with ACLs or host firewall rules, disable unnecessary interfaces and features, enforce least privilege, and alert on abnormal processes, crashes, and access logs.");
            v.setVirtualPatchAvailable(web);
            v.setVirtualPatchGuidanceZh(web
                    ?"可生成虚拟补丁策略：先在观察模式记录命中资产、URI、参数和来源地址，经过业务负责人确认后转为阻断；策略必须设置失效日期并随正式补丁复测结果撤销。"
                    :"该漏洞不适合用WAF规则作为主要控制。系统应改用网络隔离、服务降级、功能关闭或主机级防护，并保留风险例外审批。 ");
            v.setVirtualPatchGuidanceEn(web
                    ?"A virtual-patch policy can be generated in monitor mode to capture asset, URI, parameter, and source details. Move to blocking after owner validation, set an expiry, and retire it after the permanent patch passes retest."
                    :"A WAF rule is not an appropriate primary control for this weakness. Use isolation, service degradation, feature disablement, or host controls and retain an approved risk exception.");
            v.setEvidenceRequirementsZh("关闭前必须留存：① 安装前后版本或补丁清单；② 补丁包签名与SHA-256校验结果；③ 应用健康检查和关键交易验证；④ 规则 "+v.getScannerRuleId()+" 的定向复测结果；⑤ 执行时间、目标资产、执行人及失败/回退记录。 ");
            v.setEvidenceRequirementsEn("Closure evidence must include: (1) before/after version or patch inventory; (2) package signature and SHA-256 verification; (3) application health and critical-transaction validation; (4) targeted retest output from "+v.getScannerRuleId()+"; and (5) execution time, target asset, operator, and any failure or rollback record.");
            v.setIntelligenceSources(v.isKev()?"NVD local mirror · CISA KEV local mirror · vendor advisory archive":"NVD local mirror · vendor advisory archive");
            if(v.getPublishedDate()==null)v.setPublishedDate(LocalDate.now().minusDays(90));
            v.setLastAnalyzedAt(analyzedAt);
            v.setUpdatedAt(analyzedAt);
            v.setPatchAvailable(patch!=null);
        }
        vulns.saveAll(rows);
    }

    private String cweFor(String title){
        if(title.contains("sql injection"))return "CWE-89";
        if(title.contains("command injection")||title.contains("ognl")||title.contains("argument injection"))return "CWE-78";
        if(title.contains("path traversal"))return "CWE-22";
        if(title.contains("authentication bypass"))return "CWE-288";
        if(title.contains("buffer")||title.contains("out-of-bounds"))return "CWE-787";
        if(title.contains("use-after-free"))return "CWE-416";
        if(title.contains("server-side request forgery"))return "CWE-918";
        if(title.contains("denial of service"))return "CWE-400";
        if(title.contains("file read")||title.contains("disclosure")||title.contains("hash"))return "CWE-200";
        if(title.contains("privilege")||title.contains("elevation"))return "CWE-269";
        if(title.contains("supply-chain"))return "CWE-506";
        return "CWE-20";
    }

    private String attackVectorFor(String title,String product){
        String value=(title+" "+product).toLowerCase(Locale.ROOT);
        return value.contains("kernel")||value.contains("clfs")||value.contains("privilege escalation")||value.contains("elevation of privilege")?"LOCAL":"NETWORK";
    }

    private String impactKind(String title){
        if(title.contains("denial of service"))return "AVAILABILITY";
        if(title.contains("disclosure")||title.contains("file read")||title.contains("hash")||title.contains("server-side request forgery"))return "CONFIDENTIALITY";
        if(title.contains("path traversal"))return "FILE_ACCESS";
        if(title.contains("privilege")||title.contains("elevation"))return "PRIVILEGE";
        return "FULL_CONTROL";
    }

    private String componentZh(String product,String title){
        if(title.contains("path traversal")||title.contains("file read"))return product+" 的请求路径规范化与文件访问处理模块";
        if(title.contains("command injection")||title.contains("ognl")||title.contains("argument injection"))return product+" 的请求解析与命令执行链路";
        if(title.contains("authentication bypass"))return product+" 的身份认证与授权边界";
        if(title.contains("buffer")||title.contains("out-of-bounds"))return product+" 的输入解析与内存边界处理模块";
        if(title.contains("privilege")||title.contains("elevation"))return product+" 的本地权限边界与系统服务";
        if(title.contains("denial of service"))return product+" 的协议解析与资源处理模块";
        return product+" 的对外服务接口与受影响组件";
    }

    private String componentEn(String product,String title){
        if(title.contains("path traversal")||title.contains("file read"))return product+" request path normalization and file-access handler";
        if(title.contains("command injection")||title.contains("ognl")||title.contains("argument injection"))return product+" request parsing and command-execution path";
        if(title.contains("authentication bypass"))return product+" authentication and authorization boundary";
        if(title.contains("buffer")||title.contains("out-of-bounds"))return product+" input parser and memory-boundary handling";
        if(title.contains("privilege")||title.contains("elevation"))return product+" local privilege boundary and system service";
        if(title.contains("denial of service"))return product+" protocol parser and resource-handling path";
        return product+" exposed service interface and vulnerable component";
    }

    private String mechanismZh(String cve,String product,String title){
        return switch(cve){
            case "CVE-2025-24813"->"在特定配置下，Tomcat 默认 Servlet 对部分 PUT 请求的处理与持久化会话机制可被组合利用，攻击者可能写入恶意内容并触发反序列化";
            case "CVE-2024-6387"->"OpenSSH 服务端在超时信号处理过程中存在竞态条件，未认证攻击者可通过大量连接反复触发异常时序，在部分受影响系统上造成远程代码执行风险";
            case "CVE-2024-4577"->"Windows 环境中的字符编码转换可能把特定字节转换为命令行选项前缀，从而绕过 PHP-CGI 参数过滤并向解释器注入参数";
            case "CVE-2024-23897"->"Jenkins CLI 的参数解析功能会展开以 @ 开头的文件参数，具有 CLI 访问能力的攻击者可借此读取控制器上的文件内容";
            case "CVE-2024-5535"->"OpenSSL 在处理特定 TLS 数据时存在边界检查缺陷，恶意对端可诱导进程读取缓冲区边界之外的内存";
            case "CVE-2024-3094"->"受污染的 xz/liblzma 构建产物包含供应链后门逻辑，特定运行环境下可能干预认证流程并形成远程入侵入口";
            case "CVE-2024-3400"->"PAN-OS GlobalProtect 功能对外部输入的处理不安全，未认证攻击者可通过构造请求在设备上执行操作系统命令";
            case "CVE-2023-4966"->"NetScaler 对特定请求的内存边界校验不足，攻击者可读取进程内存并获取会话令牌等敏感信息";
            case "CVE-2023-38545"->"curl 的 SOCKS5 代理主机名解析路径存在堆缓冲区溢出条件，超长主机名在特定配置下可能破坏进程内存";
            case "CVE-2022-0778"->"OpenSSL 在解析包含畸形椭圆曲线参数的证书时，BN_mod_sqrt 计算可能进入无限循环并持续占用处理资源";
            case "CVE-2022-22965"->"Spring MVC 数据绑定机制在特定 JDK、Servlet 容器和部署方式组合下可被构造请求滥用，进而修改对象属性并形成代码执行链";
            case "CVE-2021-44228"->"Log4j2 对可控日志字符串执行 JNDI 查找，攻击者可通过精心构造的输入诱导应用访问外部命名服务并加载恶意内容";
            case "CVE-2021-41773","CVE-2021-42013"->"Apache HTTP Server 的路径规范化存在缺陷，编码后的路径片段可能绕过目录限制；在启用相关CGI配置时风险可进一步扩大为代码执行";
            default->genericMechanismZh(product,title);
        };
    }

    private String mechanismEn(String cve,String product,String title){
        return switch(cve){
            case "CVE-2025-24813"->"Under specific configurations, Tomcat default-servlet partial PUT handling can be combined with persistent sessions to write malicious content and trigger deserialization";
            case "CVE-2024-6387"->"A race condition in OpenSSH server timeout-signal handling can be repeatedly triggered by an unauthenticated attacker and may lead to remote code execution on affected platforms";
            case "CVE-2024-4577"->"On Windows, character-set conversion can transform crafted bytes into a command-line option prefix, bypass PHP-CGI argument filtering, and inject interpreter options";
            case "CVE-2024-23897"->"Jenkins CLI argument parsing expands file arguments prefixed with @, allowing an attacker with CLI access to read controller-side files";
            case "CVE-2024-5535"->"A boundary-validation flaw in OpenSSL processing of specific TLS data can cause a malicious peer to trigger an out-of-bounds memory read";
            case "CVE-2024-3094"->"Compromised xz/liblzma build artifacts contain supply-chain backdoor logic that can interfere with authentication flows in specific environments";
            case "CVE-2024-3400"->"PAN-OS GlobalProtect processes external input unsafely, allowing an unauthenticated attacker to submit a crafted request that executes an operating-system command";
            case "CVE-2023-4966"->"Insufficient memory-boundary validation in NetScaler request processing can expose process memory, including session tokens and other sensitive material";
            case "CVE-2023-38545"->"The curl SOCKS5 proxy hostname path can overflow a heap buffer when an overlong hostname is processed under specific configuration conditions";
            case "CVE-2022-0778"->"Parsing a certificate with malformed elliptic-curve parameters can cause OpenSSL BN_mod_sqrt computation to loop indefinitely and consume processing resources";
            case "CVE-2022-22965"->"Under a specific combination of JDK, servlet container, and deployment conditions, Spring MVC data binding can be abused to modify object properties and construct a code-execution chain";
            case "CVE-2021-44228"->"Log4j2 performs JNDI lookups on attacker-controlled log strings, which can make an application contact an external naming service and load malicious content";
            case "CVE-2021-41773","CVE-2021-42013"->"A path-normalization flaw in Apache HTTP Server allows encoded path segments to bypass directory restrictions and can progress to code execution when related CGI functionality is enabled";
            default->genericMechanismEn(product,title);
        };
    }

    private String genericMechanismZh(String product,String title){
        if(title.contains("authentication bypass"))return product+" 对特定请求或状态的身份校验不完整，攻击者可绕过正常认证流程访问受保护功能";
        if(title.contains("path traversal"))return product+" 对编码路径和目录边界的规范化校验不足，攻击者可构造路径访问预期目录之外的资源";
        if(title.contains("command injection")||title.contains("ognl")||title.contains("argument injection"))return product+" 未正确隔离外部输入与命令或表达式执行上下文，构造输入可能被当作指令执行";
        if(title.contains("buffer")||title.contains("out-of-bounds"))return product+" 在解析异常输入时缺少完整的长度与边界检查，可能访问或覆盖非预期内存区域";
        if(title.contains("denial of service"))return product+" 对异常输入或高成本计算缺少资源限制，攻击者可诱导服务耗尽资源或停止响应";
        if(title.contains("server-side request forgery"))return product+" 可被诱导代表攻击者访问非预期的内部或外部地址，造成边界绕过和敏感信息暴露";
        if(title.contains("privilege")||title.contains("elevation"))return product+" 的权限边界检查存在缺陷，低权限主体可执行仅允许高权限上下文完成的操作";
        return product+" 在处理特制输入时存在安全校验缺陷，攻击者可通过可达接口触发非预期行为";
    }

    private String genericMechanismEn(String product,String title){
        if(title.contains("authentication bypass"))return product+" incompletely validates authentication state for specific requests, allowing access to protected functions outside the normal sign-in flow";
        if(title.contains("path traversal"))return product+" does not fully normalize encoded paths and directory boundaries, allowing a crafted path to reach resources outside the intended directory";
        if(title.contains("command injection")||title.contains("ognl")||title.contains("argument injection"))return product+" fails to isolate external input from a command or expression context, allowing crafted data to be interpreted as executable instructions";
        if(title.contains("buffer")||title.contains("out-of-bounds"))return product+" lacks complete length and boundary checks while parsing malformed input, which may access or overwrite unintended memory";
        if(title.contains("denial of service"))return product+" does not adequately constrain abnormal input or expensive computation, allowing resource exhaustion or loss of service";
        if(title.contains("server-side request forgery"))return product+" can be induced to access unintended internal or external destinations on an attacker's behalf";
        if(title.contains("privilege")||title.contains("elevation"))return product+" contains a privilege-boundary validation flaw that allows a lower-privileged principal to perform higher-privileged operations";
        return product+" contains an input-validation weakness that can be triggered through a reachable interface to cause unintended behavior";
    }

    private String impactZh(String kind,String product){
        return switch(kind){
            case "AVAILABILITY"->"攻击者可触发 "+product+" 服务异常退出或资源耗尽，造成业务中断";
            case "CONFIDENTIALITY"->"攻击者可读取敏感数据、会话信息或内部资源，扩大后续横向移动风险";
            case "FILE_ACCESS"->"攻击者可绕过路径限制访问或写入非预期文件，并可能进一步执行代码";
            case "PRIVILEGE"->"具备本地访问条件的攻击者可提升权限，取得系统级控制能力";
            default->"远程攻击者可能执行未授权操作或代码，影响数据机密性、完整性与服务可用性";
        };
    }

    private String impactEn(String kind,String product){
        return switch(kind){
            case "AVAILABILITY"->"an attacker may crash or exhaust the "+product+" service and interrupt business availability";
            case "CONFIDENTIALITY"->"an attacker may read sensitive data, session material, or internal resources and enable further lateral movement";
            case "FILE_ACCESS"->"an attacker may bypass path restrictions to read or write unintended files and potentially progress to code execution";
            case "PRIVILEGE"->"an attacker with local access may cross a privilege boundary and obtain system-level control";
            default->"a remote attacker may perform unauthorized actions or execute code, affecting confidentiality, integrity, and availability";
        };
    }

    private String cvssVector(String vector,String complexity,String privileges,String interaction,String impact){
        String impacts="AVAILABILITY".equals(impact)?"C:N/I:N/A:H":("CONFIDENTIALITY".equals(impact)?"C:H/I:N/A:N":"C:H/I:H/A:H");
        return "CVSS:3.1/AV:"+("LOCAL".equals(vector)?"L":"N")+"/AC:"+("HIGH".equals(complexity)?"H":"L")+
                "/PR:"+("LOW".equals(privileges)?"L":"N")+"/UI:"+("REQUIRED".equals(interaction)?"R":"N")+"/S:U/"+impacts;
    }

    private boolean webMitigationSupported(String product,String title){
        String value=(product+" "+title).toLowerCase(Locale.ROOT);
        return List.of("tomcat","http server","sharepoint","exchange","php","spring","confluence","jenkins","screenconnect","teamcity","moveit","vcenter")
                .stream().anyMatch(value::contains);
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
        List<Asset> active=assets.findByActiveTrueOrderByNameAsc();
        List<ScanAgent> agentRows=agents.findAll();
        for(int index=0;index<agentRows.size()&&!active.isEmpty();index++){
            ScanAgent agent=agentRows.get(index);
            Asset asset=active.get(index%active.size());
            agent.setAssetId(asset.getId());agent.setHostname(asset.getHostname());agent.setIpAddress(asset.getIpAddress());agent.setOsName(asset.getOsName());
            agent.setLastHeartbeatAt(index%4==0?Instant.now().minus(Duration.ofMinutes(18)):Instant.now().minus(Duration.ofMinutes(index+1)));
            agent.setStatus(index%4==0?AgentStatus.OFFLINE:AgentStatus.ONLINE);agents.save(agent);
        }
        upsertScan("SCN-260929-001","生产数据库与中间件认证扫描","AUTHENTICATED","ENVIRONMENT","PROD","AGENT",ScanStatus.COMPLETED,100,18,120,70);
        upsertScan("SCN-260929-002","测试环境补丁效果复测","TARGETED_RESCAN","ENVIRONMENT","TEST","AGENT",ScanStatus.COMPLETED,100,3,60,35);
        upsertScan("SCN-260929-003","公网暴露应用服务扫描","NETWORK","CIDR","10.60.20.0/24","NONE",ScanStatus.RUNNING,64,5,22,null);
    }

    private void upsertScan(String jobNo,String name,String scanType,String targetType,String targetValue,String credential,
                            ScanStatus status,int progress,int findingCount,int startedMinutes,Integer completedMinutes){
        ScanJob job=scans.findByJobNo(jobNo).orElseGet(ScanJob::new);
        boolean created=job.getId()==null;
        job.setJobNo(jobNo);job.setName(name);job.setScanType(scanType);job.setTargetType(targetType);job.setTargetValue(targetValue);
        job.setCredentialType(credential);job.setRequestedByName(users.findByUsername("security").map(UserAccount::getDisplayName).orElse("security"));
        if(created){
            job.setStatus(status);job.setProgress(progress);job.setFindingsCount(findingCount);
            job.setStartedAt(Instant.now().minus(Duration.ofMinutes(startedMinutes)));
            if(completedMinutes!=null)job.setCompletedAt(Instant.now().minus(Duration.ofMinutes(completedMinutes)));
        }
        scans.save(job);
    }

    private void seedFindingsTasksApprovals(){
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
            if(findings.findByAssetIdAndCveId(x.getId(),v.getCveId()).isPresent()){n++;continue;}
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
        if(tasks.findByFindingId(f.getId()).isPresent())return;
        RemediationTask t=tasks.save(RemediationTask.builder().taskNo("RMD-PENDING-"+UUID.randomUUID()).findingId(f.getId()).patchId(p.getId()).assetId(a.getId()).ownerId(a.getOwnerId()).ownerName(a.getOwnerName()).priority(priority).stage(stage).status(stage==TaskStage.CLOSED?TaskStatus.COMPLETED:TaskStatus.IN_PROGRESS).changeType(type).dueAt(Instant.now().plus(Duration.ofDays(priority.equals("P1")?2:7))).build());
        t.setTaskNo("RMD-"+String.format("%06d",t.getId()));tasks.save(t);
        f.setRemediationTaskId(t.getId()); f.setStatus(stage==TaskStage.CLOSED?FindingStatus.RESOLVED:FindingStatus.IN_REMEDIATION); findings.save(f);
        if(stage==TaskStage.RELEASE_APPROVAL){
            UserAccount requester=users.findByUsername("security").orElseThrow();
            UserAccount operations=users.findByUsername("ops").orElseThrow();
            UserAccount releaseApprover=users.findByUsername("approver").orElseThrow();
            ApprovalRequest ar=approvals.save(ApprovalRequest.builder().approvalNo("APR-"+String.format("%06d",approvals.count()+1)).taskId(t.getId()).changeType(type).status(ApprovalStatus.PENDING).currentStep(1).requestedById(requester.getId()).requestedByName(requester.getDisplayName()).reason("测试环境验证与复测通过，申请进入生产发布。 ").rollbackPlan("失败自动暂停；恢复快照或回退补丁版本。 ").build());
            approvalSteps.save(ApprovalStep.builder().approvalId(ar.getId()).stepOrder(1).roleNameZh("运维负责人").roleNameEn("Operations Lead").approverId(operations.getId()).approverName(operations.getDisplayName()).status(ApprovalStepStatus.PENDING).build());
            approvalSteps.save(ApprovalStep.builder().approvalId(ar.getId()).stepOrder(2).roleNameZh("发布审批人").roleNameEn("Release Approver").approverId(releaseApprover.getId()).approverName(releaseApprover.getDisplayName()).status(ApprovalStepStatus.WAITING).build());
            t.setApprovalId(ar.getId()); tasks.save(t);
        }
    }

    private void seedPatchSchedules(){
        if(patchSchedules.count()>0)return;
        UserAccount ops=users.findByUsername("ops").orElse(null);
        List<Patch> available=patches.findActiveCatalog();
        if(available.isEmpty())return;
        ZonedDateTime first=ZonedDateTime.now().plusDays(3).withHour(22).withMinute(0).withSecond(0).withNano(0);
        ZonedDateTime second=ZonedDateTime.now().plusDays(10).withHour(21).withMinute(30).withSecond(0).withNano(0);
        patchSchedules.save(PatchSchedule.builder().titleZh("生产环境月度补丁窗口").titleEn("Production Monthly Patch Window")
                .patchId(available.get(0).getId()).environment("PROD").startAt(first.toInstant()).endAt(first.plusHours(3).toInstant())
                .status("APPROVED").ownerId(ops==null?null:ops.getId()).ownerName(ops==null?"ops":ops.getDisplayName())
                .createdById(ops==null?null:ops.getId()).createdByName(ops==null?"ops":ops.getDisplayName()).notes("Ring 0 验证后按批次发布。 ").build());
        if(available.size()>1)patchSchedules.save(PatchSchedule.builder().titleZh("预生产兼容性验证窗口").titleEn("Pre-production Compatibility Window")
                .patchId(available.get(1).getId()).environment("PREPROD").startAt(second.toInstant()).endAt(second.plusHours(2).toInstant())
                .status("PLANNED").ownerId(ops==null?null:ops.getId()).ownerName(ops==null?"ops":ops.getDisplayName())
                .createdById(ops==null?null:ops.getId()).createdByName(ops==null?"ops":ops.getDisplayName()).notes("执行安装、健康检查和定向复测。 ").build());
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
            String currentEvidence=finding.getEvidence();
            boolean legacy=currentEvidence==null||currentEvidence.isBlank()
                    ||currentEvidence.startsWith("agent-package-match:")
                    ||currentEvidence.startsWith("authenticated-package-version:");
            if(!legacy)continue;
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

    /**
     * A finding is the authoritative asset-to-vulnerability relation. Earlier demo versions
     * copied the asset id into tasks and work orders, so records could drift after CMDB data was
     * migrated. Reconcile only those operational references; no imported CMDB identity is changed.
     */
    private void reconcileOperationalAssetReferences(){
        Map<Long,Finding> findingById=new HashMap<>();findings.findAll().forEach(f->findingById.put(f.getId(),f));
        Map<Long,Asset> assetById=new HashMap<>();assets.findAll().forEach(a->assetById.put(a.getId(),a));
        List<RemediationTask> changedTasks=new ArrayList<>();
        for(RemediationTask task:tasks.findAll()){
            Finding finding=findingById.get(task.getFindingId());
            if(finding==null)continue;
            Asset asset=assetById.get(finding.getAssetId());
            if(asset==null)continue;
            boolean changed=!Objects.equals(task.getAssetId(),asset.getId())
                    ||!Objects.equals(task.getOwnerId(),asset.getOwnerId())
                    ||!Objects.equals(task.getOwnerName(),asset.getOwnerName());
            if(changed){task.setAssetId(asset.getId());task.setOwnerId(asset.getOwnerId());task.setOwnerName(asset.getOwnerName());task.setUpdatedAt(Instant.now());changedTasks.add(task);}
        }
        if(!changedTasks.isEmpty())tasks.saveAll(changedTasks);
        List<SecurityIncident> changedIncidents=new ArrayList<>();
        for(SecurityIncident incident:incidents.findAll()){
            Finding finding=findingById.get(incident.getFindingId());
            if(finding==null||Objects.equals(incident.getAssetId(),finding.getAssetId()))continue;
            Asset asset=assetById.get(finding.getAssetId());
            incident.setAssetId(finding.getAssetId());
            if(asset!=null){incident.setOwnerId(asset.getOwnerId());incident.setOwnerName(asset.getOwnerName());}
            incident.setUpdatedAt(Instant.now());changedIncidents.add(incident);
        }
        if(!changedIncidents.isEmpty())incidents.saveAll(changedIncidents);
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
                new String[]{"LOCAL_STATE","记录本地补丁验证状态","Record local patch verification state"},
                new String[]{"VULN_CLOSE","关闭漏洞实例","Close vulnerability finding"}
            ));
        }
        templates.findByCode("VULN-PATCH-CLOSED-LOOP").ifPresent(template->{
            List<OrchestrationTemplateStep> steps=templateSteps.findByTemplateIdOrderByStepOrderAsc(template.getId());
            boolean changed=false;
            for(OrchestrationTemplateStep step:steps)if("CMDB_UPDATE".equals(step.getCode())){
                step.setCode("LOCAL_STATE");step.setNameZh("记录本地补丁验证状态");step.setNameEn("Record local patch verification state");changed=true;
            }
            if(changed)templateSteps.saveAll(steps);
        });
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
            if(template.getId()!=null){templateSteps.deleteByTemplateId(template.getId());}
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
