package com.xzcpc.service;

import java.util.Map;

/**
 * 督导-门店关系同步服务。
 * 数据源：新门店接口（xinfo）GET /api/external/stores，认证 X-API-Key。
 * 对账键：接口 supervisor.userId ⇄ admin_permission.user_id（飞书企业稳定ID）。
 * 同步目标：
 *   1. supervisor_store_access（source='auto' 的行重建，手工行永不覆盖）
 *   2. store_info.supervisor_name
 */
public interface SupervisorSyncService {

    /**
     * 执行一次同步。
     *
     * @param apply true=写库（upsert access + 更新 supervisor_name）；false=仅拉取与对账，输出报告
     * @return 同步报告（含未匹配督导/无督导门店/名字不一致/失效 auto 行等，供人工确认）
     */
    Map<String, Object> sync(boolean apply);
}
