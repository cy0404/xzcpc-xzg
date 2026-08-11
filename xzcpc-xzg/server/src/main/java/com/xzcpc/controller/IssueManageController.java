package com.xzcpc.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.common.response.R;
import com.xzcpc.mp.entity.Issue;
import com.xzcpc.mp.service.IssueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * P0 B1: 总部端问题台账（只读）。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/issue")
@RequiredArgsConstructor
public class IssueManageController {

    private final IssueService issueService;
    private final RestTemplate restTemplate;

    @Value("${xiangmu.base-url:http://127.0.0.1:9000}")
    private String xiangmuBaseUrl;

    @Value("${xiangmu.records-api-key:}")
    private String recordsApiKey;

    /** 总部只读台账 */
    @GetMapping("/list")
    public R<Page<Issue>> list(
            @RequestParam(required = false) String storeId,
            @RequestParam(required = false) String supervisorName,
            @RequestParam(required = false) String issueType,
            @RequestParam(required = false) String urgency,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String source,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(issueService.pageAll(storeId, supervisorName, issueType, urgency, status, keyword,
                startDate, endDate, source, pageNum, pageSize));
    }

    /** 问题详情 */
    @GetMapping("/{id}")
    public R<Issue> detail(@PathVariable Long id) {
        return R.ok(issueService.detailForAdmin(id));
    }

    /** 代理查询外部处理记录（总部端用） */
    @GetMapping("/{id}/records")
    public R<Map<String, Object>> records(@PathVariable Long id) {
        Issue issue = issueService.detailForAdmin(id);
        String xiangmuId = issue.getXiangmuId();
        if (xiangmuId == null || xiangmuId.isEmpty()) {
            return R.ok(Map.of("records", java.util.List.of(), "replies", java.util.List.of(), "solutionPhotos", java.util.List.of()));
        }
        String url = xiangmuBaseUrl + "/api/mini-program/issue/" + xiangmuId + "/records";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Api-Key", recordsApiKey);
        try {
            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> body = resp.getBody();
                Object raw = body.getOrDefault("data", body);
                if (raw instanceof Map<?, ?> dm) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) dm;
                    // 相对路径补全为绝对 URL
                    prefixRelativeUrls(data, "solutionPhotos");
                    prefixRelativeUrls(data, "records", "photos");
                    prefixRelativeUrls(data, "records", "mediaUrls");
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> replies = (List<Map<String, Object>>) data.get("replies");
                    if (replies != null) {
                        for (Map<String, Object> reply : replies) {
                            prefixRelativeUrls(reply, "mediaUrls");
                        }
                    }
                    return R.ok(data);
                }
                return R.ok(body);
            }
        } catch (Exception e) {
            log.warn("[issue] admin records proxy failed: id={} xiangmuId={} url={}", id, xiangmuId, url, e);
            return R.ok(Map.of("records", java.util.List.of(), "replies", java.util.List.of(), "solutionPhotos", java.util.List.of()));
        }
        return R.ok(Map.of());
    }

    @SuppressWarnings("unchecked")
    private void prefixRelativeUrls(Map<String, Object> data, String field) {
        Object val = data.get(field);
        if (val == null) return;
        if (val instanceof List<?> list) {
            List<String> fixed = new java.util.ArrayList<>();
            for (Object item : list) {
                if (item instanceof String s) fixed.add(prefixUrl(s));
            }
            data.put(field, fixed);
        }
    }

    @SuppressWarnings("unchecked")
    private void prefixRelativeUrls(Map<String, Object> data, String parent, String child) {
        Object val = data.get(parent);
        if (!(val instanceof List<?> list)) return;
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                Map<String, Object> record = (Map<String, Object>) m;
                prefixRelativeUrls(record, child);
            }
        }
    }

    private String prefixUrl(String url) {
        if (url == null || url.isEmpty()) return url;
        if (url.startsWith("http")) return url;
        if (url.startsWith("/uploads")) return xiangmuBaseUrl + url;
        return url;
    }
}
