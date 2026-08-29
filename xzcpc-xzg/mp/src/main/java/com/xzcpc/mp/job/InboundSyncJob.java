package com.xzcpc.mp.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xzcpc.common.feishu.alert.FeishuAlertContext;
import com.xzcpc.common.feishu.alert.FeishuAlertProperties;
import com.xzcpc.common.feishu.alert.FeishuWebhookAlertService;
import com.xzcpc.mp.service.MpInboundService;
import com.xzcpc.task.entity.Store;
import com.xzcpc.task.mapper.StoreMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 入库单定时同步：每天凌晨 3 点通过企迈 OpenAPI 9.2.2 拉取近 7 天入库单，
 * 逐门店同步（bizNo 匹配归属，仅 1仓配入库/3采购入库）。
 * 小程序端下拉刷新同步（近2天）走 MpInboundController#sync。
 *
 * 失败异常不再静默：fail>0 时汇总一条飞书群告警（最多列出 ALERT_MAX_FAIL_STORES 个门店）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InboundSyncJob {

    /** 告警里最多列出多少个失败门店 */
    private static final int ALERT_MAX_FAIL_STORES = 5;
    /** 告警里单个门店错误信息最大字符数 */
    private static final int ALERT_STORE_MSG_MAX = 100;

    private final StoreMapper storeMapper;
    private final MpInboundService inboundService;
    private final FeishuWebhookAlertService feishuAlert;
    private final FeishuAlertProperties feishuAlertProperties;

    @Value("${spring.profiles.active:unknown}")
    private String activeProfile;

    @Scheduled(cron = "0 0 3 * * ?")  // 生产库建表已完成，恢复定时同步
    public void syncAll() {
        List<Store> stores = storeMapper.selectList(new LambdaQueryWrapper<Store>()
                .isNotNull(Store::getQmaiStoreId));
        int ok = 0, fail = 0;
        Throwable firstError = null;
        List<String> failStores = new ArrayList<>();
        for (Store store : stores) {
            try {
                int newCount = inboundService.syncFromQmai(store.getStoreId(), store.getQmaiStoreId(), 7);
                ok++;
                if (newCount > 0) {
                    log.info("INBOUND_SYNC_JOB store={} new={}", store.getStoreId(), newCount);
                }
            } catch (Exception e) {
                fail++;
                if (firstError == null) {
                    firstError = e;
                }
                if (failStores.size() < ALERT_MAX_FAIL_STORES) {
                    String msg = e.getMessage();
                    if (msg != null && msg.length() > ALERT_STORE_MSG_MAX) {
                        msg = msg.substring(0, ALERT_STORE_MSG_MAX) + "...";
                    }
                    failStores.add(store.getStoreId() + "(" + msg + ")");
                }
                log.error("INBOUND_SYNC_JOB store={} 同步失败: {}", store.getStoreId(), e.getMessage(), e);
            }
        }
        log.info("INBOUND_SYNC_JOB 完成 ok={} fail={}", ok, fail);
        if (fail > 0) {
            alertFailure(ok, fail, failStores, firstError);
        }
    }

    /** 失败汇总 → 飞书群告警（异步，不阻塞任务收尾） */
    private void alertFailure(int ok, int fail, List<String> failStores, Throwable firstError) {
        try {
            String summary = failStores.stream().collect(Collectors.joining("; "));
            if (fail > failStores.size()) {
                summary += " 等共 " + fail + " 家门店失败";
            }
            FeishuAlertContext ctx = FeishuAlertContext.builder()
                    .httpMethod("JOB")
                    .requestPath("/job/inbound-sync")
                    .userHint("ok=" + ok + " fail=" + fail + " 失败门店: " + summary)
                    .appName(feishuAlertProperties.getAppName())
                    .environment(activeProfile)
                    .build();
            feishuAlert.notify(ctx, firstError);
        } catch (Exception e) {
            log.warn("INBOUND_SYNC_JOB 告警发送异常: {}", e.getMessage());
        }
    }
}
