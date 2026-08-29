package com.xzcpc.template.service;

/**
 * 物料主数据同步服务：从 xinfo API 拉取原料+半成品，全量刷新 material /
 * material_inventory_rule / material_conversion_rule 三张表。
 */
public interface MaterialSyncService {

    /**
     * 执行一轮全量同步（GET_LOCK 防双端并发）。
     *
     * @return 本次处理的物料条数（原料+半成品，不含停用）
     */
    int sync();
}
