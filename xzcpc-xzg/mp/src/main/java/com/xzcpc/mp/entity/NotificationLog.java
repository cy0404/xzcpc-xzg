package com.xzcpc.mp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * P0 B2: 企微通知日志
 */
@Data
@TableName("notification_log")
public class NotificationLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventType;      // issue_processing|issue_resolved|logistics_abnormal|transfer_pending|transfer_receiving|order_confirmation
    private String storeId;
    private String targetOpenid;
    private String title;
    private String content;
    private String pagePath;       // 订阅消息点击跳转小程序页面（含参数，如 /pages/loss-report/list/index?tab=pending_approval）
    private String sourceId;       // 来源业务ID

    /** 入队幂等键（同日同店同人同事件唯一）：并发双实例跑 job 时靠 uk_idem_key 唯一索引拦截重复入队 */
    private String idemKey;
    private Integer status;        // 0待发送 1成功 2失败
    private String failReason;
    private Integer retryCount;
    private LocalDateTime sentAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
