package com.xzcpc.mp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 老板微信绑定申请记录。
 * 提交时自动匹配 store_info.owner_phone + owner_birthday，
 * 匹配成功自动绑定（auto_bound=1），否则进入总部人工审核。
 */
@Data
@TableName("owner_bind_application")
public class OwnerBindApplication {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** 一次性绑定码（二维码携带，UUID 前 16 位） */
    private String bindCode;

    /** 微信 openid（提交时通过 wx.login 获取后回填） */
    private String wechatOpenid;

    /** 老板填写的姓名 */
    private String name;

    /** 老板填写的手机号 */
    private String phone;

    /** 老板填写的生日 */
    private LocalDate birthday;

    /** 自动匹配到的门店 ID 列表（逗号分隔），为空表示未匹配到 */
    private String matchStoreIds;

    /** 状态：pending|auto_bound|approved|rejected */
    private String bindStatus;

    /** 0=人工审核  1=自动绑定 */
    private Integer autoBound;

    /** 总部审核人 ID */
    private Long approvedBy;

    /** 审核时间 */
    private LocalDateTime approvedAt;

    /** 拒绝原因 */
    private String rejectReason;

    /** 二维码过期时间 */
    private LocalDateTime expireAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
