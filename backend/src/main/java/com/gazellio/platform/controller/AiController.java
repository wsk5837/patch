package com.gazellio.platform.controller;

import com.gazellio.platform.service.AiClientService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {
    private final AiClientService service;
    public record ChatRequest(@NotBlank String message,String conversationId,List<Map<String,Object>> history){}
    @GetMapping("/status") public Map<String,Object> status(){return service.status();}
    @PostMapping("/chat") @PreAuthorize("hasAnyAuthority('REPORT_VIEW','ASSET_VIEW','VULNERABILITY_VIEW','PATCH_VIEW')")
    public Map<String,Object> chat(@Valid @RequestBody ChatRequest request){return service.chat(request.message(),request.conversationId(),request.history());}
}
