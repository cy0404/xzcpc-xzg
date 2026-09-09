package com.xzcpc.mp;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务开关：app.scheduling.enabled=false 时本实例不跑任何 @Scheduled job。
 *
 * 背景：测试环境与生产环境 mp-server 若连同一数据库且同时开启调度，
 * 定时任务（10:00 报损提醒、60s 发送队列）会双实例各执行一次 → 重复入队/重复发送。
 * 约定：生产实例跑调度（默认 true），测试实例启动时加 APP_SCHEDULING_ENABLED=false 关闭。
 */
@Configuration
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
@EnableScheduling
public class SchedulingConfig {
}
