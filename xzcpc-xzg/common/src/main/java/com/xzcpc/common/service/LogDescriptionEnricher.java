package com.xzcpc.common.service;

/**
 * 日志描述增强器：根据 ID 查询名称，拼入操作日志描述。
 * 由 server 模块实现，common 模块只定义接口。
 */
public interface LogDescriptionEnricher {
    /**
     * 根据模块和描述中的 ID 信息，返回增强后的描述。
     * 如 "修改任务#281物料#cm...总量为#50" → "修改测试门店6月任务的话梅饮料浓浆总量为50"
     */
    String enrich(String module, String operation, String rawDescription);
}
