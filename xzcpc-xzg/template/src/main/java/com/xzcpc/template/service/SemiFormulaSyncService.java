package com.xzcpc.template.service;

/**
 * 半成品成本卡（BOM 配方）同步服务。
 * 从 xinfo API 拉取全部半成品（含内嵌 formulaVersions），落库 semi_product /
 * semi_formula_version / semi_formula_item，供差异计算爆炸使用。
 */
public interface SemiFormulaSyncService {

    /** 全量同步一次，返回处理的半成品数（未获锁返回 0） */
    int sync();
}
