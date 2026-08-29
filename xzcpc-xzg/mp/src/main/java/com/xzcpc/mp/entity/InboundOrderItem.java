package com.xzcpc.mp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;

/**
 * 入库单商品明细
 */
@Data
@TableName("inbound_order_item")
public class InboundOrderItem {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 入库单ID */
    private Long inboundOrderId;

    /** 企迈商品编码 */
    private String productCode;

    /** 企迈商品ID */
    private Long productId;

    /** 商品名称 */
    private String productName;

    /** 规格 */
    private String productSpec;

    /** 订货单位 */
    private String productUnit;

    /** 报货数量 */
    private BigDecimal productNum;

    /** 单价（元） */
    private BigDecimal price;

    /** 金额（元） */
    private BigDecimal amount;

    /** 是否赠品 1=是 0=否 */
    private Integer isGift;

    /** 商品图片 */
    private String imgUrl;

    /** 履约方名称 */
    private String performanceName;

    /** 已收货数量 */
    private BigDecimal receivedQty;

    /** 是否已收货 0=否 1=是 */
    private Integer received;

    @Version
    private Integer version;

    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;
}
