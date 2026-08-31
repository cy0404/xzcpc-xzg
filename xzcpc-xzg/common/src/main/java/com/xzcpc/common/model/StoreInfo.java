package com.xzcpc.common.model;

import lombok.Data;

@Data
public class StoreInfo {
    private String id;
    private String mendianmingcheng;
    private String bianma;
    /** 门店类型: direct直营|franchise加盟（本地维护，周盘仅直营店可配订货周期） */
    private String storeType;
    /** 小象（xinfo 接口）门店名称，独立于企迈同步的 mendianmingcheng */
    private String xinfoStoreName;
    /** 省（xinfo 接口 province） */
    private String province;
    /** 地市（xinfo 接口 city） */
    private String city;
    /** 县/区（xinfo 接口 district） */
    private String district;
    /** 详细地址（xinfo 接口 address） */
    private String address;
    private String xiaochengxuid;
    private String cangkuid;
    /** 门店二维码（本地维护字段） */
    private String qrCode;
    /** 老板手机号 */
    private String ownerName;
    private String ownerPhone;
    /** 老板绑定的微信openid */
    private String ownerOpenid;
    /** 督导姓名 */
    private String supervisorName;
    /** 企迈门店ID */
    private Long qmaiStoreId;
    /** 企迈控制台仓库ID */
    private String warehouseId;
    /** 周盘点日(1周一-7周日, NULL=不参与周盘) — 已废弃，由订货周期表 store_order_cycle 替代 */
    private Integer weeklyInventoryDay;
    /** 周盘暂停: 0参与 1暂停(暂停后自动生成跳过该店) */
    private Integer weeklyPaused;
    /** 订货日(1-7逗号分隔,1=周一)，NULL=未配置订货周期（不参与周盘）— 来自 store_order_cycle */
    private String orderDays;
}
