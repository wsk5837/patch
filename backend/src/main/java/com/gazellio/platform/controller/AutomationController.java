package com.gazellio.platform.controller;

import com.gazellio.platform.dto.ApiDtos.*;
import com.gazellio.platform.service.BatchPatchService;
import com.gazellio.platform.service.OrchestrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/automation")
@RequiredArgsConstructor
public class AutomationController {
    private final OrchestrationService service;
    private final BatchPatchService batchPatch;

    @GetMapping("/templates") public List<TemplateView> templates(){return service.templates();}
    @GetMapping("/templates/{id}") public TemplateView template(@PathVariable Long id){return service.template(id);}
    @GetMapping("/runs") public List<RunView> runs(){return service.runs();}
    @GetMapping("/runs/{id}") public RunView run(@PathVariable Long id){return service.run(id);}
    @PreAuthorize("hasAnyRole('ADMIN','OPS')") @PostMapping("/runs/{id}/pause") public RunView pause(@PathVariable Long id){return service.pause(id);}
    @PreAuthorize("hasAnyRole('ADMIN','OPS')") @PostMapping("/runs/{id}/resume") public RunView resume(@PathVariable Long id){return service.resume(id);}
    @PreAuthorize("hasAnyRole('ADMIN','OPS')") @PostMapping("/runs/{id}/rollback") public RunView rollback(@PathVariable Long id){return service.rollback(id);}
    @GetMapping("/deployments") public List<DeploymentView> deployments(){return service.deployments();}

    @PostMapping("/batch/preview") public BatchScopePreview preview(@RequestBody BatchScopeRequest request){return batchPatch.preview(request);}
    @PreAuthorize("hasAnyRole('ADMIN','OPS')")
    @PostMapping("/batch/runs") public BatchRunResult execute(@RequestBody BatchScopeRequest request){return batchPatch.execute(request);}
}
