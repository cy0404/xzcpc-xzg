package com.xzcpc.task.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 门店订货周期配置表：每店每周固定订货日（可多选 1-7，逗号分隔）。
 * 周盘任务按订货周期下发：订货日前一天自动生成（盘点日 = 订货日-1）。
 * 替代 store_info.weekly_inventory_day / weekly_paused。
 */
@Data
@TableName("store_order_cycle")
public class StoreOrderCycle {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 门店ID（store_info.store_id 外部门店ID，与 task.store_id 同源） */
    private String storeId;

    /** 订货日(1-7逗号分隔,1=周一)，如 "1,4" 表示周一、周四订货 */
    private String orderDays;

    /** 暂停周盘: 0参与 1暂停(暂停后自动生成跳过该店) */
    private Integer paused;

    @Version
    private Integer version;

    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
