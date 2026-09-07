package com.xzcpc.task.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 总部侧任务创建通知入队（订阅消息，场景：盘点任务下发 → 通知门店店长/老板）。
 *
 * task 模块不依赖 mp/people，无法解析门店店长 openid（employee 表在 people），
 * 故只写入"店级广播行"（target_openid=NULL + store_id），由 mp-server 的
 * NotificationSenderJob 消费时先展开（expandStoreBroadcasts）为各店长/老板 openid 逐人发送。
 * 入队与业务同一事务，业务回滚则通知不产生；通知失败不影响任务创建（try/catch 兜底）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskNotifyService {

    private final JdbcTemplate jdbcTemplate;

    public void enqueueTaskCreated(String storeId, String taskName, String deadlineText) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO notification_log
                        (event_type, store_id, target_openid, title, content, page_path, source_id, status, created_at)
                    VALUES (?, ?, NULL, ?, ?, ?, ?, 0, NOW())
                    """,
                    "TASK_CREATED", storeId,
                    "【盘点任务】" + truncate(taskName, 40) + " 已下发",
                    "截止时间 " + (deadlineText == null ? "" : deadlineText),
                    "/pages/task/list/index?storeId=" + storeId, storeId);
        } catch (Exception e) {
            log.warn("TASK_NOTIFY 任务下发通知入队失败 storeId={}", storeId, e);
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
