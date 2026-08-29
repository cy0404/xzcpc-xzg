package com.xzcpc.job;

import com.xzcpc.service.SupervisorSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 督导-门店关系每日同步。
 * 每天凌晨 2 点从新门店接口（xinfo）拉取督导关系，重建 supervisor_store_access(source=auto) 并更新 store_info.supervisor_name。
 * 错开企迈门店同步（每天 1 点），避免接口/数据库高峰。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SupervisorSyncJob {

    private final SupervisorSyncService supervisorSyncService;

    @Scheduled(cron = "0 0 2 * * *")
    public void execute() {
        log.info("开始督导门店关系同步...");
        try {
            Map<String, Object> report = supervisorSyncService.sync(true);
            log.info("督导门店关系同步完成: {}", report);
        } catch (Exception e) {
            log.error("督导门店关系同步失败", e);
        }
    }
}
