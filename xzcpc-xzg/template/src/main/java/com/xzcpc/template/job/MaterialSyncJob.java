package com.xzcpc.template.job;

import com.xzcpc.template.service.MaterialSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每日凌晨 3 点全量同步物料主数据（xinfo API）。
 * app.sync.enabled 门控（默认 false），且 server / mp-server 双端定时由
 * MaterialSyncService 内部 GET_LOCK 串行化。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MaterialSyncJob {

    private final MaterialSyncService materialSyncService;

    @Value("${app.sync.enabled:false}")
    private boolean syncEnabled;

    @Scheduled(cron = "0 0 3 * * *")
    public void run() {
        if (!syncEnabled) {
            return;
        }
        try {
            materialSyncService.sync();
        } catch (Exception e) {
            log.error("每日物料同步失败", e);
        }
    }
}
