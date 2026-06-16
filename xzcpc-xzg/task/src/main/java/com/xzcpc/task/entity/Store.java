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

    /** 门店编码 */
    private String storeCode;

    /** 小程序号 */
    private String xiaochengxuid;

    /** 仓库 ID（来自外部 API） */
    private String cangkuid;

    /** 门店二维码（本地维护，不随外部同步覆盖） */
    private String qrCode;

    /** 老板手机号（本地维护，用于绑定匹配） */
    private String ownerPhone;

    /** 老板生日（本地维护，用于绑定匹配） */
    private String ownerBirthday;

    /** 老板绑定的微信openid（本地维护） */
    private String ownerOpenid;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;
}
