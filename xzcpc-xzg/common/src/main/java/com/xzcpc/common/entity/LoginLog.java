package com.xzcpc.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录日志
 */
@Data
@TableName("login_log")
public class LoginLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID（session_id） */
    private Long userId;

    /** 用户名/微信昵称 */
    private String username;

    /** 登录类型：login/logout/bind_store */
    private String loginType;

    /** 1成功 0失败 */
    private Integer status;

    /** 失败原因 */
    private String failReason;

    /** 请求IP */
    private String requestIp;

    /** 客户端 UA */
    private String userAgent;

    /** 操作时间 */
    private LocalDateTime createdAt;
}
