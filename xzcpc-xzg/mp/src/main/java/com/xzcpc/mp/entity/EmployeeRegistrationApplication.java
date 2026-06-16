package com.xzcpc.mp.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("employee_registration_application")
public class EmployeeRegistrationApplication {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String applicationId;
    private String openid;
    private String storeId;
    private String storeName;
    private String name;
    private String mobile;
    private String gender;
    private LocalDate birthday;
    private String expectedRole;
    private String employmentType;
    private LocalDate entryDate;
    private String emergencyContactName;
    private String emergencyContactPhone;
    private String remark;
    private String status;
    private String rejectReason;
    private String approverOpenid;
    private LocalDateTime approvedAt;
    private String employeeId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;
}
