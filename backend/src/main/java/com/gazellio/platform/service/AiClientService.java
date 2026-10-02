package com.gazellio.platform.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AiClientService {
    private final CurrentUserService currentUser;
    private final AccessControlService accessControl;
    private final DashboardReportService reports;
    private final AuditService audit;
    @Value("${app.ai.mode:mock}") private String mode;
    @Value("${app.ai.base-url:}") private String baseUrl;
    @Value("${app.ai.agent-id:}") private String agentId;
    @Value("${app.ai.api-token:}") private String apiToken;
    @Value("${app.ai.chat-path:/api/agents/{agentId}/chat}") private String chatPath;
    @Value("${app.ai.timeout-seconds:120}") private int timeoutSeconds;
    @Value("${app.ai.public-base-url:http://localhost:8080}") private String publicBaseUrl;

    public Map<String,Object> status(){
        boolean live="live".equalsIgnoreCase(mode);
        return Map.of("mode",live?"live":"mock","configured",!live||configured(),"readOnly",true,
                "capabilities",List.of("系统数据查询","跨模块检索","可视化报表生成"));
    }

    public Map<String,Object> chat(String message,String conversationId,List<Map<String,Object>> history){
        if(message==null||message.isBlank())throw new IllegalArgumentException("请输入查询内容");
        var user=currentUser.current();if(user==null)throw new IllegalStateException("无法识别当前登录用户");
        String id=conversationId==null||conversationId.isBlank()?UUID.randomUUID().toString():conversationId;
        try{
            Map<String,Object> result="live".equalsIgnoreCase(mode)?live(message.trim(),id,history,user):mock(message.trim(),id);
            audit.log("AI_ASSISTANT",id,"QUERY","智能体查询成功","AI assistant query succeeded",currentUser.name());
            return result;
        }catch(Exception e){
            audit.log("AI_ASSISTANT",id,"ERROR","智能体查询失败："+safe(e.getMessage()),"AI assistant query failed",currentUser.name());
            if(e instanceof IllegalArgumentException argument)throw argument;
            throw new IllegalStateException("公司智能体暂时无法响应，请稍后重试或联系管理员检查智能体配置");
        }
    }

    private Map<String,Object> live(String message,String conversationId,List<Map<String,Object>> history,com.gazellio.platform.model.UserAccount user){
        if(!configured())throw new IllegalStateException("公司智能体尚未完成配置");
        SimpleClientHttpRequestFactory factory=new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);factory.setReadTimeout(Math.max(5,timeoutSeconds)*1000);
        RestClient client=RestClient.builder().requestFactory(factory).baseUrl(normalizeBase(baseUrl)).build();
        String path=chatPath.replace("{agentId}",agentId);
        Map<String,Object> request=new LinkedHashMap<>();
        request.put("agentId",agentId);request.put("conversationId",conversationId);request.put("message",message);
        request.put("history",history==null?List.of():history.stream().skip(Math.max(0,history.size()-12)).toList());
        request.put("user",Map.of("id",user.getId(),"username",user.getUsername(),"displayName",user.getDisplayName(),"permissions",accessControl.permissions(user)));
        request.put("systemContext",Map.of("system","ANOWX","readOnly",true,"mcpUrl",normalizeBase(publicBaseUrl)+"/mcp/","locale","zh-CN"));
        Map<String,Object> raw=postWithOneRetry(client,path,request);
        if(raw==null)throw new IllegalStateException("公司智能体返回空响应");
        Object answer=first(raw,"answer","content","message","output","text");
        Object visualization=first(raw,"visualization","visualizations","charts","report");
        Map<String,Object> normalized=new LinkedHashMap<>();normalized.put("conversationId",Objects.toString(raw.getOrDefault("conversationId",conversationId)));
        normalized.put("answer",answer==null?"公司智能体已完成查询。":answer);normalized.put("visualization",visualization);normalized.put("source","company-agent");normalized.put("createdAt",Instant.now().toString());return normalized;
    }

    @SuppressWarnings("unchecked")
    private Map<String,Object> postWithOneRetry(RestClient client,String path,Map<String,Object> request){
        RestClientException first;
        try{return client.post().uri(path).header("Authorization","Bearer "+apiToken).body(request).retrieve().body(Map.class);}
        catch(RestClientException error){first=error;}
        try{return client.post().uri(path).header("Authorization","Bearer "+apiToken).body(request).retrieve().body(Map.class);}
        catch(RestClientException retry){retry.addSuppressed(first);throw retry;}
    }

    private Map<String,Object> mock(String message,String conversationId){
        currentUser.requireAnyAuthority("REPORT_VIEW");
        var report=reports.report(30);
        Map<String,Object> chart=Map.of("type","bar","title","风险等级分布","labels",report.severityDistribution().keySet(),"series",List.of(Map.of("name","漏洞实例","data",report.severityDistribution().values())));
        return Map.of("conversationId",conversationId,"answer","当前为本地开发模式，已使用真实数据库生成 30 天安全概览；生产环境必须配置 AI_MODE=live。","visualization",List.of(chart),"source","local-read-only","createdAt",Instant.now().toString());
    }

    private boolean configured(){return !blank(baseUrl)&&!blank(agentId)&&!blank(apiToken);}
    private boolean blank(String value){return value==null||value.isBlank();}
    private String normalizeBase(String value){String normalized=value==null?"":value.trim().replaceAll("/+$","");if(normalized.isBlank())return normalized;return normalized.matches("^https?://.*")?normalized:"https://"+normalized;}
    private Object first(Map<String,Object> map,String... keys){for(String key:keys)if(map.get(key)!=null)return map.get(key);return null;}
    private String safe(String value){if(value==null)return "未知错误";return value.length()>240?value.substring(0,240):value;}
}
