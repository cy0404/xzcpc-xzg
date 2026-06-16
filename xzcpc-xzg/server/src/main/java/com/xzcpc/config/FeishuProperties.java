package com.xzcpc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 飞书应用配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "feishu")
public class FeishuProperties {
    /** 飞书应用 App ID */
    private String appId;
    /** 飞书应用 App Secret */
    private String appSecret;
}
