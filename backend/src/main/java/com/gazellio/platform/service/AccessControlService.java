package com.gazellio.platform.service;

import com.gazellio.platform.dto.ApiDtos.*;
import com.gazellio.platform.model.*;
import com.gazellio.platform.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import static com.gazellio.platform.model.Enums.UserRole;

@Service @RequiredArgsConstructor
public class AccessControlService {
    public static final String INITIAL_PASSWORD="Gazellio@123";
    private final UserAccountRepository users;
    private final AccessRoleRepository roles;
    private final RolePermissionRepository rolePermissions;
    private final UserRoleAssignmentRepository userRoles;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final CurrentUserService currentUser;

    public List<String> permissionCatalog(){return PermissionCatalog.ALL;}
    public Set<String> permissions(UserAccount user){
        if(user==null)return Set.of();
        List<AccessRole> assigned=assignedRoles(user);
        if(assigned.isEmpty())return new LinkedHashSet<>(PermissionCatalog.defaults(user.getRole().name()));
        Set<Long> enabledRoleIds=assigned.stream().filter(AccessRole::isEnabled).map(AccessRole::getId).collect(Collectors.toSet());
        if(enabledRoleIds.isEmpty())return Set.of();
        return rolePermissions.findByRoleIdIn(enabledRoleIds).stream().map(RolePermission::getPermissionCode)
                .filter(PermissionCatalog::isKnown).collect(Collectors.toCollection(LinkedHashSet::new));
    }
    public UserView userView(UserAccount user){
        List<AccessRole> assigned=assignedRoles(user);
        AccessRole role=assigned.isEmpty()?null:assigned.getFirst();
        String code=role==null?user.getRole().name():role.getCode();
        return new UserView(user.getId(),user.getUsername(),user.getDisplayName(),user.getEmail(),user.getDepartment(),
                user.getEmployeeNo(),user.getPhone(),user.getAccountType(),code,
                role==null?null:role.getId(),role==null?code:role.getNameZh(),role==null?code:role.getNameEn(),
                user.isEnabled(),user.isLocked(),user.getFailedLoginAttempts(),s(user.getLastLoginAt()),
                s(user.getPasswordChangedAt()),s(user.getCreatedAt()),new ArrayList<>(permissions(user)),
                assigned.stream().map(AccessRole::getId).toList(),assigned.stream().map(r->new UserRoleSummary(
                        r.getId(),r.getCode(),r.getNameZh(),r.getNameEn(),r.getDataScope())).toList());
    }
    public List<UserView> users(){return users.findAllByOrderByUsernameAsc().stream().map(this::userView).toList();}
    public List<RoleView> roles(){
        List<AccessRole> list=roles.findAllByOrderByCodeAsc();
        Map<Long,List<String>> permissions=rolePermissions.findByRoleIdIn(list.stream().map(AccessRole::getId).toList()).stream()
                .collect(Collectors.groupingBy(RolePermission::getRoleId,Collectors.mapping(RolePermission::getPermissionCode,Collectors.toList())));
        return list.stream().map(r->new RoleView(r.getId(),r.getCode(),r.getNameZh(),r.getNameEn(),r.getDescriptionZh(),
                r.getDescriptionEn(),r.getDataScope(),r.isSystemRole(),r.isEnabled(),userRoles.countByRoleId(r.getId()),
                s(r.getUpdatedAt()),permissions.getOrDefault(r.getId(),List.of()).stream().filter(PermissionCatalog.ALL::contains).toList())).toList();
    }

    @Transactional public UserView createUser(UserSaveRequest req){
        if(users.findByUsername(req.username().trim()).isPresent())throw new ResponseStatusException(HttpStatus.CONFLICT,"Username already exists");
        List<AccessRole> assigned=requireRoles(req);
        AccessRole role=assigned.getFirst();
        UserAccount user=UserAccount.builder().username(req.username().trim()).displayName(req.displayName().trim()).email(trim(req.email()))
                .department(trim(req.department())).employeeNo(trim(req.employeeNo())).phone(trim(req.phone()))
                .accountType(accountType(req.accountType()))
                .passwordHash(encoder.encode(req.password()==null||req.password().isBlank()?INITIAL_PASSWORD:req.password()))
                .passwordChangedAt(Instant.now()).role(legacyRole(role.getCode())).accessRoleId(role.getId())
                .enabled(req.enabled()==null||req.enabled()).updatedAt(Instant.now()).build();
        users.save(user);replaceUserRoles(user.getId(),assigned);audit.log("USER",user.getId(),"CREATE","创建用户并分配 "+assigned.size()+" 个角色 "+user.getUsername(),"Created user and assigned "+assigned.size()+" roles "+user.getUsername(),currentUser.name());return userView(user);
    }
    @Transactional public UserView updateUser(Long id,UserSaveRequest req){
        UserAccount user=requireUser(id);List<AccessRole> assigned=requireRoles(req);AccessRole role=assigned.getFirst();
        if("admin".equalsIgnoreCase(user.getUsername())&&(assigned.stream().noneMatch(r->"ADMIN".equals(r.getCode()))||Boolean.FALSE.equals(req.enabled())))throw new ResponseStatusException(HttpStatus.CONFLICT,"Bootstrap administrator must remain enabled with the ADMIN role");
        users.findByUsername(req.username().trim()).filter(x->!x.getId().equals(id)).ifPresent(x->{throw new ResponseStatusException(HttpStatus.CONFLICT,"Username already exists");});
        user.setUsername(req.username().trim());user.setDisplayName(req.displayName().trim());user.setEmail(trim(req.email()));
        user.setDepartment(trim(req.department()));user.setEmployeeNo(trim(req.employeeNo()));user.setPhone(trim(req.phone()));
        user.setAccountType(accountType(req.accountType()));user.setAccessRoleId(role.getId());user.setRole(legacyRole(role.getCode()));
        if(req.enabled()!=null)user.setEnabled(req.enabled());if(req.password()!=null&&!req.password().isBlank()){user.setPasswordHash(encoder.encode(req.password()));user.setPasswordChangedAt(Instant.now());}
        user.setUpdatedAt(Instant.now());
        users.save(user);replaceUserRoles(user.getId(),assigned);audit.log("USER",id,"UPDATE","更新用户与角色 "+user.getUsername(),"Updated user and roles "+user.getUsername(),currentUser.name());return userView(user);
    }
    @Transactional public UserView resetPassword(Long id){UserAccount user=requireUser(id);user.setPasswordHash(encoder.encode(INITIAL_PASSWORD));user.setPasswordChangedAt(Instant.now());user.setFailedLoginAttempts(0);user.setLocked(false);user.setUpdatedAt(Instant.now());users.save(user);audit.log("USER",id,"RESET_PASSWORD","重置用户初始密码","Reset user initial password",currentUser.name());return userView(user);}
    @Transactional public UserView unlockUser(Long id){UserAccount user=requireUser(id);user.setLocked(false);user.setFailedLoginAttempts(0);user.setUpdatedAt(Instant.now());users.save(user);audit.log("USER",id,"UNLOCK","解锁用户 "+user.getUsername(),"Unlocked user "+user.getUsername(),currentUser.name());return userView(user);}
    @Transactional public void deleteUser(Long id){UserAccount user=requireUser(id);if("admin".equalsIgnoreCase(user.getUsername()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Bootstrap administrator cannot be deleted");userRoles.deleteByUserId(id);users.delete(user);audit.log("USER",id,"DELETE","删除用户 "+user.getUsername(),"Deleted user "+user.getUsername(),currentUser.name());}

    @Transactional public RoleView createRole(RoleSaveRequest req){
        String code=normalizeCode(req.code());if(roles.findByCode(code).isPresent())throw new ResponseStatusException(HttpStatus.CONFLICT,"Role code already exists");
        AccessRole role=roles.save(AccessRole.builder().code(code).nameZh(req.nameZh().trim()).nameEn(req.nameEn().trim())
                .descriptionZh(trim(req.descriptionZh())).descriptionEn(trim(req.descriptionEn())).dataScope(dataScope(req.dataScope()))
                .systemRole(false).enabled(req.enabled()==null||req.enabled()).build());
        replacePermissions(role.getId(),req.permissions());audit.log("ROLE",role.getId(),"CREATE","创建角色 "+code,"Created role "+code,currentUser.name());return roles().stream().filter(r->r.id().equals(role.getId())).findFirst().orElseThrow();
    }
    @Transactional public RoleView updateRole(Long id,RoleSaveRequest req){
        AccessRole role=requireRole(id);String code=normalizeCode(req.code());
        if(role.isSystemRole()&&!role.getCode().equals(code))throw new ResponseStatusException(HttpStatus.CONFLICT,"System role code cannot be changed");
        if("ADMIN".equals(role.getCode())&&(Boolean.FALSE.equals(req.enabled())||req.permissions()==null||!new HashSet<>(req.permissions()).containsAll(PermissionCatalog.ALL)))throw new ResponseStatusException(HttpStatus.CONFLICT,"ADMIN must remain enabled with every system permission");
        roles.findByCode(code).filter(x->!x.getId().equals(id)).ifPresent(x->{throw new ResponseStatusException(HttpStatus.CONFLICT,"Role code already exists");});
        role.setCode(code);role.setNameZh(req.nameZh().trim());role.setNameEn(req.nameEn().trim());
        role.setDescriptionZh(trim(req.descriptionZh()));role.setDescriptionEn(trim(req.descriptionEn()));role.setDataScope(dataScope(req.dataScope()));
        if(req.enabled()!=null)role.setEnabled(req.enabled());role.setUpdatedAt(Instant.now());roles.save(role);
        replacePermissions(id,req.permissions());audit.log("ROLE",id,"UPDATE","更新角色权限 "+code,"Updated role permissions "+code,currentUser.name());return roles().stream().filter(r->r.id().equals(id)).findFirst().orElseThrow();
    }
    @Transactional public void deleteRole(Long id){AccessRole role=requireRole(id);if(role.isSystemRole())throw new ResponseStatusException(HttpStatus.CONFLICT,"System role cannot be deleted");if(userRoles.countByRoleId(id)>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Role is assigned to users");rolePermissions.deleteByRoleId(id);roles.delete(role);}
    @Transactional public void replacePermissions(Long roleId,List<String> values){
        LinkedHashSet<String> clean=(values==null?List.<String>of():values).stream().map(String::trim).filter(PermissionCatalog::isKnown).collect(Collectors.toCollection(LinkedHashSet::new));
        rolePermissions.deleteByRoleId(roleId);rolePermissions.flush();for(String value:clean)rolePermissions.save(RolePermission.builder().roleId(roleId).permissionCode(value).build());
    }
    private UserAccount requireUser(Long id){return users.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));}
    public List<AccessRole> assignedRoles(UserAccount user){
        if(user==null||user.getId()==null)return List.of();
        List<Long> ids=userRoles.findByUserId(user.getId()).stream().map(UserRoleAssignment::getRoleId).distinct().toList();
        if(ids.isEmpty()&&user.getAccessRoleId()!=null)ids=List.of(user.getAccessRoleId());
        if(ids.isEmpty())return List.of();
        Map<Long,AccessRole> byId=roles.findAllById(ids).stream().collect(Collectors.toMap(AccessRole::getId,r->r));
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }
    private List<AccessRole> requireRoles(UserSaveRequest req){
        LinkedHashSet<Long> ids=new LinkedHashSet<>();
        if(req.roleIds()!=null)req.roleIds().stream().filter(Objects::nonNull).forEach(ids::add);
        if(ids.isEmpty()&&req.roleId()!=null)ids.add(req.roleId());
        if(ids.isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"At least one role is required");
        List<AccessRole> assigned=ids.stream().map(this::requireRole).toList();
        if(assigned.size()!=ids.size())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"One or more roles are invalid");
        return assigned;
    }
    private void replaceUserRoles(Long userId,List<AccessRole> assigned){
        userRoles.deleteByUserId(userId);userRoles.flush();
        for(AccessRole role:assigned)userRoles.save(UserRoleAssignment.builder().userId(userId).roleId(role.getId()).build());
    }
    private AccessRole requireRole(Long id){AccessRole role=roles.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));if(!role.isEnabled())throw new ResponseStatusException(HttpStatus.CONFLICT,"Role is disabled");return role;}
    private String normalizeCode(String code){String value=code.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]+","_");if(value.isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid role code");return value;}
    private String accountType(String value){String normalized=value==null||value.isBlank()?"LOCAL":value.trim().toUpperCase(Locale.ROOT);if(!Set.of("LOCAL","DIRECTORY","SERVICE").contains(normalized))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid account type");return normalized;}
    private String dataScope(String value){String normalized=value==null||value.isBlank()?"ALL":value.trim().toUpperCase(Locale.ROOT);if(!Set.of("ALL","BUSINESS_SERVICE","OWNED_ASSETS").contains(normalized))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid data scope");return normalized;}
    private String trim(String value){return value==null||value.isBlank()?null:value.trim();}
    private String s(Instant value){return value==null?null:value.toString();}
    private UserRole legacyRole(String code){try{return UserRole.valueOf(code);}catch(Exception ignored){return UserRole.APP_OWNER;}}
}
