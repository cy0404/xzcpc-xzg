package com.xzcpc.mp.service.impl;

import com.xzcpc.mp.entity.Issue;
import com.xzcpc.mp.service.XiangmuSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * P0 B1: 象目经理同步档位实现（外部 API 未就绪）。
 *
 * xiangmu.enabled=false（或未配置）时生效：不真正推送，仅记日志，
 * 问题保留在象掌柜、sync_status 置 pending，等待后续接入真实 API 或人工推进。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "xiangmu.enabled", havingValue = "false", matchIfMissing = true)
public class StubXiangmuSyncServiceImpl implements XiangmuSyncService {

    @Override
    public String pushIssue(Issue issue) {
        log.info("[象目经理-Stub] 未接入外部 API，问题 {} 暂不推送，sync_status=pending", issue.getBizCode());
        return null;
    }
}
