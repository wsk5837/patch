package com.gazellio.platform.controller;

import com.gazellio.platform.dto.ApiDtos.*;
import com.gazellio.platform.model.UserAccount;
import com.gazellio.platform.repository.UserAccountRepository;
import com.gazellio.platform.security.JwtService;
import com.gazellio.platform.service.AuditService;
import com.gazellio.platform.service.CurrentUserService;
import com.gazellio.platform.service.AccessControlService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final UserAccountRepository users;
    private final JwtService jwt;
    private final AccessControlService accessControl;
    private final AuditService audit;
    private final CurrentUserService currentUser;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req){
        UserAccount candidate=users.findByUsername(req.username()).orElse(null);
        if(candidate!=null&&candidate.isLocked()){
            audit.log("AUTH",candidate.getId(),"LOGIN_BLOCKED","帐户已锁定","Sign-in blocked because the account is locked",candidate.getDisplayName());
            throw new ResponseStatusException(HttpStatus.LOCKED,"Account is locked; contact an administrator");
        }
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.username(), req.password())
            );
        } catch (AuthenticationException e) {
            if(candidate!=null){
                int attempts=(candidate.getFailedLoginAttempts()==null?0:candidate.getFailedLoginAttempts())+1;
                candidate.setFailedLoginAttempts(attempts);candidate.setLocked(attempts>=5);candidate.setUpdatedAt(Instant.now());users.save(candidate);
            }
            audit.log("AUTH",req.username(),"LOGIN_FAILED","登录失败","Sign-in failed",req.username());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        UserAccount u = users.findByUsername(req.username()).orElseThrow();
        u.setFailedLoginAttempts(0);u.setLastLoginAt(Instant.now());u.setUpdatedAt(Instant.now());users.save(u);
        audit.log("AUTH",u.getId(),"LOGIN","用户登录成功","User signed in",u.getDisplayName());
        return new LoginResponse(
                jwt.generate(u),
                accessControl.userView(u)
        );
    }

    @PostMapping("/logout")
    public MessageResponse logout(){
        audit.log("AUTH","SESSION","LOGOUT","用户退出登录","User signed out",currentUser.name());
        return new MessageResponse("Signed out");
    }

    @GetMapping("/me")
    public UserView me(){
        UserAccount user=currentUser.current();
        if(user==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return accessControl.userView(user);
    }
}
