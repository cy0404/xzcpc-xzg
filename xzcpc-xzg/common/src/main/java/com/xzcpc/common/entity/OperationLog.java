package com.xzcpc.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志
 */
@Data
@TableName("operation_log")
public class OperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作人ID（小程序端为 sessionId，总部端为 0） */
    private Long userId;

    /** 操作人标识 */
    private String username;

    /** 模块：物料/模板/任务 */
    private String module;

    /** 操作：新增/编辑/删除/启停/提交 */
    private String operation;

    /** 操作描述 */
    private String description;

    /** 请求IP */
    private String requestIp;

    /** 1成功 0失败 */
    private Integer status;

    /** 失败时的错误信息 */
    private String errorMsg;

    /** 操作时间 */
    private LocalDateTime createdAt;
}
