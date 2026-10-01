package com.gazellio.platform.security;

import com.gazellio.platform.model.UserAccount;
import com.gazellio.platform.repository.UserAccountRepository;
import com.gazellio.platform.repository.AccessRoleRepository;
import com.gazellio.platform.repository.RolePermissionRepository;
import com.gazellio.platform.repository.UserRoleAssignmentRepository;
import com.gazellio.platform.model.RolePermission;
import com.gazellio.platform.service.PermissionCatalog;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {
    private final UserAccountRepository users;
    private final AccessRoleRepository roles;
    private final RolePermissionRepository permissions;
    private final UserRoleAssignmentRepository userRoles;
    public DatabaseUserDetailsService(UserAccountRepository users,AccessRoleRepository roles,RolePermissionRepository permissions,UserRoleAssignmentRepository userRoles) { this.users = users;this.roles=roles;this.permissions=permissions;this.userRoles=userRoles; }

    @Override public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserAccount u = users.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException(username));
        java.util.List<Long> roleIds=userRoles.findByUserId(u.getId()).stream().map(com.gazellio.platform.model.UserRoleAssignment::getRoleId).distinct().toList();
        if(roleIds.isEmpty()&&u.getAccessRoleId()!=null)roleIds=java.util.List.of(u.getAccessRoleId());
        var assigned=roleIds.isEmpty()?java.util.List.<com.gazellio.platform.model.AccessRole>of():roles.findAllById(roleIds);
        var enabledRoles=assigned.stream().filter(com.gazellio.platform.model.AccessRole::isEnabled).toList();
        java.util.List<SimpleGrantedAuthority> authorities=new java.util.ArrayList<>();
        if(enabledRoles.isEmpty()&&roleIds.isEmpty())authorities.add(new SimpleGrantedAuthority("ROLE_"+u.getRole().name()));
        else enabledRoles.forEach(role->authorities.add(new SimpleGrantedAuthority("ROLE_"+role.getCode())));
        java.util.Collection<String> values=roleIds.isEmpty()?PermissionCatalog.defaults(u.getRole().name()):permissions.findByRoleIdIn(enabledRoles.stream().map(com.gazellio.platform.model.AccessRole::getId).toList()).stream().map(RolePermission::getPermissionCode).distinct().toList();
        values.forEach(p->authorities.add(new SimpleGrantedAuthority(p)));
        return User.withUsername(u.getUsername()).password(u.getPasswordHash()).authorities(authorities)
                .disabled(!u.isEnabled()||(!roleIds.isEmpty()&&enabledRoles.isEmpty())).accountLocked(u.isLocked()).build();
    }
}
