package com.gazellio.platform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gazellio.platform.service.McpReadService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class McpControllerTest {
    private final McpReadService service=mock(McpReadService.class);
    private final McpController controller=new McpController(service,new ObjectMapper());

    @Test void initializeReturnsStreamableHttpProtocolVersion(){
        ResponseEntity<?> response=controller.message(Map.of("jsonrpc","2.0","id",1,"method","initialize","params",Map.of()),"application/json");
        assertEquals(200,response.getStatusCode().value());
        @SuppressWarnings("unchecked") Map<String,Object> body=(Map<String,Object>)response.getBody();
        @SuppressWarnings("unchecked") Map<String,Object> result=(Map<String,Object>)body.get("result");
        assertEquals("2025-03-26",result.get("protocolVersion"));
    }

    @Test void toolsListReturnsOnlyServiceDefinedTools(){
        List<Map<String,Object>> tools=List.of(Map.of("name","list_assets","title","查询资产"));
        when(service.tools()).thenReturn(tools);
        ResponseEntity<?> response=controller.message(Map.of("jsonrpc","2.0","id",2,"method","tools/list","params",Map.of()),"application/json");
        assertEquals(200,response.getStatusCode().value());
        @SuppressWarnings("unchecked") Map<String,Object> body=(Map<String,Object>)response.getBody();
        @SuppressWarnings("unchecked") Map<String,Object> result=(Map<String,Object>)body.get("result");
        assertEquals(tools,result.get("tools"));
    }
}
