package com.xzcpc.mp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * P0 A3: 调货单
 *
 * 7 状态流转：
 * pending_confirm → confirmed → pending_ship → pending_receive → completed
 * pending_confirm → cancelled
 * pending_confirm → rejected
 */
@Data
@TableName("transfer_order")
public class TransferOrder {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务编码 */
    private String bizCode;

    /** 调出门店ID */
    private String fromStoreId;

    /** 调出门店名称 */
    private String fromStoreName;

    /** 调入门店ID */
    private String toStoreId;

    /** 调入门店名称 */
    private String toStoreName;

    /** 状态：pending_confirm|confirmed|pending_ship|pending_receive|completed|cancelled|rejected|returned */
    private String status;

    /** 调货总数量 */
    private BigDecimal totalQty;

    /** 交接方式 */
    private String handoff;

    /** 备注/调货原因 */
    private String remark;

    /** 发起门店ID（调入方） */
    private String creatorStoreId;

    /** 发起人 openid */
    private String createdBy;

    /** 确认人 openid（调出方） */
    private String confirmedBy;

    /** 发货人 openid */
    private String shippedBy;

    /** 收货人 openid（调入方） */
    private String receivedBy;

    /** 确认时间 */
    private LocalDateTime confirmedAt;

    /** 发货时间 */
    private LocalDateTime shippedAt;

    /** 收货时间 */
    private LocalDateTime receivedAt;

    /** 完成时间 */
    private LocalDateTime completedAt;

    /** 取消时间 */
    private LocalDateTime cancelledAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;

    @TableField(exist = false)
    private String supervisorName;

    @Version
    private Integer version;

    /** 物品名称摘要（仅列表查询时填充，非数据库字段） */
    @TableField(exist = false)
    private String itemNames;

    /** 发起人姓名（仅列表查询时填充，非数据库字段） */
    @TableField(exist = false)
    private String createdByName;

    public boolean isTerminal() {
        return "completed".equals(status) || "cancelled".equals(status) || "rejected".equals(status) || "returned".equals(status);
    }

    /**
     * 验证状态转换是否合法
     */
    public void assertCanTransition(String action) {
        switch (action) {
            case "confirm" -> { if (!"pending_confirm".equals(status)) throw new IllegalStateException("仅待确认的调货单可确认"); }
            case "cancel"   -> { if (!"pending_confirm".equals(status)) throw new IllegalStateException("仅待确认的调货单可取消"); }
            case "reject"   -> { if (!"pending_confirm".equals(status)) throw new IllegalStateException("仅待确认的调货单可拒绝"); }
            case "ship"     -> { if (!"confirmed".equals(status)) throw new IllegalStateException("仅已确认的调货单可发货"); }
            case "receive"  -> { if (!"pending_ship".equals(status)) throw new IllegalStateException("仅待收货状态可确认收货"); }
            case "complete" -> { if (!"pending_receive".equals(status)) throw new IllegalStateException("仅待收货确认可完成"); }
            default -> throw new IllegalStateException("未知操作: " + action);
        }
    }
}
