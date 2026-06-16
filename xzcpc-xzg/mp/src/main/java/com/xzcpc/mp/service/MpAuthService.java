package com.xzcpc.mp.service;

import com.xzcpc.mp.dto.BindStoreReq;
import com.xzcpc.mp.dto.LoginResp;

import java.util.List;
import java.util.Map;

public interface MpAuthService {
    LoginResp wxLogin(String code, String wxNickname);
    LoginResp bindStore(Long sessionId, BindStoreReq req);
    LoginResp switchStore(Long sessionId, BindStoreReq req);
    void logout(Long sessionId);
    LoginResp me(Long sessionId);

    /**
     * 获取当前 openid 关联的所有门店列表。
     */
    List<Map<String, Object>> myStores(Long sessionId);
}
