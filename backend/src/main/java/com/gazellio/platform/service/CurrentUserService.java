package com.gazellio.platform.service;

import com.gazellio.platform.model.UserAccount;
import com.gazellio.platform.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
}
