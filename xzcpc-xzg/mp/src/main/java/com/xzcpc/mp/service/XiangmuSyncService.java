package com.xzcpc.mp.service;

import com.xzcpc.mp.entity.Issue;

/**
 * P0 B1: 象目经理外部系统同步适配层。
 *
 * 外部 API 对接资料未就绪时使用 {@code StubXiangmuSyncServiceImpl}（配置 xiangmu.enabled=false，默认）。
 * 拿到真实 baseURL / 鉴权 / 报文格式后，新增 {@code HttpXiangmuSyncServiceImpl}
 * （@ConditionalOnProperty havingValue="true"）替换即可，业务代码不变。
 */
public interface XiangmuSyncService {

    /**
     * 推送新问题到象目经理系统派单。
     *
     * @param issue 已落库的问题
     * @return 象目经理单号；未就绪/失败时返回 null，由调用方据此置 sync_status
     */
    String pushIssue(Issue issue);
}
