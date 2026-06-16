package com.xzcpc.service;

import com.xzcpc.dto.FeishuLoginResp;

/**
 * 飞书登录服务。
 */
public interface FeishuAuthService {

    /**
     * 用飞书 H5 code 换取用户身份，生成 JWT 返回。
     */
    FeishuLoginResp login(String code, String redirectUri);
}
