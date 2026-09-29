package com.gazellio.platform.controller;

import com.gazellio.platform.dto.ApiDtos.*;
import com.gazellio.platform.model.UserAccount;
import com.gazellio.platform.repository.UserAccountRepository;
import com.gazellio.platform.security.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/auth") @RequiredArgsConstructor
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final UserAccountRepository users;
    private final JwtService jwt;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req){
        try { authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(req.username(),req.password())); }
        catch(AuthenticationException e){ throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid credentials"); }
        UserAccount u=users.findByUsername(req.username()).orElseThrow();
        return new LoginResponse(jwt.generate(u),new UserView(u.getId(),u.getUsername(),u.getDisplayName(),u.getEmail(),u.getRole().name()));
    }
}
