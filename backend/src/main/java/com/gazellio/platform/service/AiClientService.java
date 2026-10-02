package com.gazellio.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
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
    private final ObjectMapper objectMapper;
    @Value("${app.ai.mode:mock}") private String mode;
    @Value("${app.ai.base-url:}") private String baseUrl;
    @Value("${app.ai.agent-id:}") private String agentId;
    @Value("${app.ai.api-token:}") private String apiToken;
    @Value("${app.ai.chat-path:/adk/run_stream}") private String chatPath;
    @Value("${app.ai.timeout-seconds:120}") private int timeoutSeconds;
    @Value("${app.ai.public-base-url:http://localhost:8080}") private String publicBaseUrl;

    public Map<String,Object> status(String language){
        boolean live="live".equalsIgnoreCase(mode);
        return Map.of("mode",live?"live":"mock","configured",!live||configured(),"readOnly",true,
                "capabilities",english(language)?List.of("System data queries","Cross-module search","Visual report generation"):List.of("系统数据查询","跨模块检索","可视化报表生成"));
    }

    public Map<String,Object> chat(String message,String conversationId,List<Map<String,Object>> history,String language){
        boolean en=english(language);
        if(message==null||message.isBlank())throw new IllegalArgumentException(en?"Enter a question to query the system.":"请输入查询内容");
        var user=currentUser.current();if(user==null)throw new IllegalStateException(en?"The current signed-in user could not be identified.":"无法识别当前登录用户");
        String id=conversationId==null||conversationId.isBlank()?UUID.randomUUID().toString():conversationId;
        try{
            Map<String,Object> result="live".equalsIgnoreCase(mode)?live(message.trim(),id,history,user,en):mock(message.trim(),id,en);
            audit.log("AI_ASSISTANT",id,"QUERY","智能体查询成功","AI assistant query succeeded",currentUser.name());
            return result;
        }catch(Exception e){
            audit.log("AI_ASSISTANT",id,"ERROR","智能体查询失败："+safe(e.getMessage()),"AI assistant query failed",currentUser.name());
            if(e instanceof IllegalArgumentException argument)throw argument;
            throw new IllegalStateException(en?"The company agent is unavailable. Retry later or ask an administrator to check its configuration.":"公司智能体暂时无法响应，请稍后重试或联系管理员检查智能体配置");
        }
    }

    private Map<String,Object> live(String message,String conversationId,List<Map<String,Object>> history,com.gazellio.platform.model.UserAccount user,boolean en){
        if(!configured())throw new IllegalStateException(en?"The company agent has not been configured.":"公司智能体尚未完成配置");
        SimpleClientHttpRequestFactory factory=new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);factory.setReadTimeout(Math.max(5,timeoutSeconds)*1000);
        RestClient client=RestClient.builder().requestFactory(factory).baseUrl(normalizeBase(baseUrl)).build();
        String path=chatPath.replace("{agentId}",agentId);
        Map<String,Object> request=new LinkedHashMap<>();
        request.put("agent_id",agentId);
        request.put("user_id",blank(user.getUsername())?Objects.toString(user.getId()):user.getUsername());
        request.put("session_id",conversationId);
        request.put("message",message);
        String raw=postWithOneRetry(client,path,request);
        if(raw==null||raw.isBlank())throw new IllegalStateException(en?"The company agent returned an empty response.":"公司智能体返回空响应");
        return normalizeStreamResponse(raw,conversationId,en);
    }

    private String postWithOneRetry(RestClient client,String path,Map<String,Object> request){
        RestClientException first;
        try{return post(client,path,request);}
        catch(RestClientException error){first=error;}
        try{return post(client,path,request);}
        catch(RestClientException retry){retry.addSuppressed(first);throw retry;}
    }

    private String post(RestClient client,String path,Map<String,Object> request){
        return client.post().uri(path)
                .header("X-Agent-API-Key",apiToken)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_NDJSON,MediaType.APPLICATION_JSON)
                .body(request).retrieve().body(String.class);
    }

    private Map<String,Object> normalizeStreamResponse(String raw,String conversationId,boolean en){
        String answer="";Object visualization=List.of();
        for(String line:raw.split("\\R")){
            String value=line.trim();if(value.isBlank())continue;
            if(value.startsWith("data:"))value=value.substring(5).trim();
            if(value.equals("[DONE]"))continue;
            try{
                JsonNode event=objectMapper.readTree(value);
                String chunk=eventText(event);
                if(!chunk.isBlank())answer=mergeChunk(answer,chunk);
                JsonNode chart=findField(event,"visualization","visualizations","charts","report");
                if(chart!=null&&!chart.isNull())visualization=objectMapper.convertValue(chart,Object.class);
            }catch(Exception ignored){
                if(!value.startsWith("{")&&!value.startsWith("["))answer=mergeChunk(answer,value);
            }
        }
        answer=answer.trim();
        if(answer.startsWith("```json"))answer=answer.substring(7).replaceFirst("```\\s*$","").trim();
        if(answer.startsWith("{")&&answer.endsWith("}")){
            try{
                JsonNode payload=objectMapper.readTree(answer);
                JsonNode payloadAnswer=findField(payload,"answer","content","message","output","text");
                JsonNode payloadCharts=findField(payload,"visualization","visualizations","charts","report");
                if(payloadAnswer!=null&&payloadAnswer.isTextual())answer=payloadAnswer.asText();
                if(payloadCharts!=null&&!payloadCharts.isNull())visualization=objectMapper.convertValue(payloadCharts,Object.class);
            }catch(Exception ignored){}
        }
        Map<String,Object> normalized=new LinkedHashMap<>();
        normalized.put("conversationId",conversationId);
        normalized.put("answer",answer.isBlank()?(en?"The company agent completed the query.":"公司智能体已完成查询。"):answer);
        normalized.put("visualization",visualization);
        normalized.put("source","company-agent");normalized.put("createdAt",Instant.now().toString());return normalized;
    }

    private String eventText(JsonNode node){
        if(node==null||node.isNull())return "";
        if(node.isTextual())return node.asText();
        if(node.isArray()){
            StringBuilder out=new StringBuilder();for(JsonNode item:node){String text=eventText(item);if(!text.isBlank())out.append(text);}return out.toString();
        }
        for(String key:List.of("answer","output_text","text")){
            JsonNode value=node.get(key);if(value!=null&&value.isTextual())return value.asText();
        }
        JsonNode message=node.get("message");if(message!=null&&message.isTextual())return message.asText();
        for(String key:List.of("content","parts","result","response","data","event")){
            JsonNode value=node.get(key);if(value!=null){String text=eventText(value);if(!text.isBlank())return text;}
        }
        return "";
    }

    private JsonNode findField(JsonNode node,String... keys){
        if(node==null||!node.isObject())return null;
        for(String key:keys)if(node.has(key))return node.get(key);
        for(String wrapper:List.of("result","response","data","event")){
            JsonNode nested=node.get(wrapper);JsonNode found=findField(nested,keys);if(found!=null)return found;
        }
        return null;
    }

    private String mergeChunk(String current,String chunk){
        if(chunk==null||chunk.isBlank())return current;
        if(current.isBlank())return chunk;
        if(chunk.startsWith(current))return chunk;
        if(current.endsWith(chunk))return current;
        return current+chunk;
    }

    private Map<String,Object> mock(String message,String conversationId,boolean en){
        currentUser.requireAnyAuthority("REPORT_VIEW");
        var report=reports.report(30);
        Map<String,Object> chart=Map.of("type","bar","title",en?"Risk severity distribution":"风险等级分布","labels",report.severityDistribution().keySet(),"series",List.of(Map.of("name",en?"Vulnerability findings":"漏洞实例","data",report.severityDistribution().values())));
        return Map.of("conversationId",conversationId,"answer",en?"Local development mode is active. This 30-day overview was generated from the real application database; production must use AI_MODE=live.":"当前为本地开发模式，已使用真实数据库生成 30 天安全概览；生产环境必须配置 AI_MODE=live。","visualization",List.of(chart),"source","local-read-only","createdAt",Instant.now().toString());
    }

    private boolean configured(){return !blank(baseUrl)&&!blank(agentId)&&!blank(apiToken);}
    private boolean blank(String value){return value==null||value.isBlank();}
    private boolean english(String language){return language!=null&&language.toLowerCase(Locale.ROOT).startsWith("en");}
    private String normalizeBase(String value){String normalized=value==null?"":value.trim().replaceAll("/+$","");if(normalized.isBlank())return normalized;return normalized.matches("^https?://.*")?normalized:"https://"+normalized;}
    private String safe(String value){if(value==null)return "未知错误";return value.length()>240?value.substring(0,240):value;}
}
