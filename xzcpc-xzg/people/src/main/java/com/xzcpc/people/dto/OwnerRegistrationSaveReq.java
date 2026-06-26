package com.xzcpc.people.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OwnerRegistrationSaveReq {

    @NotBlank(message = "姓名不能为空")
    private String name;

    @NotBlank(message = "手机号不能为空")
    private String phone;

    @NotBlank(message = "角色不能为空")
    private String role;
}
