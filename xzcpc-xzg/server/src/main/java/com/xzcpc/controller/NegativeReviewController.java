package com.xzcpc.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.common.annotation.OpLog;
import com.xzcpc.common.exception.BusinessException;
import com.xzcpc.common.response.R;
import com.xzcpc.task.dto.NegativeReviewSyncReq;
import com.xzcpc.task.entity.NegativeReview;
import com.xzcpc.task.service.NegativeReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 门店差评管理。
 *
 * 外部推送端点（/api/public/**，免 JWT，X-Api-Key 鉴权）：
 *   POST /api/public/negative-reviews/sync
 *
 * 总部管理端点（/api/admin/**，需 JWT）：
 *   台账列表、详情、处理更新
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class NegativeReviewController {

    private final NegativeReviewService negativeReviewService;

    @Value("${negative-review.api-key:}")
    private String syncApiKey;

    // ==================== 外部推送接口 ====================

    /**
     * 外部系统每天推送差评数据（批量 upsert）。
     * 路径 /api/public/** 已在 AdminWebMvcConfig 排除 JWT 拦截。
     */
    @OpLog(module = "总部-差评管理", operation = "外部推送差评数据")
    @PostMapping("/api/public/negative-reviews/sync")
    public R<Map<String, Object>> sync(
            @RequestHeader(value = "X-Api-Key", required = false) String reqApiKey,
            @RequestBody List<NegativeReviewSyncReq> items) {

        // API Key 鉴权
        if (!StringUtils.hasText(syncApiKey)) {
            throw new BusinessException(500, "服务端未配置 negative-review.api-key");
        }
        if (!syncApiKey.equals(reqApiKey)) {
            throw new BusinessException(403, "鉴权失败");
        }

        if (items == null || items.isEmpty()) {
            return R.ok(Map.of("count", 0, "message", "空数据"));
        }

        log.info("[negative-review-sync] 收到 {} 条差评推送", items.size());
        int count = negativeReviewService.batchSync(items);
        return R.ok(Map.of("count", count, "message", "同步完成，共 " + count + " 条"));
    }

    // ==================== 总部管理接口 ====================

    /**
     * 差评台账分页列表。
     */
    @GetMapping("/api/admin/negative-reviews")
    public R<Page<NegativeReview>> list(
            @RequestParam(required = false) String storeId,
            @RequestParam(required = false) String supervisorName,
            @RequestParam(required = false) String reviewPlatform,
            @RequestParam(required = false) Integer isProcessed,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(negativeReviewService.pageAll(storeId, supervisorName, reviewPlatform,
                isProcessed, startDate, endDate, keyword, pageNum, pageSize));
    }

    /**
     * 差评详情。
     */
    @GetMapping("/api/admin/negative-reviews/{id}")
    public R<NegativeReview> detail(@PathVariable Long id) {
        return R.ok(negativeReviewService.detail(id));
    }

    /**
     * 更新处理信息（指派督导、标记处理状态、填写处理说明/图片）。
     */
    @OpLog(module = "总部-差评管理", operation = "更新差评处理信息")
    @PutMapping("/api/admin/negative-reviews/{id}/process")
    public R<NegativeReview> updateProcess(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String supervisorName = str(body.get("supervisorName"));
        Integer isProcessed = body.get("isProcessed") != null
                ? Integer.parseInt(String.valueOf(body.get("isProcessed"))) : null;
        String processNote = str(body.get("processNote"));
        String processMedia = str(body.get("processMedia"));

        return R.ok(negativeReviewService.updateProcess(id, supervisorName, isProcessed, processNote, processMedia));
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
