package com.xzcpc.task.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 门店信息本地存储表，替代 Spring Cache。
 * 数据来源：外部门店 API 定时同步 + 二维码字段本地维护。
 */
@Data
@TableName("store_info")
public class Store {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 外部 API 门店 ID */
    private String storeId;

    /** 门店名称 */
    private String storeName;

    /** 小象基础门店名称（xinfo接口name，与企迈 store_name 分来源存储） */
    private String xinfoStoreName;

    /** 省（xinfo接口province） */
    private String province;

    /** 地市（xinfo接口city） */
    private String city;

    /** 县/区（xinfo接口district） */
    private String district;

    /** 详细地址（xinfo接口address） */
    private String address;

    /** 门店编码 */
    private String storeCode;

    /** 小程序号 */
    private String xiaochengxuid;

    /** 仓库 ID（来自外部 API） */
    private String cangkuid;

    /** 门店二维码（本地维护，不随外部同步覆盖） */
    private String qrCode;

    /** 老板姓名（本地维护，用于绑定匹配） */
    private String ownerName;

    /** 老板手机号（本地维护，用于绑定匹配） */
    private String ownerPhone;

    /** 老板绑定的微信openid（本地维护） */
    private String ownerOpenid;

    /** 外部问题表单系统门店标识（chat_id），本地维护，用于上报页 URL 映射 */
    private String chatId;

    /** 督导（月度区域划分，本地维护） */
    private String supervisorName;

    /** 企迈门店ID（本地维护，用于调用企迈API查询报货单等） */
    private Long qmaiStoreId;

    /** 企迈控制台仓库ID（本地维护，用于查询入库单） */
    private String warehouseId;

    /** 周盘点日(1周一-7周日, NULL=不参与周盘) */
    private Integer weeklyInventoryDay;

    /** 周盘暂停: 0参与 1暂停(暂停后自动生成跳过该店) */
    private Integer weeklyPaused;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;
}
