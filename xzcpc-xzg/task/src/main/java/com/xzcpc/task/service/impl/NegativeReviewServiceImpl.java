package com.xzcpc.task.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.common.exception.BusinessException;
import com.xzcpc.task.dto.NegativeReviewSyncReq;
import com.xzcpc.task.entity.NegativeReview;
import com.xzcpc.task.mapper.NegativeReviewMapper;
import com.xzcpc.task.service.NegativeReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NegativeReviewServiceImpl implements NegativeReviewService {

    private final NegativeReviewMapper negativeReviewMapper;

    @Override
    @Transactional
    public int batchSync(List<NegativeReviewSyncReq> items) {
        if (items == null || items.isEmpty()) {
            return 0;
        }

        int inserted = 0;
        int updated = 0;

        for (NegativeReviewSyncReq item : items) {
            if (!StringUtils.hasText(item.getExternalId())) {
                log.warn("[negative-review-sync] 跳过无 externalId 的记录: {}", item);
                continue;
            }
            if (!StringUtils.hasText(item.getStoreId())) {
                log.warn("[negative-review-sync] 跳过无 storeId 的记录: externalId={}", item.getExternalId());
                continue;
            }

            // 按 externalId 查是否已存在
            NegativeReview existing = negativeReviewMapper.selectOne(
                    new LambdaQueryWrapper<NegativeReview>()
                            .eq(NegativeReview::getExternalId, item.getExternalId()));

            if (existing != null) {
                // 已存在：更新可变字段（内容/分数/平台可能更新）
                LambdaUpdateWrapper<NegativeReview> updateWrapper = new LambdaUpdateWrapper<>();
                updateWrapper.eq(NegativeReview::getExternalId, item.getExternalId())
                        .set(StringUtils.hasText(item.getStoreId()), NegativeReview::getStoreId, item.getStoreId())
                        .set(StringUtils.hasText(item.getStoreName()), NegativeReview::getStoreName, item.getStoreName())
                        .set(StringUtils.hasText(item.getReviewPlatform()), NegativeReview::getReviewPlatform, item.getReviewPlatform())
                        .set(item.getReviewScore() != null, NegativeReview::getReviewScore, item.getReviewScore())
                        .set(StringUtils.hasText(item.getReviewDate()), NegativeReview::getReviewDate, parseDate(item.getReviewDate()))
                        .set(NegativeReview::getReviewContent, item.getReviewContent());
                negativeReviewMapper.update(null, updateWrapper);
                updated++;
            } else {
                // 新记录：插入
                NegativeReview entity = new NegativeReview();
                entity.setExternalId(item.getExternalId());
                entity.setStoreId(item.getStoreId());
                entity.setStoreName(item.getStoreName());
                entity.setReviewPlatform(item.getReviewPlatform());
                entity.setReviewScore(item.getReviewScore());
                entity.setReviewDate(parseDate(item.getReviewDate()));
                entity.setReviewContent(item.getReviewContent());
                entity.setIsProcessed(0);
                negativeReviewMapper.insert(entity);
                inserted++;
            }
        }

        log.info("[negative-review-sync] 同步完成：新增 {} 条，更新 {} 条，共 {} 条",
                inserted, updated, items.size());
        return inserted + updated;
    }

    @Override
    public Page<NegativeReview> pageAll(String storeId, String supervisorName, String reviewPlatform,
                                         Integer isProcessed, String startDate, String endDate,
                                         String keyword, int pageNum, int pageSize) {
        LambdaQueryWrapper<NegativeReview> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(storeId), NegativeReview::getStoreId, storeId)
                .eq(StringUtils.hasText(supervisorName), NegativeReview::getSupervisorName, supervisorName)
                .eq(StringUtils.hasText(reviewPlatform), NegativeReview::getReviewPlatform, reviewPlatform)
                .eq(isProcessed != null, NegativeReview::getIsProcessed, isProcessed)
                .ge(StringUtils.hasText(startDate), NegativeReview::getReviewDate, parseDate(startDate))
                .le(StringUtils.hasText(endDate), NegativeReview::getReviewDate, parseDate(endDate))
                .and(StringUtils.hasText(keyword), w -> w
                        .like(NegativeReview::getStoreName, keyword)
                        .or()
                        .like(NegativeReview::getReviewContent, keyword))
                .orderByDesc(NegativeReview::getReviewDate)
                .orderByDesc(NegativeReview::getCreatedAt);

        return negativeReviewMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public NegativeReview detail(Long id) {
        NegativeReview entity = negativeReviewMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("差评记录不存在");
        }
        return entity;
    }

    @Override
    @Transactional
    public NegativeReview updateProcess(Long id, String supervisorName, Integer isProcessed,
                                         String processNote, String processMedia) {
        NegativeReview entity = negativeReviewMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("差评记录不存在");
        }

        if (StringUtils.hasText(supervisorName)) {
            entity.setSupervisorName(supervisorName);
        }
        if (isProcessed != null) {
            entity.setIsProcessed(isProcessed);
        }
        if (processNote != null) {
            entity.setProcessNote(processNote);
        }
        if (processMedia != null) {
            entity.setProcessMedia(processMedia);
        }

        negativeReviewMapper.updateById(entity);
        return entity;
    }

    private LocalDate parseDate(String dateStr) {
        if (!StringUtils.hasText(dateStr)) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr);
        } catch (Exception e) {
            log.warn("[negative-review] 日期解析失败: {}", dateStr);
            return null;
        }
    }
}
