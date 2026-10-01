package com.gazellio.platform.controller;

import com.gazellio.platform.dto.ApiDtos.*;
import com.gazellio.platform.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/tasks") @RequiredArgsConstructor
public class TaskController {
 private final TaskService service;
 @GetMapping public List<TaskView> list(){return service.list();}
 @GetMapping("/{id}") public TaskView get(@PathVariable Long id){return service.get(id);}
 @GetMapping("/{id}/deployment-candidates") public List<AssetView> deploymentCandidates(@PathVariable Long id,@RequestParam String environment){return service.deploymentCandidates(id,environment);}
 @PreAuthorize("hasAnyAuthority('TASK_EXECUTE','TASK_RETEST','TASK_MANAGE')")
 @PostMapping("/{id}/actions/{action}") public TaskView action(@PathVariable Long id,@PathVariable String action,@RequestBody(required=false) TaskActionRequest req){return service.action(id,action,req);}
 @PreAuthorize("hasAnyAuthority('TASK_ASSIGN','TASK_MANAGE')")
 @PostMapping("/{id}/assign") public TaskView assign(@PathVariable Long id,@RequestBody TaskAssignRequest req){return service.assign(id,req);}
}
