package com.xzcpc.mp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xzcpc.common.exception.BusinessException;
import com.xzcpc.mp.entity.IssueFeedback;
import com.xzcpc.mp.mapper.MpIssueFeedbackMapper;
import com.xzcpc.mp.service.MpFeedbackService;
import com.xzcpc.mp.service.MpStaffService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MpFeedbackServiceImpl implements MpFeedbackService {

    /** 处理说明最大长度 */
    private static final int NOTE_MAX_LEN = 500;
    /** 凭证最大张数 */
    private static final int EVIDENCE_MAX_COUNT = 6;
    /** 未处理状态：待处理 + 处理中（已处理/已关闭不再提醒） */
    private static final List<String> UNRESOLVED_STATUS = Arrays.asList("pending", "processing");

    private final MpIssueFeedbackMapper issueFeedbackMapper;
    private final MpStaffService staffService;

    @Override
    public List<IssueFeedback> listByStore(String storeId, String status) {
        return issueFeedbackMapper.selectList(new LambdaQueryWrapper<IssueFeedback>()
                .eq(IssueFeedback::getStoreId, storeId)
                .eq(StringUtils.hasText(status), IssueFeedback::getStatus, status)
                .orderByDesc(IssueFeedback::getCreatedAt));
    }

    @Override
    public IssueFeedback detail(String storeId, Long id) {
        IssueFeedback f = issueFeedbackMapper.selectById(id);
        if (f == null) {
            throw new BusinessException("客诉记录不存在");
        }
        if (!storeId.equals(f.getStoreId())) {
            throw new BusinessException(403, "无权查看该客诉");
        }
        return f;
    }

    @Override
    public IssueFeedback markDone(String storeId, Long id, String openid, String employeeName,
                                  String processNote, String evidence) {
        IssueFeedback f = detail(storeId, id);
        if (StringUtils.hasText(processNote) && processNote.length() > NOTE_MAX_LEN) {
            throw new BusinessException("处理说明最多" + NOTE_MAX_LEN + "字");
        }
        if (StringUtils.hasText(evidence)) {
            String[] urls = evidence.split(",");
            if (urls.length > EVIDENCE_MAX_COUNT) {
                throw new BusinessException("凭证最多" + EVIDENCE_MAX_COUNT + "个");
            }
        }
        LocalDateTime now = LocalDateTime.now();
        f.setStatus("done");
        if (StringUtils.hasText(processNote)) {
            f.setProcessNote(processNote.trim());
        }
        if (StringUtils.hasText(evidence)) {
            f.setEvidence(evidence.trim());
        }
        f.setProcessedAt(now);
        if (f.getProcessingAt() == null) {
            f.setProcessingAt(now); // 未经过「处理中」直接完成时补记开始时间，时间线完整
        }
        f.setProcessedBy(openid);
        f.setProcessedName(StringUtils.hasText(employeeName) ? employeeName.trim() : null);
        issueFeedbackMapper.updateById(f);
        log.info("[feedback] store {} feedback {} marked done by {} ({})", storeId, id, employeeName, openid);
        return f;
    }

    @Override
    public List<Map<String, Object>> overviewByStores(String openid) {
        List<Map<String, Object>> stores = staffService.findStoresByOpenid(openid);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> s : stores) {
            String sid = (String) s.get("storeId");
            if (sid == null) {
                continue;
            }
            Long count = issueFeedbackMapper.selectCount(new LambdaQueryWrapper<IssueFeedback>()
                    .eq(IssueFeedback::getStoreId, sid)
                    .in(IssueFeedback::getStatus, UNRESOLVED_STATUS));
            if (count != null && count > 0) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("storeId", sid);
                item.put("storeName", s.get("storeName"));
                item.put("pending", count);
                result.add(item);
            }
        }
        return result;
    }

    @Override
    public Map<String, Long> overview(String storeId) {
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("pending", countUnresolved(storeId));
        return result;
    }

    @Override
    public Map<String, Long> overviewTotal(String openid) {
        List<Map<String, Object>> stores = staffService.findStoresByOpenid(openid);
        long total = 0;
        for (Map<String, Object> s : stores) {
            String sid = (String) s.get("storeId");
            if (sid != null) {
                total += countUnresolved(sid);
            }
        }
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("pending", total);
        return result;
    }

    /** 单店未处理客诉数（pending + processing） */
    private long countUnresolved(String storeId) {
        Long c = issueFeedbackMapper.selectCount(new LambdaQueryWrapper<IssueFeedback>()
                .eq(IssueFeedback::getStoreId, storeId)
                .in(IssueFeedback::getStatus, UNRESOLVED_STATUS));
        return c != null ? c : 0L;
    }
}
