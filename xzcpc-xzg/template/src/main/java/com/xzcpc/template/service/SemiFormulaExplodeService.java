package com.xzcpc.template.service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

/**
 * 半成品 BOM 爆炸引擎。
 *
 * 语义：半成品数量 → 配方原料数量（基础单位），rawQty = 半成品单位数量/净出量 × 配方行用量 × (1+损耗率)，
 * SEMI_FINISHED 行递归。爆炸仅适用于有 ACTIVE 配方且不在黑名单的半成品；
 * 不可爆炸（无配方/黑名单/净出量≤0/单位无法换算/递归环/目标未匹配）返回 null，调用方 fallback 保留自身行。
 *
 * 单位口径：所有输入 qty 均为该物料盘点基础单位（base_unit，调用方已统一换算）；
 * 内部经质量换算（kg/g/斤/两/mg）折算到净出单位与原料基础单位，输出为原料基础单位数量。
 */
public interface SemiFormulaExplodeService {

    /** 开关（sys_config semi_bom_explode_enabled，1 开） */
    boolean isEnabled();

    /** 半成品编码是否在黑名单（sys_config semi_no_explode_codes，逃生舱） */
    boolean isBlacklisted(String qmCode);

    /** 加载生效配方内存快照（每任务差异计算前调用一次） */
    void prepare();

    /**
     * 半成品 → 原料数量 map（key=原料 material_id，value=原料基础单位数量，4 位小数）。
     * 返回 null = 不可爆炸（调用方保留半成品自身行 + 打日志）。
     * 入参 semiMid 兼容两种身份：semi_product.semi_id 或 material.material_id
     * （盘点/报损/上月剩余传的是盘点主键 material_id，与 semi_id 不同值，内部经 qm_code 转半成品）。
     * unit 为 null/空时按该半成品 material_inventory_rule.base_unit 兜底（仍无则爆炸失败）。
     */
    Map<String, BigDecimal> explode(String semiMid, BigDecimal qty, String unit);

    /** 判定：material_id 是否为可爆炸半成品（有 ACTIVE 配方且不在黑名单） */
    boolean isExplodable(String mid);

    /** 按半成品 qm_code（WP 编码）爆炸——PG 消耗表/报损表以 qm_code 出现时的入口 */
    Map<String, BigDecimal> explodeByCode(String qmCode, BigDecimal qty, String unit);

    /** 判定：qm_code 是否为可爆炸半成品 */
    boolean isExplodableCode(String qmCode);

    /** 全部可爆炸半成品的 qm_code 集合（消耗查询扩展 item_code IN 集合用） */
    Set<String> explodableQmCodes();
}
