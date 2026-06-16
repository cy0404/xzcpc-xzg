package com.xzcpc.mp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class StaffUpdateReq {

    @NotBlank(message = "姓名不能为空")
    private String name;

    @NotBlank(message = "手机号不能为空")
    private String mobile;

    private String gender;
    private LocalDate birthday;

    @NotBlank(message = "岗位不能为空")
    private String role;

    @NotBlank(message = "用工类型不能为空")
    private String employmentType;

    @NotNull(message = "入职日期不能为空")
    private LocalDate entryDate;

    private String emergencyContactName;
    private String emergencyContactPhone;
    private String remark;
}
