package com.gazellio.platform.controller;

import com.gazellio.platform.dto.ApiDtos.*;
import com.gazellio.platform.service.ScanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.List;

@RestController @RequestMapping("/api/scans") @RequiredArgsConstructor
public class ScanController {
 private final ScanService service;
 @GetMapping public List<ScanJobView> jobs(){return service.jobs();}
 @GetMapping("/{id}") public ScanJobView job(@PathVariable Long id){return service.job(id);}
 @GetMapping("/{id}/findings") public List<FindingView> findings(@PathVariable Long id){return service.jobFindings(id);}
 @PreAuthorize("hasAuthority('SCAN_EXECUTE')") @PostMapping public ScanJobView create(@Valid @RequestBody ScanCreateRequest req){return service.create(req);}
 @GetMapping("/agents") public List<AgentView> agents(){return service.agentList();}
}
