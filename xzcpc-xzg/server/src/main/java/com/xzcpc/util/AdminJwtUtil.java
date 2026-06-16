package com.xzcpc.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * 总部端管理员 JWT 工具（与 mp 模块的 JwtUtil 独立，使用不同密钥）。
 */
@Component
public class AdminJwtUtil {

    private final SecretKey key;
    private final long expireMs;

    public AdminJwtUtil(@Value("${admin.jwt.secret:xzcpc-admin-jwt-secret-key-2026}") String secret,
                        @Value("${admin.jwt.expire-hours:72}") long expireHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMs = expireHours * 3600 * 1000;
    }

    /**
     * 生成 JWT token。
     */
    public String generate(String openId, String name) {
        return generate(openId, name, null);
    }

    /**
     * 生成带角色信息的 JWT token。
     */
    public String generate(String openId, String name, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(openId)
                .claim("name", name)
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMs))
                .signWith(key)
                .compact();
    }

    /**
     * 校验并解析 token。
     */
    public Claims validate(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException e) {
            throw new JwtException("Token 无效或已过期", e);
        }
    }
}
