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
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final CurrentUserService currentUser;

    public List<String> permissionCatalog(){return PermissionCatalog.ALL;}
    public Set<String> permissions(UserAccount user){
        if(user==null)return Set.of();
        if(user.getAccessRoleId()==null)return new LinkedHashSet<>(PermissionCatalog.defaults(user.getRole().name()));
        return rolePermissions.findByRoleId(user.getAccessRoleId()).stream().map(RolePermission::getPermissionCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
    public UserView userView(UserAccount user){
        AccessRole role=user.getAccessRoleId()==null?null:roles.findById(user.getAccessRoleId()).orElse(null);
        String code=role==null?user.getRole().name():role.getCode();
        return new UserView(user.getId(),user.getUsername(),user.getDisplayName(),user.getEmail(),code,
                role==null?null:role.getId(),role==null?code:role.getNameZh(),role==null?code:role.getNameEn(),
                user.isEnabled(),new ArrayList<>(permissions(user)));
    }
    public List<UserView> users(){return users.findAllByOrderByUsernameAsc().stream().map(this::userView).toList();}
    public List<RoleView> roles(){
        List<AccessRole> list=roles.findAllByOrderByCodeAsc();
        Map<Long,List<String>> permissions=rolePermissions.findByRoleIdIn(list.stream().map(AccessRole::getId).toList()).stream()
                .collect(Collectors.groupingBy(RolePermission::getRoleId,Collectors.mapping(RolePermission::getPermissionCode,Collectors.toList())));
        return list.stream().map(r->new RoleView(r.getId(),r.getCode(),r.getNameZh(),r.getNameEn(),r.isSystemRole(),r.isEnabled(),
                users.countByAccessRoleId(r.getId()),permissions.getOrDefault(r.getId(),List.of()))).toList();
    }

    @Transactional public UserView createUser(UserSaveRequest req){
        if(users.findByUsername(req.username().trim()).isPresent())throw new ResponseStatusException(HttpStatus.CONFLICT,"Username already exists");
        AccessRole role=requireRole(req.roleId());
        UserAccount user=UserAccount.builder().username(req.username().trim()).displayName(req.displayName().trim()).email(req.email())
                .passwordHash(encoder.encode(req.password()==null||req.password().isBlank()?INITIAL_PASSWORD:req.password()))
                .role(legacyRole(role.getCode())).accessRoleId(role.getId()).enabled(req.enabled()==null||req.enabled()).build();
        users.save(user);audit.log("USER",user.getId(),"CREATE","创建用户 "+user.getUsername(),"Created user "+user.getUsername(),currentUser.name());return userView(user);
    }
    @Transactional public UserView updateUser(Long id,UserSaveRequest req){
        UserAccount user=requireUser(id);AccessRole role=requireRole(req.roleId());
        if("admin".equalsIgnoreCase(user.getUsername())&&(!"ADMIN".equals(role.getCode())||Boolean.FALSE.equals(req.enabled())))throw new ResponseStatusException(HttpStatus.CONFLICT,"Bootstrap administrator must remain enabled with the ADMIN role");
        users.findByUsername(req.username().trim()).filter(x->!x.getId().equals(id)).ifPresent(x->{throw new ResponseStatusException(HttpStatus.CONFLICT,"Username already exists");});
        user.setUsername(req.username().trim());user.setDisplayName(req.displayName().trim());user.setEmail(req.email());user.setAccessRoleId(role.getId());user.setRole(legacyRole(role.getCode()));
        if(req.enabled()!=null)user.setEnabled(req.enabled());if(req.password()!=null&&!req.password().isBlank())user.setPasswordHash(encoder.encode(req.password()));
        users.save(user);audit.log("USER",id,"UPDATE","更新用户与角色 "+user.getUsername(),"Updated user and role "+user.getUsername(),currentUser.name());return userView(user);
    }
    @Transactional public UserView resetPassword(Long id){UserAccount user=requireUser(id);user.setPasswordHash(encoder.encode(INITIAL_PASSWORD));users.save(user);audit.log("USER",id,"RESET_PASSWORD","重置用户初始密码","Reset user initial password",currentUser.name());return userView(user);}
    @Transactional public void deleteUser(Long id){UserAccount user=requireUser(id);if("admin".equalsIgnoreCase(user.getUsername()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Bootstrap administrator cannot be deleted");users.delete(user);audit.log("USER",id,"DELETE","删除用户 "+user.getUsername(),"Deleted user "+user.getUsername(),currentUser.name());}

    @Transactional public RoleView createRole(RoleSaveRequest req){
        String code=normalizeCode(req.code());if(roles.findByCode(code).isPresent())throw new ResponseStatusException(HttpStatus.CONFLICT,"Role code already exists");
        AccessRole role=roles.save(AccessRole.builder().code(code).nameZh(req.nameZh().trim()).nameEn(req.nameEn().trim()).systemRole(false).enabled(req.enabled()==null||req.enabled()).build());
        replacePermissions(role.getId(),req.permissions());audit.log("ROLE",role.getId(),"CREATE","创建角色 "+code,"Created role "+code,currentUser.name());return roles().stream().filter(r->r.id().equals(role.getId())).findFirst().orElseThrow();
    }
    @Transactional public RoleView updateRole(Long id,RoleSaveRequest req){
        AccessRole role=requireRole(id);String code=normalizeCode(req.code());
        if(role.isSystemRole()&&!role.getCode().equals(code))throw new ResponseStatusException(HttpStatus.CONFLICT,"System role code cannot be changed");
        if("ADMIN".equals(role.getCode())&&(Boolean.FALSE.equals(req.enabled())||req.permissions()==null||!req.permissions().contains("USER_MANAGE")))throw new ResponseStatusException(HttpStatus.CONFLICT,"ADMIN must remain enabled with USER_MANAGE permission");
        roles.findByCode(code).filter(x->!x.getId().equals(id)).ifPresent(x->{throw new ResponseStatusException(HttpStatus.CONFLICT,"Role code already exists");});
        role.setCode(code);role.setNameZh(req.nameZh().trim());role.setNameEn(req.nameEn().trim());if(req.enabled()!=null)role.setEnabled(req.enabled());role.setUpdatedAt(Instant.now());roles.save(role);
        replacePermissions(id,req.permissions());audit.log("ROLE",id,"UPDATE","更新角色权限 "+code,"Updated role permissions "+code,currentUser.name());return roles().stream().filter(r->r.id().equals(id)).findFirst().orElseThrow();
    }
    @Transactional public void deleteRole(Long id){AccessRole role=requireRole(id);if(role.isSystemRole())throw new ResponseStatusException(HttpStatus.CONFLICT,"System role cannot be deleted");if(users.countByAccessRoleId(id)>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Role is assigned to users");rolePermissions.deleteByRoleId(id);roles.delete(role);}
    @Transactional public void replacePermissions(Long roleId,List<String> values){
        LinkedHashSet<String> clean=(values==null?List.<String>of():values).stream().map(String::trim).filter(PermissionCatalog.ALL::contains).collect(Collectors.toCollection(LinkedHashSet::new));
        rolePermissions.deleteByRoleId(roleId);rolePermissions.flush();for(String value:clean)rolePermissions.save(RolePermission.builder().roleId(roleId).permissionCode(value).build());
    }
    private UserAccount requireUser(Long id){return users.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));}
    private AccessRole requireRole(Long id){AccessRole role=roles.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));if(!role.isEnabled())throw new ResponseStatusException(HttpStatus.CONFLICT,"Role is disabled");return role;}
    private String normalizeCode(String code){String value=code.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]+","_");if(value.isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid role code");return value;}
    private UserRole legacyRole(String code){try{return UserRole.valueOf(code);}catch(Exception ignored){return UserRole.APP_OWNER;}}
}
