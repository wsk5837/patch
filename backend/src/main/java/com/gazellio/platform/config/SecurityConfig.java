package com.gazellio.platform.config;

import com.gazellio.platform.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    @Bean
    SecurityFilterChain filterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwt,
            @Qualifier("corsConfigurationSource") CorsConfigurationSource cors
    ) throws Exception {
        return http
                .csrf(c -> c.disable())
                .cors(c -> c.configurationSource(cors))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health/**", "/api/auth/**", "/api/agent/**").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .requestMatchers(
                                "/", "/index.html", "/favicon.ico", "/gazellio-logo.png", "/assets/**", "/error",
                                "/login", "/vulnerabilities/**", "/scans/**", "/assets", "/patches/**",
                                "/tasks/**", "/approvals/**", "/automation/**", "/reports/**", "/audit/**",
                                "/settings/**"
                        ).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean("corsConfigurationSource")
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors-origins:}") String origins) {
        CorsConfiguration c = new CorsConfiguration();
        var allowedOrigins = Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
        if (!allowedOrigins.isEmpty()) {
            c.setAllowedOriginPatterns(allowedOrigins);
        }
        c.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Agent-Key", "X-Agent-Registration-Token"));
        c.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", c);
        return source;
    }
}
