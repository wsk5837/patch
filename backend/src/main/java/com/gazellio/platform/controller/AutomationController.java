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
    @PreAuthorize("hasAuthority('AUTOMATION_TEMPLATE_EDIT')") @PostMapping("/templates") public TemplateView createTemplate(@jakarta.validation.Valid @RequestBody TemplateSaveRequest req){return service.createTemplate(req);}
    @PreAuthorize("hasAuthority('AUTOMATION_TEMPLATE_EDIT')") @PutMapping("/templates/{id}") public TemplateView updateTemplate(@PathVariable Long id,@jakarta.validation.Valid @RequestBody TemplateSaveRequest req){return service.updateTemplate(id,req);}
    @PreAuthorize("hasAuthority('AUTOMATION_TEMPLATE_EDIT')") @DeleteMapping("/templates/{id}") public void deleteTemplate(@PathVariable Long id){service.deleteTemplate(id);}
    @GetMapping("/bindings") public List<AutomationBindingView> bindings(){return service.bindings();}
    @PreAuthorize("hasAuthority('AUTOMATION_TEMPLATE_EDIT')") @PutMapping("/bindings") public List<AutomationBindingView> updateBindings(@RequestBody AutomationBindingsUpdateRequest req){return service.updateBindings(req);}
    @GetMapping("/runs") public List<RunView> runs(){return service.runs();}
    @GetMapping("/runs/{id}") public RunView run(@PathVariable Long id){return service.run(id);}
    @PreAuthorize("hasAnyAuthority('AUTOMATION_RUN_CONTROL','AUTOMATION_EXECUTE')") @PostMapping("/runs/{id}/pause") public RunView pause(@PathVariable Long id){return service.pause(id);}
    @PreAuthorize("hasAnyAuthority('AUTOMATION_RUN_CONTROL','AUTOMATION_EXECUTE')") @PostMapping("/runs/{id}/resume") public RunView resume(@PathVariable Long id){return service.resume(id);}
    @PreAuthorize("hasAnyAuthority('AUTOMATION_RUN_CONTROL','AUTOMATION_EXECUTE')") @PostMapping("/runs/{id}/rollback") public RunView rollback(@PathVariable Long id){return service.rollback(id);}
    @GetMapping("/deployments") public List<DeploymentView> deployments(){return service.deployments();}

    @PreAuthorize("hasAnyAuthority('PATCH_DEPLOY','AUTOMATION_EXECUTE')")
    @PostMapping("/batch/preview") public BatchScopePreview preview(@RequestBody BatchScopeRequest request){return batchPatch.preview(request);}
    @PreAuthorize("hasAnyAuthority('PATCH_DEPLOY','AUTOMATION_EXECUTE')")
    @PostMapping("/batch/runs") public BatchRunResult execute(@RequestBody BatchScopeRequest request){return batchPatch.execute(request);}
}
