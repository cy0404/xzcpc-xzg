package com.xzcpc.mp.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xzcpc.mp.entity.NotificationLog;
import com.xzcpc.mp.mapper.NotificationLogMapper;
import com.xzcpc.mp.service.NotificationService;
import com.xzcpc.mp.service.SubscribeMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订阅消息发送队列消费者（唯一发微信的地方，全部业务只入队不直发）：
 * 每 60s 扫 notification_log status=0 → 发送前原子扣额度 → 调微信。
 *
 * 状态流转：
 *   - 成功        → status=1
 *   - 额度不足    → status=2 终态（首页待办卡兜底，不占用重试）
 *   - 43101 拒收  → 回补额度 + status=2 终态（用户侧不可恢复）
 *   - 模板未配置  → status=2 终态（等用户申请模板后填配置）
 *   - 其他失败    → 回补额度 + 重试（retryCount ≥ 5 转终态）
 */
@Slf4j
@Component
// 订阅消息 job 只在 mp-server（小程序端）实例加载：总部端 server 会扫描到 com.xzcpc.mp.job
// 并执行同样的 @Scheduled（双实例共库重复发送），用 app.notify.jobs 开关隔离
@ConditionalOnProperty(name = "app.notify.jobs", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class NotificationSenderJob {

    private static final int MAX_RETRY = 5;
    private static final int BATCH = 50;

    private final NotificationLogMapper notificationLogMapper;
    private final NotificationService notificationService;
    private final SubscribeMessageService subscribeMessageService;

    @Scheduled(cron = "0 * * * * ?")
    public void drain() {
        // 先展开店级广播行（总部 server 入队时不知店长 openid，见 NotificationService.expandStoreBroadcasts）
        try {
            notificationService.expandStoreBroadcasts(100);
        } catch (Exception e) {
            log.error("订阅消息店级广播展开失败", e);
        }
        List<NotificationLog> pending = notificationLogMapper.selectList(
                new LambdaQueryWrapper<NotificationLog>()
                        .eq(NotificationLog::getStatus, 0)
                        .orderByAsc(NotificationLog::getId)
                        .last("LIMIT " + BATCH));
        if (pending.isEmpty()) return;
        for (NotificationLog n : pending) {
            try {
                process(n);
            } catch (Exception e) {
                log.error("订阅消息发送处理异常 logId={}", n.getId(), e);
                // 异常也回补额度并计重试，避免死循环
                notificationService.refundQuota(n.getTargetOpenid());
                retry(n, "发送异常: " + e.getMessage());
            }
        }
    }

    private void process(NotificationLog n) {
        String openid = n.getTargetOpenid();
        if (!StringUtils.hasText(openid)) {
            terminalFail(n, "目标 openid 为空");
            return;
        }
        // 1. 先扣额度（原子）；扣不到 → 降级站内（首页待办卡实时统计，天然兜底）
        if (!notificationService.tryConsumeQuota(openid)) {
            terminalFail(n, "额度不足，降级站内（首页待办卡）");
            return;
        }
        // 2. 发微信
        SubscribeMessageService.Outcome oc =
                subscribeMessageService.send(openid, n.getPagePath(), n.getTitle(), n.getContent());
        switch (oc) {
            case SUCCESS -> markSent(n);
            case USER_REJECTED -> {
                notificationService.refundQuota(openid);
                terminalFail(n, "43101 用户拒收/未订阅");
            }
            case NOT_CONFIGURED -> terminalFail(n, "订阅消息模板未配置");
            case FAILED -> {
                notificationService.refundQuota(openid);
                retry(n, "微信接口失败，待重试");
            }
        }
    }

    private void markSent(NotificationLog n) {
        patch(n, 1, null, "发送成功");
        log.info("订阅消息已发送 logId={} openid={} event={} page={}", n.getId(), n.getTargetOpenid(), n.getEventType(), n.getPagePath());
    }

    private void terminalFail(NotificationLog n, String reason) {
        patch(n, 2, reason, null);
        log.warn("订阅消息终态失败 logId={} openid={} event={} reason={}", n.getId(), n.getTargetOpenid(), n.getEventType(), reason);
    }

    /** 可重试失败：retryCount+1，≥ MAX_RETRY 转终态，否则保持 status=0 等下次轮询 */
    private void retry(NotificationLog n, String reason) {
        int next = (n.getRetryCount() == null ? 0 : n.getRetryCount()) + 1;
        if (next >= MAX_RETRY) {
            patch(n, 2, reason + "（已达最大重试次数）", null);
        } else {
            patch(n, 0, reason, null);
        }
    }

    private void patch(NotificationLog n, int status, String reason, String successMarker) {
        notificationLogMapper.update(null, new LambdaUpdateWrapper<NotificationLog>()
                .eq(NotificationLog::getId, n.getId())
                .set(NotificationLog::getStatus, status)
                .set(reason != null, NotificationLog::getFailReason, reason)
                .set(status == 1, NotificationLog::getSentAt, LocalDateTime.now())
                .setSql("retry_count = retry_count + 1"));
    }
}
