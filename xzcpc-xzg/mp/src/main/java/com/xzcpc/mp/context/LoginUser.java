package com.xzcpc.mp.context;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginUser {
    private Long sessionId;
    private String openid;
    private String storeId;
    private String storeName;
    private String employeeName;
    /** P0 角色：store_manager | owner | staff */
    private String role;

    public LoginUser(Long sessionId, String openid, String storeId, String storeName) {
        this(sessionId, openid, storeId, storeName, null, null);
    }

    public LoginUser(Long sessionId, String openid, String storeId, String storeName, String employeeName) {
        this(sessionId, openid, storeId, storeName, employeeName, null);
    }

    public Long getSessionId() { return sessionId; }
    public String getOpenid() { return openid; }
    public String getStoreId() { return storeId; }
    public String getStoreName() { return storeName; }
    public String getEmployeeName() { return employeeName; }
    public String getRole() { return role; }

    /** P0 角色判断：是否为店长（含老板继承） */
    public boolean isStoreManagerOrOwner() {
        return "store_manager".equals(role) || "owner".equals(role);
    }

    /** P0 角色判断：是否仅为普通员工 */
    public boolean isStaffOnly() {
        return "staff".equals(role);
    }
}
