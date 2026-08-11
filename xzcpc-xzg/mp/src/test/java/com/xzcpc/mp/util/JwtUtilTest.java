package com.xzcpc.mp.util;

import com.xzcpc.mp.config.JwtProperties;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0: JWT 角色字段测试
 */
@DisplayName("JWT 角色字段")
class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret("test-p0-jwt-secret-key-2026-for-unit-tests");
        props.setExpireHours(168);
        jwtUtil = new JwtUtil(props);
    }

    @Test
    @DisplayName("JWT token 中携带 role 字段")
    void shouldIncludeRoleInJwt() {
        String token = jwtUtil.generate(1L, "openid_001", "store_manager");

        Claims claims = jwtUtil.parse(token);
        assertEquals("1", claims.getSubject());
        assertEquals("openid_001", claims.get("openid"));
        assertEquals("store_manager", claims.get("role"));
    }

    @Test
    @DisplayName("兼容旧版：无 role 的 generate 方法仍可工作")
    void shouldWorkWithoutRole() {
        String token = jwtUtil.generate(2L, "openid_002");

        Claims claims = jwtUtil.parse(token);
        assertEquals("2", claims.getSubject());
        assertEquals("openid_002", claims.get("openid"));
        assertNull(claims.get("role"));
    }

    @Test
    @DisplayName("owner 角色可写入 JWT")
    void ownerRoleInJwt() {
        String token = jwtUtil.generate(3L, "openid_003", "owner");

        Claims claims = jwtUtil.parse(token);
        assertEquals("owner", claims.get("role"));
    }

    @Test
    @DisplayName("staff 角色可写入 JWT")
    void staffRoleInJwt() {
        String token = jwtUtil.generate(4L, "openid_004", "staff");

        Claims claims = jwtUtil.parse(token);
        assertEquals("staff", claims.get("role"));
    }

    @Test
    @DisplayName("token 过期后解析抛出异常")
    void expiredTokenShouldThrow() {
        JwtProperties shortProps = new JwtProperties();
        shortProps.setSecret("test-secret");
        shortProps.setExpireHours(-1); // 立即过期
        JwtUtil shortJwt = new JwtUtil(shortProps);

        // 负过期时间会导致 token 立即过期
        assertThrows(Exception.class, () -> {
            String token = shortJwt.generate(5L, "openid_005");
            shortJwt.parse(token);
        });
    }
}
