package com.xzcpc.mp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * P0 A4: 物流记录
 */
@Data
@TableName("logistics_record")
public class LogisticsRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long transferId;
    private String storeId;
    private String storeName;
    private String trackingNo;
    private String carrier;
    private String status;         // pending_shipment|in_transit|delivering|signed|abnormal|query_failed
    private String trackingData;   // JSON
    private String createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;
}
