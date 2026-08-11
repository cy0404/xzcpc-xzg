package com.xzcpc.mp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 督导门店访问权限映射 — P2
 * 替代直接按 store_info.supervisor_name 过滤，支持 1:N 映射（如督导领导→全部门店）。
 */
@Data
@TableName("supervisor_store_access")
public class SupervisorStoreAccess {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 admin_permission.open_id */
    private String openId;

    /** 督导/管理员姓名（冗余） */
    private String adminName;

    /** 可访问的门店ID */
    private String storeId;

    /** 门店名称（冗余） */
    private String storeName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;
}
