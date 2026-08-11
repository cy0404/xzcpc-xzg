package com.xzcpc.mp.util;

import com.xzcpc.mp.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
@RequiredArgsConstructor
public class JwtUtil {

    private final JwtProperties jwtProperties;

    private SecretKey getKey() {
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generate(Long sessionId, String openid) {
        return generate(sessionId, openid, null);
    }

    /** P0: JWT 中携带角色信息，避免每次请求都查数据库 */
    public String generate(Long sessionId, String openid, String role) {
        long expireMs = jwtProperties.getExpireHours() * 3600L * 1000L;
        Date now = new Date();
        Date expire = new Date(now.getTime() + expireMs);

        var builder = Jwts.builder()
                .subject(String.valueOf(sessionId))
                .claim("openid", openid)
                .issuedAt(now)
                .expiration(expire);
        if (role != null) {
            builder.claim("role", role);
        }
        return builder.signWith(getKey()).compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
