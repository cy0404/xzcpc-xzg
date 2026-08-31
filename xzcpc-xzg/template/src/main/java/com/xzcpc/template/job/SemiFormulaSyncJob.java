package com.xzcpc.template.job;

import com.xzcpc.template.service.SemiFormulaSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每日凌晨 3 点全量同步半成品成本卡（xinfo API，含 BOM 配方）。
 * app.sync.enabled 门控（默认 false），与物料同步同锁体系不同锁名，
 * server / mp-server 双端定时由 SemiFormulaSyncService 内部 GET_LOCK 串行化。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SemiFormulaSyncJob {

    private final SemiFormulaSyncService semiFormulaSyncService;

    @Value("${app.sync.semi-enabled:false}")
    private boolean syncEnabled;

    @Scheduled(cron = "0 0 3 * * *")
    public void run() {
        if (!syncEnabled) {
            return;
        }
        try {
            semiFormulaSyncService.sync();
        } catch (Exception e) {
            log.error("每日半成品成本卡同步失败", e);
        }
    }
}
