package com.xzcpc.job;

import com.xzcpc.common.feishu.FeishuMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

/**
 * 每周一 8:00 门店操作预警通知。
 * 将上周无支出且无报损的门店按督导分组，发给督导个人 + 督导群。
 */
@Slf4j
@Component
public class WeeklyStoreWarningJob {

    private final JdbcTemplate jdbcTemplate;
    private final FeishuMessageService fms;

    public WeeklyStoreWarningJob(JdbcTemplate jdbcTemplate, FeishuMessageService fms) {
        this.jdbcTemplate = jdbcTemplate;
        this.fms = fms;
    }

    // @Scheduled(cron = "0 0 8 ? * MON")  // TODO 等通知再开启
    public void execute() {
        log.info("开始每周门店操作预警...");
        try {
            doSend();
        } catch (Exception e) {
            log.error("每周门店操作预警失败", e);
        }
    }

    public void doSend() {
        String token = fms.getTenantToken();
        if (token == null) {
            log.warn("无法获取飞书 token，跳过预警");
            return;
        }

        // ---- 1. 上周范围 ----
        LocalDate today = LocalDate.now();
        LocalDate lastMonday = today.minusWeeks(1).with(DayOfWeek.MONDAY);
        LocalDate lastSunday = lastMonday.plusDays(6);
        log.info("预警周期：{} ~ {}", lastMonday, lastSunday);

        // ---- 2. 上周有支出的门店 ----
        Set<String> storesWithExpense = new HashSet<>(jdbcTemplate.queryForList(
                "SELECT DISTINCT store_id FROM expense_record " +
                "WHERE occurred_date BETWEEN ? AND ? AND del_flag = 0",
                String.class, lastMonday, lastSunday));

        // ---- 3. 上周有报损的门店 ----
        Set<String> storesWithLoss = new HashSet<>(jdbcTemplate.queryForList(
                "SELECT DISTINCT store_id FROM loss_report " +
                "WHERE occurred_date BETWEEN ? AND ? AND del_flag = 0",
                String.class, lastMonday, lastSunday));

        log.info("上周有支出: {} 家, 有报损: {} 家", storesWithExpense.size(), storesWithLoss.size());

        // ---- 4. 活跃门店 ----
        List<Map<String, Object>> allStores = jdbcTemplate.queryForList(
                "SELECT store_id, store_name, supervisor_name FROM store_info " +
                "WHERE del_flag = 0 AND supervisor_name IS NOT NULL");

        // ---- 5. 预警 = 活跃 - (有支出 ∪ 有报损) ----
        Set<String> warnedIds = new HashSet<>();
        for (Map<String, Object> s : allStores) {
            warnedIds.add((String) s.get("store_id"));
        }
        warnedIds.removeAll(storesWithExpense);
        warnedIds.removeAll(storesWithLoss);

        if (warnedIds.isEmpty()) {
            log.info("无预警门店");
            return;
        }
        log.info("预警门店: {} 家", warnedIds.size());

        // ---- 6. 按督导分组 ----
        Map<String, List<Map<String, Object>>> bySupervisor = new LinkedHashMap<>();
        for (Map<String, Object> s : allStores) {
            if (warnedIds.contains(s.get("store_id"))) {
                String sup = (String) s.get("supervisor_name");
                bySupervisor.computeIfAbsent(sup, k -> new ArrayList<>()).add(s);
            }
        }

        // ---- 7. 督导 → open_id ----
        Map<String, String> supervisorOpenIds = new HashMap<>();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT DISTINCT admin_name, open_id FROM supervisor_store_access WHERE del_flag = 0");
        for (Map<String, Object> row : rows) {
            supervisorOpenIds.put((String) row.get("admin_name"), (String) row.get("open_id"));
        }

        // ---- 8. 发送 ----
        String titleDate = formatTitleDate(lastMonday, lastSunday);

        for (Map.Entry<String, List<Map<String, Object>>> e : bySupervisor.entrySet()) {
            String supName = e.getKey();
            String openId = supervisorOpenIds.get(supName);
            if (openId == null || openId.isEmpty()) {
                log.warn("督导「{}」无 open_id，跳过个人通知", supName);
                continue;
            }
            sendToSupervisor(token, openId, supName, e.getValue(), titleDate);
        }

        sendToGroup(token, bySupervisor, titleDate);
    }

    // ==================== 个人卡片 ====================

    private void sendToSupervisor(String token, String openId, String supName,
                                   List<Map<String, Object>> stores, String titleDate) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("header", fms.cardHeader("orange", "门店操作预警通知--" + titleDate));

        List<Map<String, Object>> els = new ArrayList<>();
        els.add(fms.mdEl("以下门店上周没有任何报损或支出项，请督导查验和监督到位"));
        els.add(fms.tagEl("hr"));

        StringBuilder sb = new StringBuilder();
        sb.append("**").append(supName).append("**（").append(stores.size()).append("家）\n");
        for (int i = 0; i < stores.size(); i++) {
            if (i > 0) sb.append("、");
            sb.append(stores.get(i).get("store_name"));
        }
        els.add(fms.mdEl(sb.toString()));

        card.put("elements", els);
        fms.sendToUser(token, openId, card);
        log.info("预警个人 → {} {}家 → {}", supName, stores.size(), openId);
    }

    // ==================== 群卡片 ====================

    private void sendToGroup(String token, Map<String, List<Map<String, Object>>> bySupervisor,
                              String titleDate) {
        String chatId = fms.getConfig("feishu_supervisor_group_chat_id");
        if (chatId.isEmpty()) {
            log.warn("未配置 feishu_supervisor_group_chat_id，跳过群通知");
            return;
        }

        int totalStores = bySupervisor.values().stream().mapToInt(List::size).sum();

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("header", fms.cardHeader("orange", "门店操作预警通知--" + titleDate));

        List<Map<String, Object>> els = new ArrayList<>();
        els.add(fms.mdEl("以下门店上周没有任何报损或支出项，请对应督导查验和监督到位\n\n"
                + bySupervisor.size() + " 位督导 · " + totalStores + " 家门店"));
        els.add(fms.tagEl("hr"));

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<Map<String, Object>>> e : bySupervisor.entrySet()) {
            sb.append("**").append(e.getKey()).append("**（").append(e.getValue().size()).append("家）\n");
            List<Map<String, Object>> stores = e.getValue();
            for (int i = 0; i < stores.size(); i++) {
                if (i > 0) sb.append("、");
                sb.append(stores.get(i).get("store_name"));
            }
            sb.append("\n\n");
        }
        els.add(fms.mdEl(sb.toString()));

        card.put("elements", els);
        fms.sendToChat(token, chatId, card);
        log.info("预警群通知 → {}位督导 {}家 → {}", bySupervisor.size(), totalStores, chatId);
    }

    // ==================== 工具 ====================

    private String formatTitleDate(LocalDate mon, LocalDate sun) {
        return String.format("%d年%d月%d日-%d月%d日",
                mon.getYear(), mon.getMonthValue(), mon.getDayOfMonth(),
                sun.getMonthValue(), sun.getDayOfMonth());
    }
}
