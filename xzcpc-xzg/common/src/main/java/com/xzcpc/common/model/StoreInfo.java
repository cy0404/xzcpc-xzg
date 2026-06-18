package com.xzcpc.common.model;

import lombok.Data;

@Data
public class StoreInfo {
    private String id;
    private String mendianmingcheng;
    private String bianma;
    private String xiaochengxuid;
    private String cangkuid;
    /** 门店二维码（本地维护字段） */
    private String qrCode;
    /** 老板手机号 */
    private String ownerName;
    private String ownerPhone;
    /** 老板绑定的微信openid */
    private String ownerOpenid;
}
