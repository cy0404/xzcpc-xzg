package com.xzcpc.mp.service;

import java.util.List;
import java.util.Map;

public interface MpTaskService {
    Map<String, Object> list(String storeId);
    Map<String, Object> detail(Integer taskId, String storeId);
    void submit(Integer taskId, String storeId, String openid);
    Map<String, Object> summary(Integer taskId, String storeId);
    Map<String, Object> result(Integer taskId, String storeId);
    /** 获取任务中所有未录入物料列表（按分区分组） */
    List<Map<String, Object>> getUnenteredMaterials(Integer taskId, String storeId);
}
