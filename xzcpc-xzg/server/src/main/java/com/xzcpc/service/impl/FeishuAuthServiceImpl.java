package com.xzcpc.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xzcpc.common.exception.BusinessException;
import com.xzcpc.config.FeishuProperties;
import com.xzcpc.constant.AdminRole;
import com.xzcpc.dto.FeishuLoginResp;
import com.xzcpc.entity.AdminPermission;
import com.xzcpc.service.AdminPermissionService;
import com.xzcpc.service.FeishuAuthService;
import com.xzcpc.util.AdminJwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 飞书登录服务实现。
 * 使用 JDK 17 原生 HttpClient，保证 form-encoded POST 可靠发送。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeishuAuthServiceImpl implements FeishuAuthService {

    private static final String APP_TOKEN_URL = "https://open.feishu.cn/open-apis/auth/v3/app_access_token/internal";
    private static final String USER_TOKEN_URL = "https://open.feishu.cn/open-apis/authen/v1/access_token";
    private static final String USER_INFO_URL = "https://open.feishu.cn/open-apis/authen/v1/user_info";

    private static final String CACHE_NAME = "feishuAppToken";
    private static final String CACHE_KEY = "app_access_token";

    private final FeishuProperties feishuProperties;
    private final AdminJwtUtil adminJwtUtil;
    private final AdminPermissionService adminPermissionService;
    private final ObjectMapper objectMapper;
    private final CacheManager cacheManager;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public FeishuLoginResp login(String code, String redirectUri) {
        String appAccessToken = getAppAccessToken();
        if (!StringUtils.hasText(redirectUri)) {
            throw new BusinessException("OAuth 回调地址为空");
        }

        // 用 code 换 user_access_token + open_id
        JsonNode tokenResp = postJson(USER_TOKEN_URL,
                appAccessToken,
                objectMapper.createObjectNode()
                        .put("grant_type", "authorization_code")
                        .put("code", code)
                        .put("redirect_uri", redirectUri)
                        .toString());

        // 兼容两种响应格式：data.access_token 或直接在根节点
        JsonNode data = tokenResp.path("data");
        if (data.isMissingNode()) data = tokenResp;
        String userAccessToken = data.path("access_token").asText();
        if (userAccessToken.isEmpty()) {
            log.error("飞书 token 交换失败，完整响应: {}", tokenResp);
            String msg = tokenResp.path("msg").asText();
            if (msg.isEmpty()) msg = tokenResp.toString();
            throw new BusinessException("飞书登录失败: " + msg);
        }

        // 换用户信息
        JsonNode userInfo = get(USER_INFO_URL, userAccessToken);
        String name = userInfo.path("data").path("name").asText("飞书用户");
        String avatarUrl = userInfo.path("data").path("avatar_url").asText("");
        String email = userInfo.path("data").path("email").asText("");
        String mobile = userInfo.path("data").path("mobile").asText("");
        String openId = data.path("open_id").asText("");
        if (!StringUtils.hasText(openId)) {
            openId = userInfo.path("data").path("open_id").asText("");
        }
        if (!StringUtils.hasText(openId)) {
            openId = userInfo.path("data").path("user_id").asText("");
        }
        if (!StringUtils.hasText(openId)) {
            log.error("飞书用户信息缺少 open_id/user_id，tokenResp={}, userInfo={}", tokenResp, userInfo);
            throw new BusinessException("飞书登录失败: 未获取到用户标识");
        }

        AdminPermission permission = adminPermissionService.ensureLoginUser(openId, name, avatarUrl, email, mobile);
        String role = permission.getRole();
        String token = adminJwtUtil.generate(openId, name, role);
        log.info("飞书登录成功: openId={}, name={}", openId, name);
        return new FeishuLoginResp(
                token,
                openId,
                name,
                avatarUrl,
                email,
                mobile,
                role,
                AdminRole.labelOf(role),
                AdminRole.hasAdminAccess(role)
        );
    }

    // ---------- private ----------

    private String getAppAccessToken() {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        String cached = cache != null ? cache.get(CACHE_KEY, String.class) : null;
        if (cached != null) return cached;

        String json;
        try {
            json = objectMapper.writeValueAsString(objectMapper.createObjectNode()
                    .put("app_id", feishuProperties.getAppId())
                    .put("app_secret", feishuProperties.getAppSecret()));
        } catch (Exception e) {
            throw new BusinessException("构造飞书请求失败: " + e.getMessage());
        }

        JsonNode resp = postJson(APP_TOKEN_URL, json);
        if (resp.path("code").asInt(-1) != 0) {
            throw new BusinessException("获取飞书 app_access_token 失败: " + resp.path("msg").asText());
        }

        String token = resp.path("app_access_token").asText();
        if (cache != null) cache.put(CACHE_KEY, token);
        return token;
    }

    // ---------- HTTP helpers ----------

    private JsonNode postJson(String url, String bearerToken, String jsonBody) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + bearerToken)
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(10))
                .build();

        return send(request);
    }

    private JsonNode postJson(String url, String jsonBody) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(10))
                .build();

        return send(request);
    }

    private JsonNode get(String url, String bearerToken) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + bearerToken)
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();

        return send(request);
    }

    private JsonNode send(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String body = response.body();
            log.debug("飞书 API {} {} → {}", request.method(), request.uri(), body);
            if (body == null || body.isBlank()) {
                throw new BusinessException("飞书接口返回空: " + response.statusCode());
            }
            return objectMapper.readTree(body);
        } catch (java.net.http.HttpTimeoutException e) {
            throw new BusinessException("飞书接口超时");
        } catch (java.io.IOException e) {
            throw new BusinessException("飞书接口网络异常: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("飞书接口被中断");
        }
    }
}
