package com.gazellio.platform.security;

import com.gazellio.platform.model.UserAccount;
import com.gazellio.platform.repository.UserAccountRepository;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {
    private final UserAccountRepository users;
    public DatabaseUserDetailsService(UserAccountRepository users) { this.users = users; }

    @Override public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserAccount u = users.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException(username));
        return User.withUsername(u.getUsername()).password(u.getPasswordHash()).roles(u.getRole().name()).disabled(!u.isEnabled()).build();
    }
}
