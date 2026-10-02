package com.gazellio.platform.controller;

import com.gazellio.platform.service.AiClientService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {
    private final AiClientService service;
    public record ChatRequest(@NotBlank String message,String conversationId,List<Map<String,Object>> history){}
    @GetMapping("/status") public Map<String,Object> status(@RequestHeader(value="Accept-Language",required=false)String language){return service.status(language);}
    @PostMapping("/chat") @PreAuthorize("hasAnyAuthority('REPORT_VIEW','ASSET_VIEW','VULNERABILITY_VIEW','PATCH_VIEW')")
    public Map<String,Object> chat(@Valid @RequestBody ChatRequest request,@RequestHeader(value="Accept-Language",required=false)String language){
        try{return service.chat(request.message(),request.conversationId(),request.history(),language);}
        catch(IllegalStateException error){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,error.getMessage(),error);}
    }
}
