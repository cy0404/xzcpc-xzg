package com.xzcpc.mp.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.common.annotation.OpLog;
import com.xzcpc.common.exception.BusinessException;
import com.xzcpc.common.response.R;
import com.xzcpc.mp.context.UserContextHolder;
import com.xzcpc.mp.dto.IssueCreateReq;
import com.xzcpc.mp.entity.Issue;
import com.xzcpc.mp.service.IssueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * P0 B1: 门店端问题处理
 */
/**
 * P0 B1: 门店端问题处理
 */
@Slf4j
@RestController
@RequestMapping("/api/mp/issue")
@RequiredArgsConstructor
public class MpIssueController {

    private final IssueService issueService;
    private final RestTemplate restTemplate;

    @Value("${xiangmu.base-url:http://127.0.0.1:9000}")
    private String xiangmuBaseUrl;

    @Value("${xiangmu.api-key:}")
    private String xiangmuApiKey;

    @Value("${xiangmu.records-api-key:}")
    private String recordsApiKey;

    @OpLog(module = "小程序-问题处理", operation = "上报问题")
    @PostMapping
    public R<Issue> create(@Valid @RequestBody IssueCreateReq req) {
        return R.ok(issueService.create(req));
    }

    /** 点击"上报新问题"时记录日志（H5 方式上报不经过 create 接口） */
    @OpLog(module = "小程序-问题处理", operation = "上报问题")
    @PostMapping("/report-click")
    public R<Void> reportClick() {
        return R.ok();
    }

    @GetMapping("/list")
    public R<Page<Issue>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "false") boolean all) {
        if (all) {
            String openid = UserContextHolder.get().getOpenid();
            return R.ok(issueService.pageByStores(openid, status, pageNum, pageSize));
        }
        String storeId = UserContextHolder.get().getStoreId();
        return R.ok(issueService.pageByStore(storeId, status, pageNum, pageSize));
    }

    /** 全部门店待验收计数（首页用） */
    @GetMapping("/overview-stores")
    public R<List<Map<String, Object>>> overviewByStores() {
        String openid = UserContextHolder.get().getOpenid();
        return R.ok(issueService.overviewByStores(openid));
    }

    @GetMapping("/overview")
    public R<Map<String, Long>> overview(
            @RequestParam(defaultValue = "false") boolean all) {
        if (all) {
            String openid = UserContextHolder.get().getOpenid();
            return R.ok(issueService.overviewTotal(openid));
        }
        String storeId = UserContextHolder.get().getStoreId();
        return R.ok(issueService.overview(storeId));
    }

    @GetMapping("/{id}")
    public R<Issue> detail(@PathVariable Long id) {
        return R.ok(issueService.detailForAdmin(id));
    }

    @OpLog(module = "小程序-问题处理", operation = "验收问题")
    @PostMapping("/{id}/accept")
    public R<Issue> accept(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        var user = UserContextHolder.get();
        if (!user.isStoreManagerOrOwner()) {
            throw new BusinessException(403, "仅店长或老板可验收问题");
        }
        String remark = body != null ? body.getOrDefault("remark", "") : "";
        return R.ok(issueService.accept(id, remark));
    }

    /** 按外部 ID 查 issue（webview 提交后跳详情页用） */
    @GetMapping("/by-external/{externalId}")
    public R<Issue> byExternalId(@PathVariable Long externalId) {
        return R.ok(issueService.findByExternalId(externalId));
    }

    /** webview postMessage 回传：同步外部表单提交结果到 issue 表 */
    @OpLog(module = "小程序-问题处理", operation = "同步外部问题")
    @PostMapping("/sync-external")
    public R<Issue> syncExternal(@RequestBody Map<String, Object> body) {
        var user = UserContextHolder.get();
        if (!user.isStoreManagerOrOwner()) {
            throw new BusinessException(403, "仅店长或老板可上报问题");
        }
        Long externalId = parseLong(body.get("id"));
        String issueNo = (String) body.get("issueNo");
        String status = (String) body.get("status");
        if (externalId == null) {
            throw new BusinessException("缺少外部问题 ID");
        }
        return R.ok(issueService.syncExternal(externalId, issueNo, status));
    }

    /** 代理查询外部处理记录 */
    @GetMapping("/{id}/records")
    public R<Map<String, Object>> records(@PathVariable Long id) {
        return R.ok(issueService.getExternalRecords(id));
    }

    /** 代理上传文件到 task_platform */
    @PostMapping("/upload-media")
    public R<Map<String, Object>> uploadMedia(
            @RequestPart("files") MultipartFile file,
            @RequestParam(defaultValue = "") String chatId) {
        List<MultipartFile> files = new ArrayList<>();
        if (file != null && !file.isEmpty()) files.add(file);
        if (files == null || files.isEmpty()) {
            throw new BusinessException("请选择文件");
        }
        String uploadChatId = StringUtils.hasText(chatId) ? chatId : UserContextHolder.get().getStoreId();
        String url = xiangmuBaseUrl + "/api/store-issue/upload";
        List<File> tempFiles = new ArrayList<>();
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            for (MultipartFile f : files) {
                Path tmp = Files.createTempFile("issue-upload-", "-" + f.getOriginalFilename());
                f.transferTo(tmp.toFile());
                File tf = tmp.toFile();
                tempFiles.add(tf);
                body.add("files", new FileSystemResource(tf));
            }
            body.add("chatId", uploadChatId);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.POST,
                    new HttpEntity<>(body, headers), Map.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> rb = resp.getBody();
                return R.ok((Map<String, Object>) rb.getOrDefault("data", rb));
            }
            return R.fail("上传失败");
        } catch (IOException e) {
            log.error("[issue] upload failed", e);
            throw new BusinessException("上传失败，请重试");
        } finally {
            for (File tf : tempFiles) { try { tf.delete(); } catch (Exception ignored) {} }
        }
    }

    @OpLog(module = "小程序-问题处理", operation = "门店确认")
    @PostMapping("/{id}/store-confirm")
    public R<Map<String, Object>> storeConfirm(@PathVariable Long id, @RequestParam String action) {
        Issue issue = issueService.detail(id, UserContextHolder.get().getStoreId());
        if (issue == null || !org.springframework.util.StringUtils.hasText(issue.getXiangmuId())) {
            throw new BusinessException("未关联外部问题单");
        }
        String url = xiangmuBaseUrl + "/api/store-issue/" + issue.getXiangmuId() + "/store-confirm";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // 此接口无需 X-Api-Key（文档标注"无需认证"）
        Map<String, String> body = Map.of("action", action);
        try {
            log.info("[issue-store-confirm] calling: {} body={}", url, body);
            ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
            log.info("[issue-store-confirm] response: status={} body={}", resp.getStatusCode(), resp.getBody());
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> rb = resp.getBody();
                return R.ok((Map<String, Object>) rb.getOrDefault("data", rb));
            }
            // 非 2xx 但未抛异常（自定义 ResponseErrorHandler 场景）
            String errMsg = resp.getBody() != null ? resp.getBody().toString() : resp.getStatusCode().toString();
            log.warn("[issue-store-confirm] non-2xx response: {}", errMsg);
            throw new BusinessException("外部服务返回异常: " + errMsg);
        } catch (BusinessException e) {
            throw e;
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            // 4xx/5xx：尝试从响应体提取错误信息
            String detail = e.getResponseBodyAsString();
            log.error("[issue] store-confirm HTTP {} issue={} response={}", e.getStatusCode(), issue.getBizCode(), detail);
            throw new BusinessException("外部服务拒绝(" + e.getStatusCode().value() + ")，请稍后重试");
        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.error("[issue] store-confirm network error issue={}", issue.getBizCode(), e);
            throw new BusinessException("外部服务暂时不可达，请稍后重试");
        } catch (Exception e) {
            log.error("[issue] store-confirm failed issue={}", issue.getBizCode(), e);
            throw new BusinessException("操作失败，请稍后重试");
        }
    }

    @OpLog(module = "小程序-问题处理", operation = "门店回复")
    @PostMapping("/{id}/reply")
    public R<Map<String, Object>> reply(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String replyText = body.getOrDefault("replyText", "");
        if (!StringUtils.hasText(replyText)) {
            throw new BusinessException("回复内容不能为空");
        }
        String mediaUrls = body.getOrDefault("mediaUrls", "");
        return R.ok(issueService.replyExternal(id, replyText, mediaUrls));
    }

    private Long parseLong(Object o) {
        if (o == null) return null;
        try { return Long.parseLong(String.valueOf(o)); }
        catch (NumberFormatException e) { return null; }
    }
}
