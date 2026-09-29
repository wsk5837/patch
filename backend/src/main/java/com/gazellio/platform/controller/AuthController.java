package com.gazellio.platform.controller;

import com.gazellio.platform.dto.ApiDtos.*;
import com.gazellio.platform.model.UserAccount;
import com.gazellio.platform.repository.UserAccountRepository;
import com.gazellio.platform.security.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final UserAccountRepository users;
    private final JwtService jwt;
    private final PasswordEncoder passwordEncoder;

    @Value("${ADMIN_INITIAL_PASSWORD:}")
    private String adminInitialPassword;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req){
        // Keep the bootstrap admin credential aligned with Render's ADMIN_INITIAL_PASSWORD.
        // This avoids stale hashes from an earlier deployment while still requiring the server-side secret.
        if ("admin".equals(req.username()) && adminInitialPassword != null && !adminInitialPassword.isBlank()
                && adminInitialPassword.equals(req.password())) {
            users.findByUsername("admin").ifPresent(admin -> {
                if (!passwordEncoder.matches(adminInitialPassword, admin.getPasswordHash())) {
                    admin.setPasswordHash(passwordEncoder.encode(adminInitialPassword));
                    admin.setEnabled(true);
                    users.save(admin);
                }
            });
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.username(), req.password())
            );
        } catch (AuthenticationException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        UserAccount u = users.findByUsername(req.username()).orElseThrow();
        return new LoginResponse(
                jwt.generate(u),
                new UserView(u.getId(), u.getUsername(), u.getDisplayName(), u.getEmail(), u.getRole().name())
        );
    }
}
