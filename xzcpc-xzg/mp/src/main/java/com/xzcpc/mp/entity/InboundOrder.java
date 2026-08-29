package com.xzcpc.mp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 入库单（企迈控制台入库单本地缓存）。
 *
 * 数据来源：inapi.qmai.cn/gw/scm/console/inbound/order/list
 * 关联来源：企迈 OpenAPI 报货单 → 通过 bizNo 匹配
 *
 * 控制台 status → 本地 localStatus 映射：
 *   1=待入库 → pending
 *   2=已入库 → done
 *   3=已关闭 → cancelled
 */
@Data
@TableName("inbound_order")
public class InboundOrder {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 本地门店ID */
    private String storeId;

    /** 企迈门店ID（归属匹配层级1：按门店拉报货单的键） */
    private Long qmaiStoreId;

    /** 企迈控制台仓库ID（9.2.2 warehouseId，确认收货时 warehouseMark 兜底） */
    private String warehouseId;

    /** 企迈开放平台仓库编码（9.2.2 warehouseCode，确认收货时回传 warehouseNo 优先用） */
    private String warehouseCode;

    /** 仓库名称 */
    private String warehouseName;

    /** 入库单号（控制台 inboundNo） */
    private String inboundNo;

    /** 关联单号（控制台 bizNo：采购→CG* / 退货→R*），用于匹配报货单 */
    private String bizNo;

    /** 来源报货单号（匹配到的 OpenAPI declareNo），空表示未匹配 */
    private String sourceDeclareNo;

    /** 来源订货单号（匹配到的 OpenAPI requireNo），空表示未匹配 */
    private String sourceRequireNo;

    /** 入库时间（控制台 inboundAt） */
    private LocalDateTime inboundAt;

    /** 单据日期（控制台 documentDate） */
    private String documentDate;

    /** 入库类型：1期初 2盘盈 3采购 4调拨 5退货 6其他 10加工 */
    private Integer inboundType;

    /** 控制台原始状态：1待入库 2已入库 3已关闭 */
    private Integer status;

    /** 入库金额（元） */
    private BigDecimal amount;

    /** 货品总数量 */
    private BigDecimal productAllNum;

    /** 货品种类数 */
    private Integer productTypeNum;

    /** 创建人 */
    private String creator;

    /** 入库人 */
    private String inboundPerson;

    /** 备注 */
    private String remark;

    /** 控制台创建时间 */
    private LocalDateTime createdAtQmai;

    /** 本地入库状态：pending=待入库 done=已入库 cancelled=已关闭 */
    private String localStatus;

    /** 本地入库完成时间 */
    private LocalDateTime receivedAt;

    /** 入库操作人 */
    private String receivedBy;

    @Version
    private Integer version;

    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public boolean canReceive() {
        return "pending".equals(localStatus);
    }
}
