package com.xzcpc.mp.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xzcpc.mp.entity.LossReport;
import com.xzcpc.mp.entity.NotificationLog;
import com.xzcpc.mp.mapper.LossReportMapper;
import com.xzcpc.mp.mapper.NotificationLogMapper;
import com.xzcpc.mp.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 订阅消息 · 场景1：报损待审批每日提醒（每天 10:00 一次）。
 *
 * 店员提交的报损单进入 pending_approval 待门店店长/老板审批（店长/老板本人提交的不走审批流）。
 * 仅当门店有待审批项时，向该店店长/老板合并发一条"N 条待审批"；无待审批门店不发。
 * 首页待办卡实时统计同源兜底：额度不足降级后店长仍能在首页看到待审批数。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LossApprovalReminderJob {

    private final LossReportMapper lossReportMapper;
    private final NotificationLogMapper notificationLogMapper;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 0 10 * * ?")
    public void remind() {
        // 聚合查询用 QueryWrapper（列名/函数），@TableLogic del_flag=0 自动拼接
        List<Map<String, Object>> rows = lossReportMapper.selectMaps(
                new QueryWrapper<LossReport>()
                        .select("store_id", "COUNT(*) AS cnt")
                        .eq("status", "pending_approval")
                        .groupBy("store_id"));
        int sentStores = 0;
        for (Map<String, Object> row : rows) {
            Object storeIdObj = row.get("store_id");
            Object cntObj = row.get("cnt");
            if (storeIdObj == null || cntObj == null) continue;
            long cnt = ((Number) cntObj).longValue();
            if (cnt <= 0) continue;
            String storeId = String.valueOf(storeIdObj);
            // 当天幂等：该店今天已入队过提醒则不重复（防双实例共库同时跑 job 重复入队/重复发送）
            Long exists = notificationLogMapper.selectCount(
                    new LambdaQueryWrapper<NotificationLog>()
                            .eq(NotificationLog::getEventType, "LOSS_APPROVAL_REMIND")
                            .eq(NotificationLog::getStoreId, storeId)
                            .ge(NotificationLog::getCreatedAt, LocalDate.now().atStartOfDay()));
            if (exists != null && exists > 0) continue;
            notificationService.enqueueToStoreManagers(storeId, "LOSS_APPROVAL_REMIND",
                    "【报损审批】今日有 " + cnt + " 条报损待审批",
                    "店员提交的报损单待您审批，请到店长工作台处理",
                    null, "/pages/loss-report/list/index?tab=pending_approval");
            sentStores++;
        }
        log.info("LOSS_APPROVAL_REMIND 完成：{} 家门店有待审批报损", sentStores);
    }
}
