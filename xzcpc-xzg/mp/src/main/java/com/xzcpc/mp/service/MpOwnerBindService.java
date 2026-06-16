package com.xzcpc.mp.service;

import com.xzcpc.mp.dto.OwnerBindReq;
import com.xzcpc.mp.dto.OwnerBindStatusResp;
import com.xzcpc.mp.dto.OwnerMyStatusResp;

/**
 * 老板微信绑定服务。
 * 老板扫描总部生成的二维码 → 填写姓名+手机号+生日 →
 * 系统用手机号+生日匹配 store_info 表中的门店数据：
 *   匹配成功 → 自动绑定（更新 owner_openid）
 *   匹配失败 → 进入总部人工审核
 */
public interface MpOwnerBindService {

    /**
     * 提交老板绑定申请。
     * @param req 绑定请求（bindCode, wxCode, name, phone, birthday）
     * @return 绑定结果（auto_bound / pending）
     */
    OwnerBindStatusResp submitBind(OwnerBindReq req);

    /**
     * 查询绑定状态（通过 bindCode）。
     * @param bindCode 绑定码
     * @return 当前状态
     */
    OwnerBindStatusResp getBindStatus(String bindCode);
    /**
     * 生成小程序码图片（PNG 二进制）。
     * 调用微信 getwxacodeunlimit，scene=bindCode，page=pages/bind/owner-register。
     */
    byte[] generateQrCode(String bindCode);

    /**
     * 登录时查询当前微信的绑定状态（通过 wxCode 换 openid 后查）。
     * @param wxCode 微信登录 code
     * @return 已绑定门店信息 + 最新申请状态
     */
    OwnerMyStatusResp getMyStatus(String wxCode);

    /**
     * 通过 openid 查询最新绑定申请（用于首页 banner）。
     */
    OwnerBindStatusResp getLatestApplicationByOpenid(String openid);
}
