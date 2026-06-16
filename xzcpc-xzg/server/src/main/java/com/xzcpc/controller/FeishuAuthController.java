package com.xzcpc.controller;

import com.xzcpc.common.annotation.OpLog;
import com.xzcpc.common.response.R;
import com.xzcpc.dto.FeishuLoginReq;
import com.xzcpc.dto.FeishuLoginResp;
import com.xzcpc.service.FeishuAuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * 飞书登录接口。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class FeishuAuthController {

    private final FeishuAuthService feishuAuthService;

    @OpLog(module = "认证", operation = "飞书登录")
    @PostMapping("/feishu/login")
    public R<FeishuLoginResp> login(@RequestBody FeishuLoginReq req, HttpServletRequest request) {
        return R.ok(feishuAuthService.login(req.getCode(), resolveRedirectUri(req, request)));
    }

    private String resolveRedirectUri(FeishuLoginReq req, HttpServletRequest request) {
        if (StringUtils.hasText(req.getRedirectUri())) {
            return req.getRedirectUri();
        }

        String referer = request.getHeader("Referer");
        if (StringUtils.hasText(referer)) {
            int hashIndex = referer.indexOf('#');
            if (hashIndex >= 0) {
                String base = referer.substring(0, hashIndex).split("\\?", 2)[0];
                String hash = referer.substring(hashIndex)
                        .replaceAll("[?&]code=[^&]*", "")
                        .replaceAll("\\?$", "");
                return base + (StringUtils.hasText(hash) ? hash : "#/tasks");
            }
        }

        String origin = request.getHeader("Origin");
        if (!StringUtils.hasText(origin)) {
            String proto = request.getHeader("X-Forwarded-Proto");
            String host = request.getHeader("X-Forwarded-Host");
            if (StringUtils.hasText(proto) && StringUtils.hasText(host)) {
                origin = proto + "://" + host;
            } else {
                origin = request.getScheme() + "://" + request.getHeader("Host");
            }
        }
        return origin + request.getContextPath() + "/#/tasks";
    }
}
