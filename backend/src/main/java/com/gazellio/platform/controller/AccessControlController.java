package com.gazellio.platform.controller;
import com.gazellio.platform.dto.ApiDtos.*;
import com.gazellio.platform.service.AccessControlService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/access-control") @RequiredArgsConstructor
public class AccessControlController {
 private final AccessControlService service;
 @PreAuthorize("hasAnyAuthority('USER_MANAGE','ROLE_MANAGE','USER_CREDENTIAL_RESET','USER_ACCOUNT_STATUS')")
 @GetMapping("/users") public List<UserView> users(){return service.users();}
 @PreAuthorize("hasAuthority('USER_MANAGE')")
 @PostMapping("/users") public UserView createUser(@Valid @RequestBody UserSaveRequest req){return service.createUser(req);}
 @PreAuthorize("hasAuthority('USER_MANAGE')")
 @PutMapping("/users/{id}") public UserView updateUser(@PathVariable Long id,@Valid @RequestBody UserSaveRequest req){return service.updateUser(id,req);}
 @PreAuthorize("hasAuthority('USER_CREDENTIAL_RESET')")
 @PostMapping("/users/{id}/reset-password") public UserView reset(@PathVariable Long id){return service.resetPassword(id);}
 @PreAuthorize("hasAuthority('USER_ACCOUNT_STATUS')")
 @PostMapping("/users/{id}/unlock") public UserView unlock(@PathVariable Long id){return service.unlockUser(id);}
 @PreAuthorize("hasAuthority('USER_MANAGE')")
 @DeleteMapping("/users/{id}") public void deleteUser(@PathVariable Long id){service.deleteUser(id);}
 @PreAuthorize("hasAnyAuthority('USER_MANAGE','ROLE_MANAGE','USER_CREDENTIAL_RESET','USER_ACCOUNT_STATUS')")
 @GetMapping("/roles") public List<RoleView> roles(){return service.roles();}
 @PreAuthorize("hasAnyAuthority('USER_MANAGE','ROLE_MANAGE','USER_CREDENTIAL_RESET','USER_ACCOUNT_STATUS')")
 @GetMapping("/permissions") public List<String> permissions(){return service.permissionCatalog();}
 @PreAuthorize("hasAuthority('ROLE_MANAGE')")
 @PostMapping("/roles") public RoleView createRole(@Valid @RequestBody RoleSaveRequest req){return service.createRole(req);}
 @PreAuthorize("hasAuthority('ROLE_MANAGE')")
 @PutMapping("/roles/{id}") public RoleView updateRole(@PathVariable Long id,@Valid @RequestBody RoleSaveRequest req){return service.updateRole(id,req);}
 @PreAuthorize("hasAuthority('ROLE_MANAGE')")
 @DeleteMapping("/roles/{id}") public void deleteRole(@PathVariable Long id){service.deleteRole(id);}
}
