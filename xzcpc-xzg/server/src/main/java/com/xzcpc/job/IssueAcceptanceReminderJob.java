package com.xzcpc.job;

import com.xzcpc.common.feishu.FeishuMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 每日 9:00 未验收问题提醒。
 * 统计全部 pending_acceptance（待验收）状态的问题，按门店对应的飞书群（store_info.chat_id）分组，
 * 每个有未验收问题的门店群发一张黄色预警卡片，提醒门店在「象子掌柜」小程序中验收。
 */
@Slf4j
@Component
public class IssueAcceptanceReminderJob {

    private final JdbcTemplate jdbcTemplate;
    private final FeishuMessageService fms;

    public IssueAcceptanceReminderJob(JdbcTemplate jdbcTemplate, FeishuMessageService fms) {
        this.jdbcTemplate = jdbcTemplate;
        this.fms = fms;
    }

    /** 每天 9:00 执行 */
    @Scheduled(cron = "0 0 9 * * ?")
    public void sendReminder() {
        log.info("开始未验收问题提醒...");
        try {
            doSend();
        } catch (Exception e) {
            log.error("未验收问题提醒失败", e);
        }
    }

    public void doSend() {
        String token = fms.getTenantToken();
        if (token == null) {
            log.warn("无法获取飞书 token，跳过未验收问题提醒");
            return;
        }

        // 查全部待验收问题 + 门店群映射（store_info.chat_id = 门店飞书群）
        List<Map<String, Object>> issues = jdbcTemplate.queryForList(
                "SELECT i.id, i.store_id, i.store_name, i.title, i.issue_type, i.created_at, s.chat_id " +
                "FROM issue i LEFT JOIN store_info s ON i.store_id = s.store_id AND s.del_flag = 0 " +
                "WHERE i.status = 'pending_acceptance' AND i.del_flag = 0");

        if (issues.isEmpty()) {
            log.info("无待验收问题，跳过");
            return;
        }
        log.info("待验收问题共 {} 条", issues.size());

        // 本地测试模式：不查门店群，固定发配置的测试群（防止误发生产门店群）
        String testChatId = fms.getIssueReminderTestChatId();
        if (testChatId != null && !testChatId.isBlank()) {
            log.info("测试模式：固定发送到测试群 {}", testChatId);
            sendToStoreGroup(token, testChatId, issues, true);
            log.info("未验收问题提醒完成（测试模式）：共 {} 条问题，发送测试群 {}", issues.size(), testChatId);
            return;
        }

        // 按门店群 chat_id 分组
        Map<String, List<Map<String, Object>>> byChat = new LinkedHashMap<>();
        Set<String> noChatStores = new TreeSet<>();
        for (Map<String, Object> issue : issues) {
            String chatId = (String) issue.get("chat_id");
            if (chatId == null || chatId.isBlank()) {
                noChatStores.add(String.valueOf(issue.getOrDefault("store_name", issue.getOrDefault("store_id", "未知门店"))));
                continue;
            }
            byChat.computeIfAbsent(chatId, k -> new ArrayList<>()).add(issue);
        }
        if (!noChatStores.isEmpty()) {
            log.warn("以下门店未配置飞书群 chat_id，跳过提醒：{}", noChatStores);
        }

        int sent = 0;
        for (Map.Entry<String, List<Map<String, Object>>> e : byChat.entrySet()) {
            sendToStoreGroup(token, e.getKey(), e.getValue(), false);
            sent++;
        }
        log.info("未验收问题提醒完成：共 {} 条问题，发送 {} 个门店群", issues.size(), sent);
    }

    /**
     * 单群触发（手动测试用）：只发指定门店群（store_info.chat_id = chatId）的未验收问题卡片。
     * 与门店模式一致：按钮带 ?chatId= 过滤，H5 只展示该群对应门店的问题。
     */
    public void doSendToGroup(String chatId) {
        String token = fms.getTenantToken();
        if (token == null) {
            log.warn("无法获取飞书 token，跳过单群提醒 {}", chatId);
            return;
        }
        List<Map<String, Object>> issues = jdbcTemplate.queryForList(
                "SELECT i.id, i.store_id, i.store_name, i.title, i.issue_type, i.created_at " +
                "FROM issue i JOIN store_info s ON i.store_id = s.store_id AND s.del_flag = 0 " +
                "WHERE i.status = 'pending_acceptance' AND i.del_flag = 0 AND s.chat_id = ? " +
                "ORDER BY i.created_at DESC", chatId);
        if (issues.isEmpty()) {
            log.info("单群 {} 无待验收问题，跳过", chatId);
            return;
        }
        sendToStoreGroup(token, chatId, issues, false);
        log.info("单群提醒完成：群 {} 共 {} 条问题", chatId, issues.size());
    }

    // ==================== 门店群卡片 ====================

    private void sendToStoreGroup(String token, String chatId, List<Map<String, Object>> issues, boolean testMode) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("header", fms.cardHeader("yellow", "⚠️ 未验收问题提醒（" + issues.size() + " 条）"));

        List<Map<String, Object>> els = new ArrayList<>();
        String info = "本店有 <font color='red'>**" + issues.size() + "**</font> 条问题已处理完成，请点击查看详情并验收：";
        els.add(fms.mdEl(info));
        els.add(fms.tagEl("hr"));

        // 表格：问题列表（序号/问题/类型/提交时间四列）。
        // 平台限制：列宽 width 任何格式都会被拒（200912），只能自动分配；lark_md 单元格必须是纯字符串（对象格式被拒）
        // 问题列用 lark_md 链接：点击问题标题直接跳 H5 验收页（验收动作全在 H5 完成）
        List<Map<String, Object>> columns = List.of(
                colDef("title", "问题", "lark_md"),
                colDef("type", "类型", "text"),
                colDef("time", "提交时间", "text")
        );
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> issue : issues) {
            long id = ((Number) issue.get("id")).longValue();
            String title = String.valueOf(issue.getOrDefault("title", "-"));
            String url = fms.buildApplink("/h5/issue-accept.html?id=" + id);
            rows.add(Map.of(
                    "title", "[**" + title + "**](" + url + ")",
                    "type", String.valueOf(issue.getOrDefault("issue_type", "-")),
                    "time", formatTime(issue.get("created_at"))
            ));
        }
        Map<String, Object> table = new LinkedHashMap<>();
        table.put("tag", "table");
        table.put("columns", columns);
        table.put("rows", rows);
        table.put("row_height", "low");
        table.put("page_size", 10);
        els.add(table);

        // 按钮不放卡片（已解决/未解决按钮在 H5 验收页内），卡片只展示表格 + 一个入口按钮

        // 底部「查看详情并验收」入口：跳 H5 验收页
        // 门店模式带 ?chatId=本群，H5 只展示本群对应门店的问题；测试模式不带参数，展示全部
        Map<String, Object> allAction = new LinkedHashMap<>();
        allAction.put("tag", "action");
        Map<String, Object> acceptBtn = new LinkedHashMap<>();
        acceptBtn.put("tag", "button");
        acceptBtn.put("text", fms.textObj("👀 查看详情并验收"));
        acceptBtn.put("type", "primary");
        acceptBtn.put("url", fms.buildApplink("/h5/issue-accept.html" + (testMode ? "" : "?chatId=" + chatId)));
        allAction.put("actions", List.of(acceptBtn));
        els.add(allAction);

        card.put("elements", els);
        String messageId = fms.sendToChat(token, chatId, card);
        // 记录发送日志（供查询已读回执用）；表未建或写失败不影响卡片发送
        if (messageId != null && !messageId.isEmpty()) {
            try {
                String storeName = issues.stream()
                        .map(i -> i.get("store_name"))
                        .filter(Objects::nonNull)
                        .map(String::valueOf)
                        .filter(s -> !s.isBlank())
                        .findFirst().orElse("");
                jdbcTemplate.update(
                        "INSERT INTO issue_reminder_send_log (send_date, chat_id, store_name, message_id, issue_count) VALUES (?, ?, ?, ?, ?)",
                        LocalDate.now(), chatId, storeName, messageId, issues.size());
                log.info("未验收问题卡片 → 群 {}（{} 条），message_id={} 已记录", chatId, issues.size(), messageId);
            } catch (Exception e) {
                log.warn("记录未验收提醒发送日志失败 chatId={}", chatId, e);
            }
        } else {
            log.warn("卡片发送失败或未返回 message_id，跳过记录：群 {}", chatId);
        }
    }

    private String formatTime(Object t) {
        if (t == null) return "-";
        try {
            LocalDateTime dt = t instanceof LocalDateTime
                    ? (LocalDateTime) t
                    : LocalDateTime.parse(String.valueOf(t).replace(' ', 'T'));
            return dt.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));
        } catch (Exception e) {
            String s = String.valueOf(t);
            return s.length() >= 16 ? s.substring(5, 16) : (s.length() >= 10 ? s.substring(5, 10) : s);
        }
    }

    /** 表格列定义：dataType 支持 text / lark_md / number 等；注意 width 字段不可用（飞书 200912 拒绝） */
    private Map<String, Object> colDef(String name, String display, String dataType) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("name", name);
        c.put("display_name", display);
        c.put("data_type", dataType);
        return c;
    }

}
