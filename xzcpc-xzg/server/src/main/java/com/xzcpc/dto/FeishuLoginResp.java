package com.xzcpc.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 飞书登录响应，返回给前端。
 */
@Data
@AllArgsConstructor
public class FeishuLoginResp {
    /** JWT token，后续请求放 Authorization header */
    private String token;
    /** 飞书 open_id */
    private String openId;
    /** 用户姓名 */
    private String name;
    /** 头像 */
    private String avatarUrl;
    /** 邮箱 */
    private String email;
    /** 手机号 */
    private String mobile;
    /** 权限角色（逗号分隔，如 "headquarters_admin,finance_admin"） */
    private String role;
    /** 权限角色展示名 */
    private String roleName;
    /** 是否具备后台管理员访问权限 */
    private Boolean hasAdminAccess;
}
