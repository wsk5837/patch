package com.gazellio.platform.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    public JwtAuthenticationFilter(JwtService jwt) { this.jwt = jwt; }

    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ") && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = auth.substring(7);
            try {
                JwtService.TokenClaims claims = jwt.read(token);
                UserDetails u = User.withUsername(claims.username()).password("").roles(claims.role()).build();
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception ignored) {}
        }
        chain.doFilter(req, res);
    }
}
