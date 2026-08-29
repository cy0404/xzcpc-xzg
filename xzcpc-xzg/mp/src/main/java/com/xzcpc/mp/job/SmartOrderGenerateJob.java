package com.xzcpc.mp.job;

import com.xzcpc.mp.service.SmartOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * P2B: 智能订货每日生成任务。
 * 每天凌晨 3 点扫描参与周盘的门店：本周周盘任务已提交的门店生成本周建议订货单（幂等：本周已有单据自动跳过），
 * 未提交的跳过（先盘后订，等待周盘提交后的补触发）。
 * 联调/补生成可调用手动触发接口 POST /api/mp/smart-order/generate。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmartOrderGenerateJob {

    private final SmartOrderService smartOrderService;

    @Scheduled(cron = "0 0 3 * * *")
    public void generateWeekly() {
        log.info("SMART_ORDER_GEN 定时任务开始");
        try {
            smartOrderService.generateAll();
        } catch (Exception e) {
            log.error("SMART_ORDER_GEN 定时任务异常", e);
        }
    }
}
