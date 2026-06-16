package com.xzcpc.dto;

import lombok.Data;

/**
 * 飞书登录请求。
 */
@Data
public class FeishuLoginReq {
    /** 飞书 H5 跳转的临时授权码 */
    private String code;

    /** 发起飞书 H5 授权时使用的回调地址，换取 user_access_token 时需保持一致 */
    private String redirectUri;
}
