package com.xzcpc.common.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

/**
 * API 签名工具。
 * <p>
 * 签名流程：参数按 key 升序排序 → 拼接为 key=value&... →
 * URL 编码 → HMAC-SHA1 → Base64 → URL 编码得到最终 token。
 * </p>
 *
 * <pre>
 * Map&lt;String, Object&gt; params = new LinkedHashMap&lt;&gt;();
 * params.put("openId",     "...");
 * params.put("grantCode",  "...");
 * params.put("timestamp",  "...");
 * params.put("nonce",      "...");
 * String token = ApiSignUtil.sign(params, openKey);
 * </pre>
 */
public final class ApiSignUtil {

    private static final Base64.Encoder BASE64 = Base64.getEncoder();

    private ApiSignUtil() {}

    /**
     * 对参数签名，返回 URL 编码后的 token。
     *
     * @param params  请求参数（key 升序排列）
     * @param openKey 签名密钥
     * @return URL 编码后的签名 token
     */
    public static String sign(Map<String, Object> params, String openKey) {
        String sorted = kSort(params);
        String hmac  = hmacSha1(sorted, openKey);
        return urlEncode(hmac);
    }

    /**
     * 只做 HMAC-SHA1 + Base64，不 URL 编码（用于直接比较或其他场景）。
     */
    public static String signRaw(Map<String, Object> params, String openKey) {
        return hmacSha1(kSort(params), openKey);
    }

    // ---- 内部实现 ----

    /**
     * key 升序排序 → key=value 拼接 → URL 编码 → 还原 = 和 &。
     */
    static String kSort(Map<String, Object> map) {
        // TreeMap 天然按 key 排序
        TreeMap<String, Object> sorted = new TreeMap<>(map);

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> e : sorted.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(e.getKey()).append('=').append(e.getValue());
        }

        // URL 编码后把 = 和 & 还原（保持参数结构可见）
        String encoded = urlEncode(sb.toString());
        return encoded.replace("%3D", "=").replace("%26", "&");
    }

    /**
     * HMAC-SHA1 → Base64 编码。
     */
    static String hmacSha1(String data, String key) {
        try {
            SecretKeySpec signingKey = new SecretKeySpec(
                    key.getBytes(StandardCharsets.UTF_8), "HmacSHA1");
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(signingKey);
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return BASE64.encodeToString(raw);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("HMAC-SHA1 signature failed", e);
        }
    }

    /**
     * UTF-8 URL 编码。
     */
    static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            throw new RuntimeException("URL encoding failed", e);
        }
    }

    // ---- 验证主方法 ----

    public static void main(String[] args) {
        String openKey = "LyvrkvkxRkG2R6aM55bXpPwjYAbkEXTbVnKwfDYvVHjNwNFAmx";

        Map<String, Object> params = new java.util.LinkedHashMap<>();
        params.put("openId",    "71fcea7abc9709d653693116410b5385");
        params.put("grantCode", "WaGI2rqy9b");
        params.put("timestamp", "1782716429");
        params.put("nonce",     "11887");

        String token = ApiSignUtil.sign(params, openKey);
        System.out.println("token: " + token);
        // 期望输出: M9fit%2FDMw4%2FqFD2BZn6y%2BpwBD%2FVhx%2FIc5hBNcdo%3D
    }
}
