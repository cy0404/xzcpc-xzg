package com.xzcpc.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 扫码问题反馈记录（顾客/公众扫码提交，顾客凭手机号查询处理进度）。
 *
 * 独立于小程序：H5 页面与接口均走总部端（server :4026 /api/xzg/feedback/**），
 * 仅借用门店数据（store_info）。反馈类型选项存
 * sys_config.feedback_type_options（逗号分隔），可配置无需改代码。
 * 状态流转：pending待处理 → processing处理中 → done已处理 / closed已关闭。
 */
@Data
@TableName("issue_feedback")
public class IssueFeedback {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 反馈类型（门店服务|饮品品质|其他，sys_config 可配） */
    private String feedbackType;

    /** 门店ID（store_info.id，可为空） */
    private String storeId;

    /** 门店名称 */
    private String storeName;

    /** 手机号全号（提交必填，查询进度凭证，门店联系回访用；历史数据为空） */
    private String phone;

    /** 处理状态：pending待处理/processing处理中/done已处理/closed已关闭 */
    private String status;

    /** 处理说明（店长/老板小程序、admin 后台填写；仅内部可见，顾客不可见） */
    private String processNote;

    /** 开始处理时间 */
    private LocalDateTime processingAt;

    /** 处理完成时间 */
    private LocalDateTime processedAt;

    /** 处理凭证URL（图片/视频逗号分隔，店长/老板小程序与 admin 后台可见；顾客不可见） */
    private String evidence;

    /** 处理人openid（小程序店长/老板） */
    private String processedBy;

    /** 处理人姓名 */
    private String processedName;

    /** 反馈内容 */
    private String content;

    /** 图片URL，逗号分隔，最多3张 */
    private String images;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;
    @Version
    private Integer version;
}
