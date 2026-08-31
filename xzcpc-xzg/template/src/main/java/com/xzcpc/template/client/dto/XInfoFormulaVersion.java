package com.xzcpc.template.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * xinfo 半成品配方版本（GET /api/semi-finished-products 内嵌 formulaVersions[]）。
 *
 * 2026-08-31 实测：版本层仅 id/versionName/status/totalCost/items——
 * 净出量/净出单位/得率在半成品主对象层（XInfoSemiFinishedProduct），不在此处。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class XInfoFormulaVersion {

    /** 配方版本 ID（内部主键），同步为 semi_formula_version.version_id */
    private Long id;

    /** 版本名（如 牛油果泥-配方-v1） */
    private String versionName;

    /** 状态：DRAFT / ACTIVE / DISABLED（仅 ACTIVE 用于爆炸） */
    private String status;

    /** 版本总成本（元） */
    private BigDecimal totalCost;

    /** 配方行明细 */
    private List<XInfoFormulaItem> items;
}
