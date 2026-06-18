package com.xzcpc.mp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 老板绑定申请请求。
 * bindCode 由总部生成的二维码携带，wxCode 由小程序 wx.login() 获取。
 */
@Data
public class OwnerBindReq {

    /** 一次性绑定码（来自二维码参数） */
    @NotBlank(message = "绑定码不能为空")
    private String bindCode;

    /** 微信 wx.login 返回的 code，用于换取 openid */
    @NotBlank(message = "微信授权码不能为空")
    private String wxCode;

    /** 老板真实姓名 */
    @NotBlank(message = "姓名不能为空")
    private String name;

    /** 手机号（自行填写，前端校验格式） */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
}
