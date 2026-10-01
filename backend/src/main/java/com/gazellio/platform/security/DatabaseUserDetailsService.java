package com.gazellio.platform.security;

import com.gazellio.platform.model.UserAccount;
import com.gazellio.platform.repository.UserAccountRepository;
import com.gazellio.platform.repository.AccessRoleRepository;
import com.gazellio.platform.repository.RolePermissionRepository;
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
    public DatabaseUserDetailsService(UserAccountRepository users,AccessRoleRepository roles,RolePermissionRepository permissions) { this.users = users;this.roles=roles;this.permissions=permissions; }

    @Override public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserAccount u = users.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException(username));
        var accessRole=u.getAccessRoleId()==null?null:roles.findById(u.getAccessRoleId()).orElse(null);
        String roleCode=accessRole==null?u.getRole().name():accessRole.getCode();
        java.util.List<SimpleGrantedAuthority> authorities=new java.util.ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_"+roleCode));
        java.util.Collection<String> values=u.getAccessRoleId()==null?PermissionCatalog.defaults(u.getRole().name()):permissions.findByRoleId(u.getAccessRoleId()).stream().map(RolePermission::getPermissionCode).toList();
        values.forEach(p->authorities.add(new SimpleGrantedAuthority(p)));
        return User.withUsername(u.getUsername()).password(u.getPasswordHash()).authorities(authorities)
                .disabled(!u.isEnabled()||(accessRole!=null&&!accessRole.isEnabled())).accountLocked(u.isLocked()).build();
    }
}
