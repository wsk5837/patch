package com.gazellio.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class McpReadService {
    private final CurrentUserService currentUser;
    private final AccessControlService accessControl;
    private final GlobalSearchService search;
    private final AssetService assets;
    private final FindingService findings;
    private final PatchService patches;
    private final ApprovalService approvals;
    private final DashboardReportService reports;
    private final AuditService audit;
    private final ObjectMapper mapper;

    public List<Map<String,Object>> tools(){
        return List.of(
                tool("get_current_user","查询当前用户和权限","返回当前 MCP 委托用户、角色与生效权限。",schema(Map.of())),
                tool("search_system_data","搜索系统数据","按关键字搜索当前用户有权查看的漏洞、资产、任务和补丁。",schema(Map.of("query",string("搜索关键字")),"query")),
                tool("list_assets","查询资产列表","模糊查询真实 CMDB 镜像资产。需要 ASSET_VIEW。",schema(Map.of("query",string("名称、编号、IP、网段或业务系统"),"page",integer("页码，从 1 开始"),"size",integer("每页数量，最大 100")))),
                tool("get_asset","查询资产详情","按数据库业务 ID 查询单个资产。需要 ASSET_VIEW。",schema(Map.of("id",integer("资产业务 ID")),"id")),
                tool("list_vulnerabilities","查询漏洞库","查询漏洞知识库，可按关键字、等级和是否 KEV 过滤。需要 VULNERABILITY_VIEW。",schema(Map.of("query",string("CVE、标题、厂商或产品"),"severity",string("CRITICAL/HIGH/MEDIUM/LOW/UNKNOWN"),"kev",bool("是否只查 KEV"),"page",integer("页码，从 1 开始"),"size",integer("每页数量，最大 100")))),
                tool("get_vulnerability","查询漏洞详情","按 CVE 查询漏洞、修复版本和补丁映射。需要 VULNERABILITY_VIEW。",schema(Map.of("cve",string("CVE 编号")),"cve")),
                tool("list_findings","查询漏洞实例","查询资产上的真实漏洞实例。需要 VULNERABILITY_VIEW。",schema(Map.of("query",string("CVE、资产或标题"),"status",string("状态"),"severity",string("风险等级"),"page",integer("页码，从 1 开始"),"size",integer("每页数量，最大 100")))),
                tool("get_finding","查询漏洞实例详情","按业务 ID 查询扫描证据、资产上下文和处置状态。需要 VULNERABILITY_VIEW。",schema(Map.of("id",integer("漏洞实例业务 ID")),"id")),
                tool("list_patches","查询补丁库","返回当前补丁目录、CVE 映射和官方来源。需要 PATCH_VIEW。",schema(Map.of())),
                tool("get_patch","查询补丁详情","按业务 ID 查询补丁说明、适用性和验证依据。需要 PATCH_VIEW。",schema(Map.of("id",integer("补丁业务 ID")),"id")),
                tool("list_pending_approvals","查询待办审批","返回当前用户可查看的待审批记录，不提供审批写操作。需要 APPROVAL_VIEW。",schema(Map.of())),
                tool("get_report_data","查询统计数据","返回真实数据库计算的漏洞、资产、补丁、SLA 和趋势数据。需要 REPORT_VIEW。",schema(Map.of("days",integer("统计周期：7-365 天")))),
                tool("build_visual_report","生成可视化报表数据","生成可直接渲染的折线图、柱状图和环形图数据结构。需要 REPORT_VIEW。",schema(Map.of("days",integer("统计周期：7-365 天"))))
        );
    }

    public Object call(String name,Map<String,Object> args){
        Object result=switch(name){
            case "get_current_user" -> accessControl.userView(requireUser());
            case "search_system_data" -> search.search(requiredString(args,"query"));
            case "list_assets" -> {require("ASSET_VIEW");yield assets.page(string(args,"query"),null,null,page(args),size(args));}
            case "get_asset" -> {require("ASSET_VIEW");yield assets.get(requiredLong(args,"id"));}
            case "list_vulnerabilities" -> {require("VULNERABILITY_VIEW");yield findings.library(string(args,"query"),string(args,"severity"),boolValue(args,"kev"),null,page(args),size(args));}
            case "get_vulnerability" -> {require("VULNERABILITY_VIEW");yield findings.vulnerability(requiredString(args,"cve").toUpperCase(Locale.ROOT));}
            case "list_findings" -> {require("VULNERABILITY_VIEW");yield findings.page(string(args,"status"),string(args,"severity"),string(args,"query"),null,page(args),size(args));}
            case "get_finding" -> {require("VULNERABILITY_VIEW");yield findings.get(requiredLong(args,"id"));}
            case "list_patches" -> {require("PATCH_VIEW");yield patches.list();}
            case "get_patch" -> {require("PATCH_VIEW");yield patches.get(requiredLong(args,"id"));}
            case "list_pending_approvals" -> {require("APPROVAL_VIEW");yield approvals.list().stream().filter(a->"PENDING".equals(a.status())).toList();}
            case "get_report_data" -> {require("REPORT_VIEW");yield reports.report(days(args));}
            case "build_visual_report" -> {require("REPORT_VIEW");yield visualReport(days(args));}
            default -> throw new IllegalArgumentException("未知 MCP 工具："+name);
        };
        audit.log("AI_MCP",name,"READ","公司智能体只读查询："+name,"Company agent read-only query: "+name,currentUser.name());
        return result;
    }

    private Map<String,Object> visualReport(int days){
        var report=reports.report(days);
        List<Map<String,Object>> charts=new ArrayList<>();
        charts.add(Map.of("type","bar","title","风险等级分布","labels",report.severityDistribution().keySet(),"series",List.of(Map.of("name","漏洞实例","data",report.severityDistribution().values()))));
        charts.add(Map.of("type","donut","title","环境分布","labels",report.environmentDistribution().keySet(),"series",report.environmentDistribution().values()));
        charts.add(Map.of("type","line","title","整改趋势","labels",report.remediationTrend().stream().map(p->p.date()).toList(),"series",List.of(
                Map.of("name","新增","data",report.remediationTrend().stream().map(p->p.opened()).toList()),
                Map.of("name","已修复","data",report.remediationTrend().stream().map(p->p.resolved()).toList()),
                Map.of("name","待整改","data",report.remediationTrend().stream().map(p->p.backlog()).toList()))));
        return Map.of("generatedAt",report.generatedAt(),"windowDays",report.reportingWindowDays(),"summary",report,"charts",charts);
    }

    private com.gazellio.platform.model.UserAccount requireUser(){var user=currentUser.current();if(user==null)throw new AccessDeniedException("无法识别 MCP 调用用户");return user;}
    private void require(String permission){currentUser.requireAnyAuthority(permission);}
    private int page(Map<String,Object> args){return Math.max(0,intValue(args,"page",1)-1);}
    private int size(Map<String,Object> args){return Math.max(1,Math.min(100,intValue(args,"size",30)));}
    private int days(Map<String,Object> args){return Math.max(7,Math.min(365,intValue(args,"days",30)));}
    private int intValue(Map<String,Object> args,String key,int fallback){Object v=args.get(key);return v instanceof Number n?n.intValue():fallback;}
    private Long requiredLong(Map<String,Object> args,String key){Object v=args.get(key);if(v instanceof Number n)return n.longValue();try{return Long.valueOf(String.valueOf(v));}catch(Exception e){throw new IllegalArgumentException(key+" 必须是整数");}}
    private String requiredString(Map<String,Object> args,String key){String value=string(args,key);if(value==null||value.isBlank())throw new IllegalArgumentException(key+" 为必填项");return value.trim();}
    private String string(Map<String,Object> args,String key){Object v=args.get(key);return v==null?null:String.valueOf(v);}
    private Boolean boolValue(Map<String,Object> args,String key){Object v=args.get(key);return v instanceof Boolean b?b:null;}
    private Map<String,Object> tool(String name,String title,String description,Map<String,Object> input){return Map.of("name",name,"title",title,"description",description,"inputSchema",input,"annotations",Map.of("readOnlyHint",true,"destructiveHint",false,"idempotentHint",true));}
    private Map<String,Object> schema(Map<String,Object> properties,String... required){Map<String,Object> out=new LinkedHashMap<>();out.put("type","object");out.put("properties",properties);out.put("additionalProperties",false);if(required.length>0)out.put("required",List.of(required));return out;}
    private Map<String,Object> string(String description){return Map.of("type","string","description",description);}
    private Map<String,Object> integer(String description){return Map.of("type","integer","description",description);}
    private Map<String,Object> bool(String description){return Map.of("type","boolean","description",description);}
}
