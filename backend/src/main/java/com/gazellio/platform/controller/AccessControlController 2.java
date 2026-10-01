package com.gazellio.platform.controller;
import com.gazellio.platform.dto.ApiDtos.*;
import com.gazellio.platform.service.AccessControlService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/access-control") @RequiredArgsConstructor
@PreAuthorize("hasAuthority('USER_MANAGE')")
public class AccessControlController {
 private final AccessControlService service;
 @GetMapping("/users") public List<UserView> users(){return service.users();}
 @PostMapping("/users") public UserView createUser(@Valid @RequestBody UserSaveRequest req){return service.createUser(req);}
 @PutMapping("/users/{id}") public UserView updateUser(@PathVariable Long id,@Valid @RequestBody UserSaveRequest req){return service.updateUser(id,req);}
 @PostMapping("/users/{id}/reset-password") public UserView reset(@PathVariable Long id){return service.resetPassword(id);}
 @DeleteMapping("/users/{id}") public void deleteUser(@PathVariable Long id){service.deleteUser(id);}
 @GetMapping("/roles") public List<RoleView> roles(){return service.roles();}
 @GetMapping("/permissions") public List<String> permissions(){return service.permissionCatalog();}
 @PostMapping("/roles") public RoleView createRole(@Valid @RequestBody RoleSaveRequest req){return service.createRole(req);}
 @PutMapping("/roles/{id}") public RoleView updateRole(@PathVariable Long id,@Valid @RequestBody RoleSaveRequest req){return service.updateRole(id,req);}
 @DeleteMapping("/roles/{id}") public void deleteRole(@PathVariable Long id){service.deleteRole(id);}
}
