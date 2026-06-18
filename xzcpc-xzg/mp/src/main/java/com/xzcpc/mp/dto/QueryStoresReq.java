package com.xzcpc.mp.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class QueryStoresReq {

    @NotBlank(message = "绑定码不能为空")
    private String bindCode;

    @NotBlank(message = "微信授权码不能为空")
    private String wxCode;

    @NotBlank(message = "姓名不能为空")
    private String name;

    @NotBlank(message = "手机号不能为空")
    private String phone;
}
