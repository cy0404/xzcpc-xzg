package com.xzcpc.mp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 扫码客诉记录（小程序端店长/老板处理，与总部端同表 issue_feedback）。
 *
 * 提交走 H5（总部端 /api/xzg/feedback/submit），小程序端仅处理：
 * 列表（本店）→ 详情 → 标记已处理（处理说明 + 凭证，仅内部可见）。
 */
@Data
@TableName("issue_feedback")
public class IssueFeedback {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 反馈类型（门店服务|饮品品质|其他，sys_config 可配） */
    private String feedbackType;

    /** 门店ID（store_info.id） */
    private String storeId;

    /** 门店名称 */
    private String storeName;

    /** 手机号全号（顾客提交，门店联系回访用） */
    private String phone;

    /** 处理状态：pending待处理/processing处理中/done已处理/closed已关闭 */
    private String status;

    /** 处理说明（仅内部可见，顾客不可见） */
    private String processNote;

    /** 开始处理时间 */
    private LocalDateTime processingAt;

    /** 处理完成时间 */
    private LocalDateTime processedAt;

    /** 处理凭证URL（图片/视频逗号分隔，仅内部可见） */
    private String evidence;

    /** 处理人openid */
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
