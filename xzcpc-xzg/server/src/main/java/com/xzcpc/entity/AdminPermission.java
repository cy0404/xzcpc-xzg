package com.xzcpc.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.xzcpc.constant.AdminRole;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 总部端飞书用户权限。
 */
@Data
@TableName("admin_permission")
public class AdminPermission {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String openId;
    private String name;
    private String avatarUrl;
    private String email;
    private String mobile;
    /** 角色，逗号分隔多角色 */
    private String role;
    private LocalDateTime authorizedAt;
    private LocalDateTime lastLoginAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @Version
    private Integer version;

    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;

    /** 获取角色列表 */
    public List<String> getRoleList() {
        return AdminRole.parseRoles(role);
    }

    /** 是否有后台管理权限 */
    public boolean hasAdminAccess() {
        return AdminRole.hasAdminAccess(role);
    }
}
