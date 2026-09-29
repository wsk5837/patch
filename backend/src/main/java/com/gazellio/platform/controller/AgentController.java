package com.gazellio.platform.controller;

import com.gazellio.platform.dto.ApiDtos.*;
import com.gazellio.platform.service.ScanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController @RequestMapping("/api/agent") @RequiredArgsConstructor
public class AgentController {
 private final ScanService service;
 @Value("${app.agent-registration-token}") private String registrationToken;

 @PostMapping("/register")
 public AgentView register(@RequestHeader("X-Agent-Registration-Token") String token,@Valid @RequestBody AgentRegisterRequest req){
   if(registrationToken==null||registrationToken.isBlank()||!registrationToken.equals(token)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid agent registration token");
   return service.register(req);
 }
 @PostMapping("/heartbeat") public AgentView heartbeat(@RequestHeader("X-Agent-Key") String key,@RequestBody AgentHeartbeatRequest req){return service.heartbeat(key,req);}
 @PostMapping("/results") public Map<String,Integer> results(@RequestHeader("X-Agent-Key") String key,@Valid @RequestBody AgentResultRequest req){return Map.of("accepted",service.ingestAgentResults(key,req));}
}
