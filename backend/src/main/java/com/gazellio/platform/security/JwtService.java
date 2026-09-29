package com.gazellio.platform.security;

import com.gazellio.platform.model.UserAccount;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
public class JwtService {
    private final SecretKey key;
    private final long hours;

    public JwtService(@Value("${app.jwt-secret}") String secret, @Value("${app.jwt-hours:12}") long hours) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(bytes, 0, padded, 0, bytes.length);
            for (int i = bytes.length; i < padded.length; i++) padded[i] = (byte) (31 + i);
            bytes = padded;
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.hours = hours;
    }

    public String generate(UserAccount user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("uid", user.getId())
                .claim("name", user.getDisplayName())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(hours, ChronoUnit.HOURS)))
                .signWith(key)
                .compact();
    }

    public String username(String token) {
        return claims(token).getSubject();
    }

    public TokenClaims read(String token) {
        Claims value = claims(token);
        Object rawUserId = value.get("uid");
        Number userId = rawUserId instanceof Number number ? number : null;
        return new TokenClaims(
                value.getSubject(),
                userId == null ? null : userId.longValue(),
                value.get("name", String.class),
                value.get("role", String.class)
        );
    }

    public boolean valid(String token) {
        try { claims(token); return true; } catch (Exception e) { return false; }
    }

    private Claims claims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public record TokenClaims(String username, Long userId, String displayName, String role) {}
}
