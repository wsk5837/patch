package com.gazellio.platform.controller;

import com.gazellio.platform.dto.ApiDtos.*;
import com.gazellio.platform.service.PatchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/patches")
@RequiredArgsConstructor
public class PatchController {
    private final PatchService service;

    @GetMapping public List<PatchView> list(){return service.list();}
    @GetMapping("/{id}") public PatchView get(@PathVariable Long id){return service.get(id);}
    @GetMapping("/{id}/findings") public List<FindingView> findings(@PathVariable Long id){return service.findings(id);}
    @GetMapping("/servers") public List<PatchServerView> servers(){return service.servers();}
    @GetMapping("/deployments") public List<DeploymentView> deployments(){return service.deployments();}

    @PreAuthorize("hasAnyRole('ADMIN','SECURITY','OPS')")
    @PostMapping
    public PatchView register(@Valid @RequestBody PatchCreateRequest req){return service.register(req);}

    @PreAuthorize("hasAnyRole('ADMIN','SECURITY','OPS')")
    @PostMapping("/sync")
    public MessageResponse sync(){return new MessageResponse("servers="+service.sync());}
}
