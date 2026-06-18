package com.xzcpc.mp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 老板绑定 - 确认绑定请求
 */
@Data
public class ConfirmBindReq {

    @NotBlank(message = "绑定码不能为空")
    private String bindCode;

    @NotBlank(message = "微信授权码不能为空")
    private String wxCode;

    @NotBlank(message = "姓名不能为空")
    private String name;

    @NotBlank(message = "手机号不能为空")
    private String phone;

    /** 用户选择的门店ID列表 */
    @NotEmpty(message = "请至少选择一个门店")
    private List<String> storeIds;
}
