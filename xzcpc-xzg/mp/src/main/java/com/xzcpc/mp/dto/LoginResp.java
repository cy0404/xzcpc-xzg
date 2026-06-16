package com.xzcpc.mp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class LoginResp {
    private String token;
    /** 是否已绑定门店（旧字段名沿用），bound=true 才能进入业务页面 */
    private boolean bound;
    private String storeId;
    private String storeName;
    private String employeeId;
    private String employeeName;
    private String role;
    private boolean staffBound;
    private List<String> permissions;

    private int storeCount;

    public LoginResp(String token, boolean bound, String storeId, String storeName) {
        this.token = token;
        this.bound = bound;
        this.storeId = storeId;
        this.storeName = storeName;
        this.employeeId = "";
        this.employeeName = "";
        this.role = "";
        this.staffBound = false;
        this.permissions = List.of();
        this.storeCount = 1;
    }
}
