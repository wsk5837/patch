package com.gazellio.platform.controller;

import com.gazellio.platform.dto.ApiDtos.*;
import com.gazellio.platform.service.WorkOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/work-orders")
@RequiredArgsConstructor
public class WorkOrderController {
    private final WorkOrderService service;

    @GetMapping("/incidents") public List<SecurityIncidentView> incidents(){ return service.incidents(); }
    @GetMapping("/incidents/{id}") public SecurityIncidentView incident(@PathVariable Long id){ return service.incident(id); }
    @GetMapping("/changes") public List<ChangeWorkOrderView> changes(){ return service.changes(); }
    @GetMapping("/changes/{id}") public ChangeWorkOrderView change(@PathVariable Long id){ return service.change(id); }
    @GetMapping("/finding-options") public List<FindingView> findingOptions(@RequestParam(defaultValue="INCIDENT") String stage){ return service.findingOptions(stage); }

    @PreAuthorize("hasAnyAuthority('INCIDENT_CREATE','INCIDENT_MANAGE')")
    @PostMapping("/incidents")
    public SecurityIncidentView createIncident(@Valid @RequestBody AggregateIncidentCreateRequest req){ return service.createIncident(req); }

    @PreAuthorize("hasAnyAuthority('CHANGE_CREATE','CHANGE_MANAGE')")
    @PostMapping("/changes")
    public ChangeWorkOrderView createChange(@Valid @RequestBody AggregateChangeCreateRequest req){ return service.createChange(req); }

    @PreAuthorize("hasAnyAuthority('INCIDENT_CREATE','INCIDENT_MANAGE')")
    @PostMapping("/findings/{findingId}/incident")
    public SecurityIncidentView createIncident(@PathVariable Long findingId){
        return service.incident(service.ensureForFinding(findingId).getId());
    }

    @PreAuthorize("hasAnyAuthority('INCIDENT_ASSIGN','INCIDENT_MANAGE')")
    @PostMapping("/incidents/{id}/assign")
    public SecurityIncidentView assign(@PathVariable Long id, @RequestBody IncidentActionRequest req){ return service.assign(id, req); }

    @PreAuthorize("hasAnyAuthority('INCIDENT_REMEDIATE','INCIDENT_MANAGE')")
    @PostMapping("/incidents/{id}/start-remediation")
    public SecurityIncidentView startRemediation(@PathVariable Long id, @RequestBody(required=false) IncidentActionRequest req){ return service.startRemediation(id, req); }

    @PreAuthorize("hasAnyAuthority('CHANGE_CREATE','CHANGE_MANAGE')")
    @PostMapping("/incidents/{id}/changes")
    public ChangeWorkOrderView createChange(@PathVariable Long id, @Valid @RequestBody ChangeCreateRequest req){ return service.createChange(id, req); }

    @PreAuthorize("hasAnyAuthority('CHANGE_RESUBMIT','CHANGE_MANAGE')")
    @PostMapping("/changes/{id}/resubmit")
    public ChangeWorkOrderView resubmitChange(@PathVariable Long id, @Valid @RequestBody ChangeCreateRequest req){ return service.resubmitChange(id, req); }
}
