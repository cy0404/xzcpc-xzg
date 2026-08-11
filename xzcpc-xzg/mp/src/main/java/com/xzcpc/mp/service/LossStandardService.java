package com.xzcpc.mp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xzcpc.mp.entity.LossStandard;
import com.xzcpc.mp.mapper.LossStandardMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class LossStandardService {

    private final LossStandardMapper lossStandardMapper;

    /**
     * 根据物料ID查水果验收标准 + 根据物料分类查视频上传标准
     */
    public Map<String, Object> getByMaterial(String materialId, String category) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 水果验收标准：按 material_id 匹配
        List<LossStandard> fruitList = lossStandardMapper.selectList(
                new LambdaQueryWrapper<LossStandard>()
                        .eq(LossStandard::getStandardType, "fruit_check")
                        .eq(LossStandard::getMaterialId, materialId)
                        .eq(LossStandard::getStatus, 1)
                        .last("LIMIT 1"));
        if (!fruitList.isEmpty()) {
            result.put("fruitCheck", toMap(fruitList.get(0)));
        }

        // 视频上传标准：按 category 匹配
        List<LossStandard> videoList = lossStandardMapper.selectList(
                new LambdaQueryWrapper<LossStandard>()
                        .eq(LossStandard::getStandardType, "video_upload")
                        .eq(LossStandard::getMaterialCategory, category)
                        .eq(LossStandard::getStatus, 1)
                        .last("LIMIT 1"));
        if (!videoList.isEmpty()) {
            result.put("videoUpload", toMap(videoList.get(0)));
        }

        return result;
    }

    private Map<String, Object> toMap(LossStandard s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("standardType", s.getStandardType());
        m.put("title", s.getTitle() != null ? s.getTitle() : "");
        m.put("description", s.getDescription() != null ? s.getDescription() : "");
        List<String> urls = new ArrayList<>();
        if (s.getMediaUrls() != null && !s.getMediaUrls().isEmpty()) {
            urls = Arrays.asList(s.getMediaUrls().split(","));
        }
        m.put("mediaUrls", urls);
        return m;
    }
}
