package com.xzcpc.mp.service;

import com.xzcpc.mp.dto.ConfirmBindReq;
import com.xzcpc.mp.dto.OwnerBindStatusResp;
import com.xzcpc.mp.dto.OwnerMyStatusResp;
import com.xzcpc.mp.dto.StoreMatchResp;

/**
 * 老板微信绑定服务。
 * 扫码 → 填姓名+手机号 → 查匹配门店 → 选门店 → 确认绑定。
 */
public interface MpOwnerBindService {

    /**
     * 查询匹配的门店列表（按姓名+手机号）。
     */
    StoreMatchResp queryStores(String bindCode, String wxCode, String name, String phone);

    /**
     * 确认绑定选中门店。
     */
    OwnerBindStatusResp confirmBind(ConfirmBindReq req);

    /**
     * 查询绑定状态（通过 bindCode）。
     */
    OwnerBindStatusResp getBindStatus(String bindCode);

    /** 生成小程序码 */
    byte[] generateQrCode(String bindCode);

    /** 登录时查询绑定状态 */
    OwnerMyStatusResp getMyStatus(String wxCode);

    /** 首页 banner 查最新申请 */
    OwnerBindStatusResp getLatestApplicationByOpenid(String openid);
}
