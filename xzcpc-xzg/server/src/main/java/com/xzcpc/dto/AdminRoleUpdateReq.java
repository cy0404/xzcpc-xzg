package com.xzcpc.dto;

import lombok.Data;

import java.util.List;

@Data
public class AdminRoleUpdateReq {
    /** 角色列表，如 ["headquarters_admin", "finance_admin"] */
    private List<String> roles;
}
