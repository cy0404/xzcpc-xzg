package com.xzcpc.mp.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Random;

/**
 * 企迈 OpenAPI v3 签名工具。
 * 算法：HMAC-SHA1(sorted_params) → Base64 → URLEncode
 */
public final class QmaiSignUtil {

    private QmaiSignUtil() {}

    public static QmaiAuth makeAuth(String openId, String grantCode, String openKey) {
        long ts = System.currentTimeMillis() / 1000;
        int nonce = 10000 + new Random().nextInt(90000);
        String sortedStr = "grantCode=" + grantCode + "&nonce=" + nonce
                + "&openId=" + openId + "&timestamp=" + ts;
        String encoded = URLEncoder.encode(sortedStr, StandardCharsets.UTF_8);
        // 还原 = 和 & 用于签名计算
        String restored = encoded.replace("%3D", "=").replace("%26", "&");
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(openKey.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            byte[] signBytes = mac.doFinal(restored.getBytes(StandardCharsets.UTF_8));
            String rawToken = Base64.getEncoder().encodeToString(signBytes);
            // 放入 JSON body 前必须 URLEncode（+ → %2B, = → %3D）
            String urlToken = URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
            return new QmaiAuth(ts, nonce, urlToken);
        } catch (Exception e) {
            throw new RuntimeException("Qmai signature failed", e);
        }
    }

    public record QmaiAuth(long timestamp, int nonce, String token) {}
}
