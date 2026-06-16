package com.xzcpc.mp.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WxLoginReq {
    @NotBlank(message = "code不能为空")
    private String code;

    private String wxNickname;
}
