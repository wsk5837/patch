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
    @GetMapping("/calendar") public List<PatchCalendarEventView> calendar(@RequestParam(required=false) String month){return service.calendar(month);}
    @GetMapping("/calendar/schedules/{id}") public PatchScheduleView schedule(@PathVariable Long id){return service.schedule(id);}

    @PreAuthorize("hasAuthority('PATCH_SCHEDULE')")
    @PostMapping("/calendar/schedules") public PatchScheduleView createSchedule(@Valid @RequestBody PatchScheduleRequest req){return service.createSchedule(req);}
    @PreAuthorize("hasAuthority('PATCH_SCHEDULE')")
    @PutMapping("/calendar/schedules/{id}") public PatchScheduleView updateSchedule(@PathVariable Long id,@Valid @RequestBody PatchScheduleRequest req){return service.updateSchedule(id,req);}
    @PreAuthorize("hasAuthority('PATCH_SCHEDULE')")
    @DeleteMapping("/calendar/schedules/{id}") public void deleteSchedule(@PathVariable Long id){service.deleteSchedule(id);}

    @PreAuthorize("hasAuthority('PATCH_MANAGE')")
    @PostMapping
    public PatchView register(@Valid @RequestBody PatchCreateRequest req){return service.register(req);}

    @PreAuthorize("hasAuthority('PATCH_MANAGE')")
    @PostMapping("/sync")
    public MessageResponse sync(){return new MessageResponse("servers="+service.sync());}
}
