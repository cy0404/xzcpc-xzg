package com.xzcpc.task.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 盘点差异项（含计算明细）
 * 理论剩余 = 上月剩余 + 采购 + 订货 + 调货净值 + 还货净值 - 报损 + 自购 - 消耗
 * 差异 = 本月盘点数(adjusted_qty) - 理论剩余
 * 差异率 = abs(差异) / 理论剩余
 */
@Data
@TableName("inventory_difference")
public class InventoryDifference {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer taskId;
    private String materialId;
    private String materialName;
    private String spec;
    private String unit;

    // ---- 上月盘点剩余 ----
    private BigDecimal lastMonthQty;

    // ---- MySQL 数据来源 ----
    private BigDecimal transferNetQty;     // 调货净值（调入-调出）
    private BigDecimal returnQty;          // 还货净值
    private BigDecimal lossQty;            // 报损数量
    private BigDecimal selfPurchaseQty;    // 自购食材数量

    // ---- PostgreSQL 数据来源 ----
    private BigDecimal purchaseQty;        // 采购数量(PG)
    private BigDecimal orderQty;           // 订货数量(PG)
    private BigDecimal consumptionQty;     // 消耗数量(PG)

    // ---- 计算结果 ----
    private BigDecimal theoreticalQty;     // 理论剩余
    private BigDecimal actualQty;          // 本月盘点数（adjusted_qty）
    private BigDecimal diffQty;            // 差异数量
    private BigDecimal diffRate;           // 差异率
    private Integer isLarge;               // 是否大差异（1=是，修改后会变）
    private Integer originalIsLarge;       // 初始是否大差异（不随修改变化）

    // ---- 处理字段 ----
    private String status;                 // pending|adjusted|closed
    private String handler;
    private LocalDateTime handledAt;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;

    @Version
    private Integer version;

    /** 录入明细（非DB字段，查询时从task_material_summary填充） */
    @TableField(exist = false)
    private String unitBreakdown;

    // ---- 终态判断 ----
    public boolean isTerminal() {
        return "adjusted".equals(status) || "closed".equals(status);
    }
}
