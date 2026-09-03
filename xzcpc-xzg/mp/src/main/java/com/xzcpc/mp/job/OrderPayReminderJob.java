package com.xzcpc.mp.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xzcpc.mp.client.QmaiClient;
import com.xzcpc.mp.entity.NotificationLog;
import com.xzcpc.mp.entity.SmartOrder;
import com.xzcpc.mp.mapper.NotificationLogMapper;
import com.xzcpc.mp.mapper.SmartOrderMapper;
import com.xzcpc.mp.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订阅消息 · 场景7：订货去企迈付款提醒（每 30 分钟）。
 *
 * 店长确认下单（status=success 且已生成企迈报货单 qmai_declare_no）后：
 * 确认 1 小时起发第一条，之后每 2 小时重复，直到企迈付款成功或确认当天 24:00 停止（不跨天）。
 * qmPayStatus 本地列不实时更新（列表同步只改内存），此处按 detail() 同模式实时查企迈：
 * payStatus 0=未支付才提醒；1 已付款 / 2 已审核 不再提醒；查询失败跳过该单，下轮再试。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPayReminderJob {

    private static final int LIMIT = 50;

    private final SmartOrderMapper orderMapper;
    private final NotificationLogMapper notificationLogMapper;
    private final NotificationService notificationService;
    private final QmaiClient qmaiClient;

    /** 每 30 分钟（7:00-23:30，深夜不打扰）；窗口由 SQL 条件 + 2h 去重共同约束 */
    @Scheduled(cron = "0 0,30 7-23 * * ?")
    public void remind() {
        List<SmartOrder> candidates = orderMapper.selectList(
                new LambdaQueryWrapper<SmartOrder>()
                        .eq(SmartOrder::getStatus, "success")
                        .isNotNull(SmartOrder::getQmaiDeclareNo)
                        .ne(SmartOrder::getQmaiDeclareNo, "")
                        .isNotNull(SmartOrder::getConfirmedBy)
                        .ne(SmartOrder::getConfirmedBy, "")
                        .isNotNull(SmartOrder::getConfirmedAt)
                        // 提醒窗口：确认当天且已过确认后 1 小时（确认次日开始不再提醒）
                        .apply("DATE(confirmed_at) = CURDATE()")
                        .apply("confirmed_at <= NOW() - INTERVAL 1 HOUR")
                        .orderByAsc(SmartOrder::getId)
                        .last("LIMIT " + LIMIT));
        for (SmartOrder order : candidates) {
            try {
                process(order);
            } catch (Exception e) {
                log.warn("ORDER_PAY_REMIND 处理异常 orderId={}", order.getId(), e);
            }
        }
    }

    private void process(SmartOrder order) {
        Long orderId = order.getId();
        // 1. 距该单最近一次付款提醒不足 2h → 跳过（首条由"确认后 1 小时"窗口自然触发）
        NotificationLog last = notificationLogMapper.selectOne(
                new LambdaQueryWrapper<NotificationLog>()
                        .eq(NotificationLog::getEventType, "ORDER_PAY_REMIND")
                        .eq(NotificationLog::getSourceId, String.valueOf(orderId))
                        .orderByDesc(NotificationLog::getCreatedAt)
                        .last("LIMIT 1"));
        if (last != null && last.getCreatedAt() != null
                && last.getCreatedAt().isAfter(LocalDateTime.now().minusHours(2))) {
            return;
        }
        // 2. 实时查企迈支付状态（失败抛异常 → 外层跳过，下轮再试，不误发不骚扰）
        QmaiClient.DeclareOrderDetail qm = qmaiClient.getDeclareOrderDetail(order.getQmaiDeclareNo());
        if (qm.getPayStatus() != 0) {
            log.info("ORDER_PAY_REMIND 已付款/已审核，停止提醒 orderId={} payStatus={}", orderId, qm.getPayStatus());
            return;
        }
        // 3. 提醒确认人本人（跳首页：待办卡显示该店待付款订货）
        notificationService.enqueue(order.getConfirmedBy(), order.getStoreId(), "ORDER_PAY_REMIND",
                "【订货】您的订货单还未付款",
                "您已确认的订货单还未在企迈完成付款，请及时处理",
                String.valueOf(orderId), "/pages/home/index/index");
        log.info("ORDER_PAY_REMIND 入队 orderId={} storeId={} openid={}", orderId, order.getStoreId(), order.getConfirmedBy());
    }
}
