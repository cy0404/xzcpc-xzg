package com.xzcpc.controller;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.xzcpc.common.model.StoreInfo;
import com.xzcpc.common.response.R;
import com.xzcpc.entity.IssueFeedback;
import com.xzcpc.service.IssueFeedbackService;
import com.xzcpc.task.service.StoreService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 扫码问题反馈公开接口（H5 表单页调用，总部端）。
 * 路径 /api/xzg/**，AdminWebMvcConfig 已放行，无需登录。
 * 用项目代号前缀避免与服务器上其他项目的 /api/** 冲突。
 * 独立于小程序：仅上传图片代理复用 mp-server 的文件存储。
 */
@Slf4j
@RestController
@RequestMapping("/api/xzg/feedback")
@RequiredArgsConstructor
public class FeedbackPublicController {

    private final IssueFeedbackService issueFeedbackService;
    private final StoreService storeService;
    private final RestTemplate restTemplate;

    /** 门店全量列表缓存：全国扫码搜索复用，10 分钟过期重查，避免每次请求都查库 */
    private final Cache<String, List<StoreInfo>> storeCache = Caffeine.newBuilder()
            .maximumSize(1)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();

    /** 反馈类型选项（sys_config 可配） */
    @GetMapping("/options")
    public R<List<String>> options() {
        return R.ok(issueFeedbackService.getTypeOptions());
    }

    /** 门店搜索（按名称/编码，复用 store_info 数据，带 10 分钟缓存） */
    @GetMapping("/stores")
    public R<List<Map<String, Object>>> stores(@RequestParam(value = "keyword", required = false) String keyword) {
        List<StoreInfo> all = storeCache.get("all", k -> storeService.getAllStores());
        String kw = StringUtils.hasText(keyword) ? keyword.trim().toLowerCase() : null;
        List<Map<String, Object>> result = new ArrayList<>();
        for (StoreInfo s : all) {
            if (kw != null) {
                String name = s.getMendianmingcheng() == null ? "" : s.getMendianmingcheng().toLowerCase();
                String code = s.getBianma() == null ? "" : s.getBianma().toLowerCase();
                // 名称/编码匹配兜底：名称不带城市前缀的店（如「翠湖店」）搜「昆明」会漏，
                // 补上 xinfo 的省/市/区匹配，搜城市/区县名也能全量搜出
                String region = ((s.getProvince() == null ? "" : s.getProvince())
                        + (s.getCity() == null ? "" : s.getCity())
                        + (s.getDistrict() == null ? "" : s.getDistrict())).toLowerCase();
                if (!name.contains(kw) && !code.contains(kw) && !region.contains(kw)) {
                    continue;
                }
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("storeId", s.getId());
            item.put("storeName", displayName(s));
            item.put("address", s.getAddress());
            result.add(item);
        }
        return R.ok(result);
    }

    /**
     * 门店展示名：优先小象（xinfo）名称，格式「XX（YY）」时取括号内 YY；无括号取整体；
     * xinfo 为空退回企迈名称。结果再统一去除品牌前缀「象子茶铺茶」。
     */
    private static String displayName(StoreInfo s) {
        String name = StringUtils.hasText(s.getXinfoStoreName())
                ? s.getXinfoStoreName() : s.getMendianmingcheng();
        if (StringUtils.hasText(name)) {
            int l = name.indexOf('（');
            int r = name.indexOf('）');
            if (l < 0) {
                l = name.indexOf('(');
                r = name.indexOf(')');
            }
            if (l >= 0 && r > l) {
                name = name.substring(l + 1, r).trim();
            }
        }
        return cleanStoreName(name);
    }

    /** 门店名称去除品牌前缀「象子茶铺茶」，搜索结果与提交记录统一展示干净名称 */
    private static String cleanStoreName(String name) {
        return name == null ? null : name.replace("象子茶铺茶", "");
    }

    /** 提交反馈（ip 用于限频防刷；仅手机端 UA 可提交，防 PC 绕过前端灌数据） */
    @PostMapping("/submit")
    public R<Void> submit(@RequestBody Map<String, String> body, HttpServletRequest request) {
        if (!isMobileUa(request.getHeader("User-Agent"))) {
            return R.fail(400, "请在手机端打开提交");
        }
        issueFeedbackService.submit(
                body.get("feedbackType"),
                body.get("channel"),
                body.get("storeId"),
                body.get("storeName"),
                body.get("phone"),
                body.get("content"),
                body.get("images"),
                resolveClientIp(request));
        return R.ok();
    }

    /** 顾客凭手机号查询自己的反馈进度（公开接口，手机号即凭证；IP 限频防刷） */
    @GetMapping("/query")
    public R<List<IssueFeedback>> query(@RequestParam String phone, HttpServletRequest request) {
        return R.ok(issueFeedbackService.queryByPhone(phone, resolveClientIp(request)));
    }

    /** 图片上传（代理 mp-server 文件存储，返回 {url}；仅放行图片且限 10MB，防存储滥用） */
    @PostMapping("/upload")
    public R<Map<String, String>> upload(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        if (!isMobileUa(request.getHeader("User-Agent"))) {
            return R.fail(400, "请在手机端打开");
        }
        try {
            String originalName = file.getOriginalFilename();
            String ext = originalName != null && originalName.lastIndexOf('.') > 0
                    ? originalName.substring(originalName.lastIndexOf('.')).toLowerCase() : "";
            if (!ext.matches("\\.(jpg|jpeg|png|gif|bmp|webp)")) {
                return R.fail(400, "仅支持 jpg/png/gif/bmp/webp 图片格式");
            }
            if (file.getSize() > 10L * 1024 * 1024) {
                return R.fail(400, "图片大小不能超过10MB");
            }
            ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() { return file.getOriginalFilename(); }
            };
            MultiValueMap<String, Object> map = new LinkedMultiValueMap<>();
            map.add("file", resource);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(map, headers);
            String mpUrl = "http://localhost:30261/storeInventory/api/mp/upload/voucher";
            ResponseEntity<Map> resp = restTemplate.postForEntity(mpUrl, entity, Map.class);
            Map<String, Object> body = resp.getBody();
            if (body != null && 200 == ((Number) body.getOrDefault("code", 0)).intValue()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) body.get("data");
                Map<String, String> result = new LinkedHashMap<>();
                result.put("url", String.valueOf(data.get("url")));
                return R.ok(result);
            }
            return R.fail(500, "上传失败");
        } catch (Exception e) {
            log.error("[feedback] upload failed", e);
            return R.fail(500, "上传失败");
        }
    }

    /** 仅手机端 UA 放行（含微信内置浏览器）；PC/桌面端拒绝提交与上传 */
    private static boolean isMobileUa(String ua) {
        if (ua == null) return false;
        String u = ua.toLowerCase();
        return u.contains("mobile") || u.contains("android") || u.contains("iphone")
                || u.contains("ipad") || u.contains("micromessenger");
    }

    /** 取客户端真实 IP：Nginx 反代后取 X-Forwarded-For 第一段，取不到退回 remoteAddr */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank() && !"unknown".equalsIgnoreCase(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
