package com.xzcpc.job;

import com.xzcpc.task.service.DifferenceCalcService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 每月1号10:00自动计算差异（只算10:00前提交的任务）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiffCalcJob {

    private final DifferenceCalcService diffCalcService;
    private final JdbcTemplate jdbcTemplate;

    @Scheduled(cron = "0 0 10 1 * *")
    public void execute() {
        log.info("========== 月度差异计算开始 ==========");
        LocalDateTime cutoff = LocalDateTime.now();
        List<Integer> taskIds = jdbcTemplate.queryForList(
                "SELECT id FROM task WHERE status = 'submitted' AND submitted_at < ? AND del_flag = 0 AND task_type = 'monthly' " +
                "AND NOT EXISTS (SELECT 1 FROM inventory_difference d WHERE d.task_id = task.id AND d.del_flag = 0) " +
                "ORDER BY id", Integer.class, cutoff);

        log.info("共 {} 个任务需要计算", taskIds.size());
        int done = 0;
        for (Integer taskId : taskIds) {
            try {
                diffCalcService.calculateAndSaveDifferences(taskId);
                done++;
                if (done % 10 == 0) log.info("进度: {}/{}", done, taskIds.size());
            } catch (Exception e) {
                log.error("任务 {} 计算失败: {}", taskId, e.getMessage());
            }
        }
        log.info("========== 月度差异计算完成: {}/{} ==========", done, taskIds.size());
    }
}
