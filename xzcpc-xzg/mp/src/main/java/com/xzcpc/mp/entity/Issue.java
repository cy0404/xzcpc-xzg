package com.xzcpc.mp.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * P0 B1: 问题处理单
 *
 * 状态流转（象掌柜侧 5 态）：
 * pending（已提交）→ processing（处理中）→ pending_acceptance（待验收）→ resolved（已解决）
 * closed（已关闭）为终态：合并/无需处理
 *
 * 派单与处理由象目经理外部系统完成，象掌柜仅同步简化状态 + 处理结果。
 */
@Data
@TableName("issue")
public class Issue {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务编码 */
    private String bizCode;

    /** 提交门店ID */
    private String storeId;

    /** 门店名称 */
    private String storeName;

    /** 问题标题 */
    private String title;

    /** 问题类型：设备问题|物料问题|物流问题|系统问题|经营异常|其他 */
    private String issueType;

    /** 子类型（如设备问题下的制冰机/净水器） */
    private String subType;

    /** 紧急程度：urgent|normal|low */
    private String urgency;

    /** 问题描述 */
    private String description;

    /** 象目经理同步单号 */
    private String xiangmuId;

    /** 象目经理同步状态：pending|synced|failed */
    private String syncStatus;

    /** 状态：pending|processing|pending_acceptance|resolved|closed */
    private String status;

    /** 来源渠道：MINI_PROGRAM(小程序)|FEISHU_GROUP(飞书群H5)|HQ(总部上报)|null(其他旧数据) */
    private String source;

    /** 处理记录 JSON（records + replies + solutionPhotos），task_platform 回调推送 */
    private String recordsData;

    /** 解决原因（门店回复的未解决原因文本） */
    @TableField("acceptance_remark")
    private String replyText;

    /** 处理人 */
    private String processedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    @TableLogic(value = "0", delval = "1")
    private Integer delFlag;
    @Version
    private Integer version;

    @TableField(exist = false)
    private String supervisorName;
}
