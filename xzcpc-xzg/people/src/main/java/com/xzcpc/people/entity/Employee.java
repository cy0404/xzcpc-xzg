package com.xzcpc.people.entity;

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
@TableName("employee")
public class Employee {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String employeeId;
    private String name;
    private String mobile;
    private String openid;
    private String gender;
    private LocalDate birthday;
    private String storeId;
    private String storeMiniappNo;
    private String storeName;
    private String role;
    private String employmentType;
    private LocalDate entryDate;
    private LocalDate leaveDate;
    private String emergencyContactName;
    private String emergencyContactPhone;
    private String remark;
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;
}
