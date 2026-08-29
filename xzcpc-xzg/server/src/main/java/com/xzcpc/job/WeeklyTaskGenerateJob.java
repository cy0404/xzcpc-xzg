package com.xzcpc.job;

import com.xzcpc.task.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 周盘任务自动生成：每天凌晨 2 点，为盘点日在今天/明天的门店生成本周周盘任务。
 * 幂等（同店同周未提交任务已存在则跳过）、暂停门店跳过、无启用的周盘模板则全部跳过。
 * 联调/补生成可调用手动触发接口 POST /api/tasks/weekly-generate。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeeklyTaskGenerateJob {

    private final TaskService taskService;

    @Scheduled(cron = "0 0 2 * * *")
    public void execute() {
        log.info("WEEKLY_TASK_GEN 定时任务开始");
        try {
            var result = taskService.autoGenerateWeekly();
            log.info("WEEKLY_TASK_GEN done: {}", result);
        } catch (Exception e) {
            log.error("WEEKLY_TASK_GEN 定时任务异常", e);
        }
    }
}
