package com.xzcpc.common.feishu.alert;

import lombok.Builder;
import lombok.Data;

/**
 * 异常告警上下文，包含发生异常时的请求信息和运行环境。
 */
@Data
@Builder
public class FeishuAlertContext {

    /** HTTP 方法：GET / POST / PUT / DELETE */
    private String httpMethod;

    /** 请求路径，如 /api/materials */
    private String requestPath;

    /** 查询参数，如 name=测试&page=1 */
    private String queryString;

    /** 客户端来源 IP */
    private String remoteAddr;

    /** 操作人标识（总部端为飞书用户姓名，小程序端为店员名+门店名），可能为空 */
    private String userHint;

    /** 应用名称（从配置注入） */
    private String appName;

    /** 运行环境：dev / prod / local */
    private String environment;
}
