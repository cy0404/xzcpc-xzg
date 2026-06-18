package com.xzcpc.mp.config;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.api.impl.WxMaServiceImpl;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import cn.binarywang.wx.miniapp.config.impl.WxMaDefaultConfigImpl;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.error.WxErrorException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Slf4j
@Configuration
@Profile("dev")
public class MockConfig {

    private final WxMaProperties properties;

    public MockConfig(WxMaProperties properties) {
        this.properties = properties;
    }

    @Bean
    public WxMaService wxMaServiceMock() {
        log.warn("=== 开发模式：使用 Mock WxMaService，wx.login 返回模拟 openid ===");

        WxMaDefaultConfigImpl config = new WxMaDefaultConfigImpl();
        config.setAppid(properties.getAppid());
        config.setSecret(properties.getSecret());

        return new WxMaServiceImpl() {
            @Override
            public WxMaJscode2SessionResult jsCode2SessionInfo(String code) throws WxErrorException {
                // dev 模式使用固定 openid，确保每次登录都能匹配 Employee 表中的 openid 记录
                String openid = code != null && code.startsWith("dev_") ? code : "oK2o33VPGXkBpwW5Cu2JmuWnO2Xk";
                log.info("Mock jsCode2SessionInfo code={} → openid={}", code, openid);
                WxMaJscode2SessionResult result = new WxMaJscode2SessionResult();
                result.setOpenid(openid);
                result.setSessionKey("mock_session_key_" + code);
                return result;
            }
        };
    }
}
