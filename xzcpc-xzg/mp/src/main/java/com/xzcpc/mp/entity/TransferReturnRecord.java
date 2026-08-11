package com.xzcpc.mp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 调货归还记录
 */
@Data
@TableName("transfer_return_record")
public class TransferReturnRecord {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务ID */
    private String recordId;

    /** 关联调货单ID */
    private Long transferId;

    /** 关联物料明细ID */
    private Long itemId;

    /** 归还类型: goods(还货)|money(还钱) */
    private String returnType;

    /** 归还数量 */
    private BigDecimal returnQty;

    /** 归还金额 */
    private BigDecimal returnAmount;

    /** 单价快照 */
    private BigDecimal unitPrice;

    /** 备注 */
    private String remark;

    /** 经手人 */
    private String handlerName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
