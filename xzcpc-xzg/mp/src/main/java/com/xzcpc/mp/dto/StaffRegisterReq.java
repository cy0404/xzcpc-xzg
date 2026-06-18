package com.xzcpc.mp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class StaffRegisterReq {

    @NotBlank(message = "门店ID不能为空")
    private String storeId;

    @NotBlank(message = "姓名不能为空")
    private String name;

    @NotBlank(message = "手机号不能为空")
    private String mobile;

    private String gender;
    private LocalDate birthday;

    @NotBlank(message = "入职岗位不能为空")
    private String expectedRole;

    @NotBlank(message = "用工类型不能为空")
    private String employmentType;

    @NotNull(message = "入职日期不能为空")
    private LocalDate entryDate;

    private String emergencyContactName;
    private String emergencyContactPhone;
    private String remark;

    /** 重新申请时传入，覆盖旧申请 */
    private String applicationId;

    /** 微信登录 code，未登录用户提交时用于换取 openid */
    private String wxCode;
}
