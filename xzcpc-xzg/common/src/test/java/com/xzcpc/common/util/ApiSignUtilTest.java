package com.xzcpc.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ApiSignUtilTest {

    private static final String OPEN_KEY = "LyvrkvkxRkG2R6aM55bXpPwjYAbkEXTbVnKwfDYvVHjNwNFAmx";

    // ==================== kSort ====================

    @Test
    @DisplayName("kSort: 参数乱序 → 输出按 key 升序 + URL 编码")
    void kSortShouldSortKeysAndUrlEncode() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("zebra", 1);
        params.put("apple", 2);
        params.put("monkey", 3);

        String result = ApiSignUtil.kSort(params);

        // apple=2&monkey=3&zebra=1 经过 URL 编码
        assertTrue(result.startsWith("apple=2"));
        assertTrue(result.contains("monkey=3"));
        assertTrue(result.contains("zebra=1"));
        // 确认 = 和 & 已还原
        assertTrue(result.contains("="));
        assertTrue(result.contains("&"));
    }

    @Test
    @DisplayName("kSort: = 和 & 被正确还原（不是 %3D %26）")
    void kSortShouldRestoreEqualsAndAmpersand() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("key", "value");
        params.put("foo", "bar");

        String result = ApiSignUtil.kSort(params);

        assertFalse(result.contains("%3D"));
        assertFalse(result.contains("%26"));
        assertEquals("foo=bar&key=value", result);
    }

    // ==================== sign ====================

    @Test
    @DisplayName("sign: 完整签名流程，验证 token 不为空且 URL 编码")
    void signShouldReturnUrlEncodedToken() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("openId",    "d14c1559e87b747d577c834b275a4310");
        params.put("grantCode", "ba67d4fa46");
        params.put("timestamp", "1465185768");
        params.put("nonce",     "11886");

        String token = ApiSignUtil.sign(params, OPEN_KEY);

        assertNotNull(token);
        assertFalse(token.isBlank());
        // 最终 token 经过 URL 编码，不应含裸 =
        assertFalse(token.contains("="));
    }

    @Test
    @DisplayName("signRaw: 返回原始 Base64，含 = 填充")
    void signRawShouldReturnBase64WithPadding() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("openId",    "d14c1559e87b747d577c834b275a4310");
        params.put("grantCode", "ba67d4fa46");
        params.put("timestamp", "1465185768");
        params.put("nonce",     "11886");

        String raw = ApiSignUtil.signRaw(params, OPEN_KEY);

        assertNotNull(raw);
        assertFalse(raw.isBlank());
        // 原始 Base64 不含 %
        assertFalse(raw.contains("%"));
    }

    @Test
    @DisplayName("sign: 相同参数两次调用结果一致（幂等）")
    void signShouldBeIdempotent() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("openId",    "d14c1559e87b747d577c834b275a4310");
        params.put("grantCode", "ba67d4fa46");
        params.put("timestamp", "1465185768");
        params.put("nonce",     "11886");

        String t1 = ApiSignUtil.sign(params, OPEN_KEY);
        String t2 = ApiSignUtil.sign(params, OPEN_KEY);

        assertEquals(t1, t2);
    }

    // ==================== 边界 ====================

    @Test
    @DisplayName("kSort: 空 Map → 空字符串")
    void kSortWithEmptyMap() {
        String result = ApiSignUtil.kSort(Map.of());
        assertEquals("", result);
    }

    @Test
    @DisplayName("kSort: 特殊字符空格被正确 URL 编码")
    void kSortShouldEncodeSpaces() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "hello world");
        params.put("code", "abc123");

        String result = ApiSignUtil.kSort(params);

        // 空格会被编码
        assertTrue(result.contains("hello+world") || result.contains("hello%20world"));
        // code 正常出现
        assertTrue(result.contains("abc123"));
    }
}
