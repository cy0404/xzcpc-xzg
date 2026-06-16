package com.xzcpc.mp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "wx.miniapp")
public class WxMaProperties {

    private String appid;

    private String secret;

    private String msgDataFormat = "JSON";

    public String getAppid() { return appid; }
    public String getSecret() { return secret; }
    public String getMsgDataFormat() { return msgDataFormat; }
}
