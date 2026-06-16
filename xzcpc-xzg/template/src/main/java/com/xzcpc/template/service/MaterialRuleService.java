package com.xzcpc.template.service;

import com.xzcpc.template.dto.MaterialRuleResp;
import com.xzcpc.template.dto.MaterialRuleSaveReq;

import java.util.List;
import java.util.Map;

public interface MaterialRuleService {

    Map<String, Object> page(String keyword, String parentCategory, String category,
                             String baseUnit, String conversionType,
                             int pageNum, int pageSize);

    MaterialRuleResp detail(String materialId);

    MaterialRuleResp save(String materialId, MaterialRuleSaveReq req);

    /** 返回全部不重复的基础盘点单位列表 */
    List<String> getAllBaseUnits();

    /** 批量查询盘点规则，避免 N+1 */
    Map<String, MaterialRuleResp> batchDetail(List<String> materialIds);
}
