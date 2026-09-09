package com.xzcpc.mp.job;

import com.xzcpc.mp.service.SmartOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * P2B: 智能订货每日生成任务（拆单版）。
 * 批1（首批，库存=实盘）：每天凌晨 3 点扫描参与周盘的门店——本周周盘任务已提交的门店生成本周第 1 批
 * 建议订货单（幂等：本批已有单据自动跳过），未提交的跳过（先盘后订，等待周盘提交后的补触发）。
 * 批2（次批，库存=估算值）：第二订货日早上 9:00 自动生成并推送店长确认（plan v0.2 决策#5）——
 * 第二订货日 = 首个订货日 + 4 天（如周三订 → 周日订），不另盘点，库存 = 盘点 + 到货 − 预估消耗。
 * 联调/补生成可调用手动触发接口 POST /api/mp/smart-order/generate（批1）/ /generate-second（批2）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmartOrderGenerateJob {

    private final SmartOrderService smartOrderService;

    /** 批1：每天 3:00（保留：提交补触发之外的兜底扫描） */
    @Scheduled(cron = "0 0 3 * * *")
    public void generateWeekly() {
        log.info("SMART_ORDER_GEN 批1定时任务开始");
        try {
            smartOrderService.generateAll();
        } catch (Exception e) {
            log.error("SMART_ORDER_GEN 批1定时任务异常", e);
        }
    }

    /** 批2：第二订货日 9:00 自动生成（幂等：该订货日已有单据自动跳过） */
    @Scheduled(cron = "0 0 9 * * *")
    public void generateSecondBatch() {
        log.info("SMART_ORDER_GEN 批2定时任务开始");
        try {
            smartOrderService.generateSecondBatchAll();
        } catch (Exception e) {
            log.error("SMART_ORDER_GEN 批2定时任务异常", e);
        }
    }
}
