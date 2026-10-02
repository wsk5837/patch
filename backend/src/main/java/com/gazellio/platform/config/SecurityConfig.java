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
import com.gazellio.platform.security.McpAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

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
            McpAuthenticationFilter mcp,
            @Qualifier("corsConfigurationSource") CorsConfigurationSource cors
    ) throws Exception {
        return http
                .csrf(c -> c.disable())
                .cors(c -> c.configurationSource(cors))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/api/auth/**", "/api/agent/**").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/mcp", "/mcp/**").authenticated()
                        .requestMatchers("/api/access-control/**").hasAnyAuthority("USER_MANAGE","ROLE_MANAGE","USER_CREDENTIAL_RESET","USER_ACCOUNT_STATUS")
                        .requestMatchers(HttpMethod.GET, "/api/dashboard/report", "/api/dashboard/report/**").hasAuthority("REPORT_VIEW")
                        .requestMatchers(HttpMethod.GET, "/api/dashboard/**").hasAuthority("DASHBOARD_VIEW")
                        .requestMatchers(HttpMethod.GET, "/api/vulnerabilities/**").hasAuthority("VULNERABILITY_VIEW")
                        .requestMatchers(HttpMethod.GET, "/api/scans/**").hasAuthority("SCAN_VIEW")
                        .requestMatchers(HttpMethod.GET, "/api/assets/**").hasAuthority("ASSET_VIEW")
                        .requestMatchers(HttpMethod.GET, "/api/work-orders/incidents/**").hasAuthority("INCIDENT_VIEW")
                        .requestMatchers(HttpMethod.GET, "/api/work-orders/changes/**").hasAuthority("CHANGE_VIEW")
                        .requestMatchers(HttpMethod.GET, "/api/tasks/**").hasAuthority("TASK_VIEW")
                        .requestMatchers(HttpMethod.GET, "/api/patches/**").hasAuthority("PATCH_VIEW")
                        .requestMatchers(HttpMethod.GET, "/api/approvals/**").hasAuthority("APPROVAL_VIEW")
                        .requestMatchers(HttpMethod.GET, "/api/automation/**").hasAuthority("AUTOMATION_VIEW")
                        .requestMatchers(HttpMethod.GET, "/api/audit/**").hasAuthority("AUDIT_VIEW")
                        .requestMatchers(HttpMethod.GET, "/api/settings/**").hasAuthority("SETTINGS_VIEW")
                        .requestMatchers(HttpMethod.GET, "/api/compliance/**").hasAuthority("COMPLIANCE_VIEW")
                        .requestMatchers("/api/**").authenticated()
                        .requestMatchers(
                                "/", "/index.html", "/favicon.ico", "/gazellio-logo.png", "/assets/**", "/error",
                                "/login", "/vulnerabilities/**", "/scans/**", "/assets", "/patches/**",
                                "/tasks/**", "/approvals/**", "/automation/**", "/reports/**", "/audit/**",
                                "/settings/**", "/compliance/**"
                        ).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(mcp, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean("corsConfigurationSource")
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors-origins:}") String origins) {
        CorsConfiguration c = new CorsConfiguration();
        List<String> allowedOrigins = Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(SecurityConfig::normalizeOrigin)
                .toList();
        if (!allowedOrigins.isEmpty()) {
            c.setAllowedOriginPatterns(allowedOrigins);
        }
        c.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Agent-Key", "X-Agent-Registration-Token", "X-ANOWX-User"));
        c.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", c);
        return source;
    }

    private static String normalizeOrigin(String origin) {
        String normalized = origin.replaceAll("/+$", "");
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            return normalized;
        }
        return "https://" + normalized;
    }
}
