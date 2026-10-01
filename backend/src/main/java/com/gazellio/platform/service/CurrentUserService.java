package com.gazellio.platform.service;

import com.gazellio.platform.model.UserAccount;
import com.gazellio.platform.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class CurrentUserService {
    private final UserAccountRepository users;
    public UserAccount current(){
        Authentication a=SecurityContextHolder.getContext().getAuthentication();
        if(a==null || a.getName()==null) return users.findByUsername("admin").orElse(null);
        return users.findByUsername(a.getName()).orElse(null);
    }
    public String name(){UserAccount u=current(); return u==null?"system":u.getDisplayName();}
    public boolean hasAnyAuthority(String... permissions){
        Authentication authentication=SecurityContextHolder.getContext().getAuthentication();
        // Service methods are also invoked by trusted startup/background workflows without a
        // request security context. HTTP calls are authenticated by SecurityConfig first.
        if(authentication==null)return true;
        if(authentication.getAuthorities()==null)return false;
        java.util.Set<String> granted=authentication.getAuthorities().stream()
                .map(authority->authority.getAuthority()).collect(java.util.stream.Collectors.toSet());
        return java.util.Arrays.stream(permissions).anyMatch(granted::contains);
    }
    public void requireAnyAuthority(String... permissions){
        if(!hasAnyAuthority(permissions))throw new AccessDeniedException("Required permission is missing");
    }
}
