package com.xzcpc.mp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * P1: 智能订货建议单明细（生成时快照物料信息）
 */
@Data
@TableName("smart_order_item")
public class SmartOrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联订货单ID */
    private Long orderId;

    /** 物料ID（关联 material.id） */
    private Long materialId;

    /** 物料名称快照 */
    private String materialName;

    /** 规格快照 */
    private String spec;

    /** 分类快照 */
    private String category;

    /** 企迈编码快照（productCode） */
    private String qmCode;

    /** 订货单位（库存单位） */
    private String stockUnit;

    /** 基础盘点单位 */
    private String baseUnit;

    /** 单价快照（元） */
    private BigDecimal unitPrice;

    /** 当前库存（基础单位） */
    private BigDecimal currentInventory;

    /** 预测日均消耗（基础单位/天：去年同期×趋势或降级值） */
    private BigDecimal dailyUse;

    /** 去年同周销量快照（基础单位） */
    private BigDecimal lastYearQty;

    /** 趋势系数（近4周÷去年同4周，clamp 0.5~2.0） */
    private BigDecimal trendFactor;

    /** 损耗修正（基础单位，近4周报损周均） */
    private BigDecimal lossQty;

    /** 调货净值修正（基础单位，近4周周均，净调出为正） */
    private BigDecimal transferQty;

    /** 还货净值修正（基础单位，近4周周均，仅还货品 goods，净还出为正；还钱不影响实物不计） */
    private BigDecimal returnQty;

    /** 在途量（基础单位，累计订货-累计到货差值法） */
    private BigDecimal inTransitQty;

    /** 自购食材修正（基础单位，近4周自购周均，减项） */
    private BigDecimal selfPurchaseQty;

    /** 配送周期天数 */
    private Integer cycleDays;

    /** 安全天数 */
    private Integer safetyDays;

    /** 建议数量（订货单位） */
    private BigDecimal suggestQty;

    /** 确认数量（订货单位） */
    private BigDecimal confirmedQty;

    /** 企迈总仓可用库存（订货单位，detail 实时查询附加，不落库；null=未查到） */
    @TableField(exist = false)
    private BigDecimal qmStock;

    /** 企迈库存单位 */
    @TableField(exist = false)
    private String qmStockUnit;

    /** 当前库存可支撑天数 */
    private BigDecimal supportDays;

    /** 建议依据 */
    private String reason;

    /** 店长近期单次订货参考量（订货单位，近90天订货节奏日均×订货周期折算）——"您近期每次约订 X" */
    private BigDecimal orderRefQty;

    /** 系统建议与店长近期订货偏差>30% 标记（1=需店长确认；店长确认时以店长修改为准，未改则以系统建议为准） */
    private Integer needsReview;

    /** 排序号 */
    private Integer sortNo;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;

    @Version
    private Integer version;
}
