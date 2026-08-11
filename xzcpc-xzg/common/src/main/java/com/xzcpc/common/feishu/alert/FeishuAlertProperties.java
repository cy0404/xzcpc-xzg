package com.xzcpc.common.feishu.alert;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 飞书异常告警配置。
 * <p>
 * 通过自定义机器人 Webhook 将未处理异常推送到飞书群。
 * Webhook URL 为空或 enabled=false 时不发送告警。
 */
@Data
@Component
@ConfigurationProperties(prefix = "feishu.alert")
public class FeishuAlertProperties {

    /** 是否启用异常告警，默认 true */
    private boolean enabled = true;

    /** 飞书群自定义机器人 Webhook 地址，为空时不发送告警 */
    private String webhookUrl = "";

    /** 应用名称，出现在告警消息标题中，用于区分总部端/小程序端 */
    private String appName = "象掌柜";
}
