package com.xzcpc.mp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 小程序订阅消息授权额度：用户授权 +1（每次进首页/关键提交动作静默请求），发送 -1。
 * 额度不足时该条消息不发微信，靠首页待办卡兜底。
 */
@Data
@TableName("notification_quota")
public class NotificationQuota {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String openid;
    private Integer quota;
    private LocalDateTime updatedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
