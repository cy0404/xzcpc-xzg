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

    public Long getSessionId() { return sessionId; }
    public String getOpenid() { return openid; }
    public String getStoreId() { return storeId; }
    public String getStoreName() { return storeName; }
}
