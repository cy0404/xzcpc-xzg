package com.xzcpc.mp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 企迈商品订货约束（起订量 / 订货倍数 / 限购）。
 * 企迈下单页对部分商品有「起订数量 / 订货倍数 / 限购数量」约束，智能订货按缺口精算会算出
 * 下不了单的量（如酸角起订 12 瓶、建议 2 瓶），店长必须手动改成整件。
 * 生成建议量时按 max(minOrderQty, ceil(建议量 / orderMultiple) × orderMultiple) 取整。
 * 数据来源：企迈控制台订货模板（MpQmaiConsole 拉取，按 qm_code 幂等 upsert）。
 */
@Data
@TableName("material_order_constraint")
public class MaterialOrderConstraint {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 企迈商品编码（如 WP0733） */
    private String qmCode;

    /** 本地物料业务码（关联 material.material_id，冗余便于关联） */
    private String materialId;

    /** 物料名称快照 */
    private String materialName;

    /** 约束口径单位（订货单位，如 瓶/g/份/件） */
    private String orderUnit;

    /** 起订数量（建议量低于此值则提升到此值；null=无约束） */
    private BigDecimal minOrderQty;

    /** 订货倍数（建议量向上取整到该倍数；null=无约束） */
    private BigDecimal orderMultiple;

    /** 单次限购上限（null=不限购） */
    private BigDecimal limitQty;

    /** 来源：qmai_console=企迈控制台订货模板 */
    private String source;

    /** 备注（规格 / 模板启用状态） */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;
    @Version
    private Integer version;
}
