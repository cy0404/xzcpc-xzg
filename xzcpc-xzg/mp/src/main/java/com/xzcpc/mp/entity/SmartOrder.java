package com.xzcpc.mp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * P1: 智能订货建议单
 *
 * 状态流转：
 * pending → syncing → success
 * pending → syncing → submit_failed →（重试确认）→ syncing → …
 */
@Data
@TableName("smart_order")
public class SmartOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务编码 */
    private String bizCode;

    /** 门店ID */
    private String storeId;

    /** 门店名称快照 */
    private String storeName;

    /** 订货周周一（ISO 周） */
    private LocalDate weekStartDate;

    /** 周标签，如 2026-W34 */
    private String weekLabel;

    /** 订货日（1-7，1=周一）：批1=门店第一个订货日（周盘提交日）；批2=order_days 第二值，无则推导(首个+4) */
    private Integer orderDay;

    /** 批次：1=首批（周盘提交即触发，库存=实盘）；2=次批（第二订货日 9:00 自动生成，库存=估算值） */
    private Integer batchNo;

    /** 来源周盘任务ID（同周两批共用一个周盘任务；幂等键 uk_store_week_batch = store+week+order_day） */
    private Long taskId;

    /** 状态：pending|syncing|success|submit_failed */
    private String status;

    /** 建议品项数 */
    private Integer itemCount;

    /** 建议数量合计（订货单位） */
    private BigDecimal totalQty;

    /** 建议金额合计（元） */
    private BigDecimal suggestAmount;

    /** 截止时间（仅展示，不强制） */
    private LocalDateTime deadline;

    /** 生成时间 */
    private LocalDateTime generatedAt;

    /** 确认人 openid */
    private String confirmedBy;

    /** 确认时间 */
    private LocalDateTime confirmedAt;

    /** 企迈报货单号 */
    private String qmaiDeclareNo;

    /** 企迈提交错误信息 */
    private String submitError;

    /** 提交尝试次数 */
    private Integer syncAttempts;

    /** 企迈订单状态（列表实时查询，不落库）：0=待支付 1=待接单 2=已接单 3=履约中 4=已完成 5=已取消 6=已驳回 */
    @TableField(exist = false)
    private Integer qmOrderStatus;

    /** 企迈支付状态（列表实时查询，不落库）：0=未支付 1=已付款 2=已审核 */
    @TableField(exist = false)
    private Integer qmPayStatus;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;

    @Version
    private Integer version;

    public boolean isActionable() {
        return "pending".equals(status) || "submit_failed".equals(status);
    }

    /** 验证状态转换是否合法 */
    public void assertCanTransition(String action) {
        switch (action) {
            case "confirm" -> {
                if (!"pending".equals(status) && !"submit_failed".equals(status)) {
                    throw new IllegalStateException("仅待确认或提交失败的订货单可确认");
                }
            }
            default -> throw new IllegalStateException("未知操作: " + action);
        }
    }
}
