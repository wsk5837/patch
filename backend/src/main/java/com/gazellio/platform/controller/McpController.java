package com.gazellio.platform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gazellio.platform.service.McpReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
public class McpController {
    private final McpReadService service;
    private final ObjectMapper mapper;

    @PostMapping({"","/"})
    public ResponseEntity<?> message(@RequestBody Map<String,Object> request,
                                     @RequestHeader(value="Accept",required=false)String accept){
        Object id=request.get("id");String method=Objects.toString(request.get("method"),"");
        if(id==null&&method.startsWith("notifications/"))return ResponseEntity.accepted().build();
        try{
            Object result=switch(method){
                case "initialize" -> Map.of("protocolVersion","2025-03-26","capabilities",Map.of("tools",Map.of("listChanged",false)),"serverInfo",Map.of("name","ANOWX Read-only Data & Reporting MCP","version","1.0.0"));
                case "ping" -> Map.of();
                case "tools/list" -> Map.of("tools",service.tools());
                case "tools/call" -> call(params(request));
                default -> throw new NoSuchElementException("不支持的 MCP 方法："+method);
            };
            Map<String,Object> response=Map.of("jsonrpc","2.0","id",id,"result",result);
            return encode(response,accept);
        }catch(AccessDeniedException e){return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error(id,-32003,"当前委托用户没有执行该查询的权限"));}
        catch(IllegalArgumentException e){return ResponseEntity.badRequest().body(error(id,-32602,e.getMessage()));}
        catch(NoSuchElementException e){return ResponseEntity.badRequest().body(error(id,-32601,e.getMessage()));}
        catch(Exception e){return ResponseEntity.internalServerError().body(error(id,-32603,"系统查询失败"));}
    }

    private Map<String,Object> call(Map<String,Object> params){
        String name=Objects.toString(params.get("name"),"");
        @SuppressWarnings("unchecked") Map<String,Object> args=params.get("arguments") instanceof Map<?,?> m?(Map<String,Object>)m:Map.of();
        Object data=service.call(name,args);
        String text;
        try{text=mapper.writeValueAsString(data);}catch(Exception e){text=String.valueOf(data);}
        return Map.of("content",List.of(Map.of("type","text","text",text)),"structuredContent",Map.of("data",data),"isError",false);
    }

    @SuppressWarnings("unchecked") private Map<String,Object> params(Map<String,Object> request){return request.get("params") instanceof Map<?,?> map?(Map<String,Object>)map:Map.of();}
    private Map<String,Object> error(Object id,int code,String message){Map<String,Object> out=new LinkedHashMap<>();out.put("jsonrpc","2.0");out.put("id",id);out.put("error",Map.of("code",code,"message",message));return out;}
    private ResponseEntity<?> encode(Map<String,Object> response,String accept){
        if(accept!=null&&accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE)){
            try{return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM).body("event: message\ndata: "+mapper.writeValueAsString(response)+"\n\n");}
            catch(Exception ignored){}
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(response);
    }
}
