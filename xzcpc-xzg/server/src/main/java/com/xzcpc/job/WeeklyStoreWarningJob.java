package com.xzcpc.job;

import com.xzcpc.common.feishu.FeishuMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

/**
 * 每周一 8:30 门店操作预警通知。
 * 报损、支出分开判定：上周"无支出"或"无报损"任一缺失的门店即上榜。
 * 卡片用文本分行清单（不用表格组件）：每督导一段，
 * 标题行带 🔵无支出/🟠无报损 统计，门店一行一家、行尾标缺失项；
 * 一项都不缺的店不会出现，两项都缺的店只出现一次、标注两项。
 * 群卡一张发完（督导 ≤5 人时不受表格数限制，文本无此限制），个人卡同风格只含自己门店。
 * 群卡/个人卡底部均带「查看支出明细 / 查看报损明细」按钮：
 * 经无 # 的 302 中介接口（weekly-warning/*-list-link）带上周日期直达后台支出/报损列表，督导页内筛店核实。
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

    /** 预警门店（记录缺失维度，用于标注缺项） */
    private record WarnedStore(String id, String name, String supervisor, boolean noExpense, boolean noLoss) {}

    @Scheduled(cron = "0 30 8 ? * MON")  // 每周一 8:30 发送上周门店操作预警
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

        // ---- 4. 门店全集（仅取配置了督导的门店） ----
        List<Map<String, Object>> allStores = jdbcTemplate.queryForList(
                "SELECT store_id, store_name, supervisor_name FROM store_info " +
                "WHERE del_flag = 0 AND supervisor_name IS NOT NULL");

        // ---- 5. 上榜 = 缺支出 ∪ 缺报损（两维度独立判定） ----
        List<WarnedStore> warnedStores = new ArrayList<>();
        int noExpenseTotal = 0;
        int noLossTotal = 0;
        for (Map<String, Object> s : allStores) {
            String storeId = (String) s.get("store_id");
            boolean noExpense = !storesWithExpense.contains(storeId);
            boolean noLoss = !storesWithLoss.contains(storeId);
            if (noExpense || noLoss) {
                if (noExpense) noExpenseTotal++;
                if (noLoss) noLossTotal++;
                warnedStores.add(new WarnedStore(storeId, (String) s.get("store_name"),
                        (String) s.get("supervisor_name"), noExpense, noLoss));
            }
        }

        if (warnedStores.isEmpty()) {
            log.info("无预警门店");
            return;
        }
        log.info("上榜门店: {} 家（无支出 {} 家 / 无报损 {} 家）",
                warnedStores.size(), noExpenseTotal, noLossTotal);

        // ---- 6. 按督导分组 ----
        Map<String, List<WarnedStore>> bySupervisor = new LinkedHashMap<>();
        for (WarnedStore s : warnedStores) {
            bySupervisor.computeIfAbsent(s.supervisor(), k -> new ArrayList<>()).add(s);
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
        String urlStart = lastMonday.toString();   // 按钮跳转用 yyyy-MM-dd
        String urlEnd = lastSunday.toString();

        for (Map.Entry<String, List<WarnedStore>> e : bySupervisor.entrySet()) {
            String supName = e.getKey();
            String openId = supervisorOpenIds.get(supName);
            if (openId == null || openId.isEmpty()) {
                log.warn("督导「{}」无 open_id，跳过个人通知", supName);
                continue;
            }
            sendToSupervisor(token, openId, supName, e.getValue(), titleDate, urlStart, urlEnd);
        }

        sendToGroup(token, bySupervisor, titleDate, warnedStores.size(), noExpenseTotal, noLossTotal,
                urlStart, urlEnd);
    }

    // ==================== 个人卡片（只含该督导名下门店） ====================

    private void sendToSupervisor(String token, String openId, String supName,
                                   List<WarnedStore> stores, String titleDate,
                                   String urlStart, String urlEnd) {
        List<Map<String, Object>> body = new ArrayList<>();
        body.add(fms.mdEl("以下门店上周有支出或报损记录缺失，请督导查验和监督到位"));
        body.add(fms.tagEl("hr"));
        body.add(fms.mdEl(supervisorBlock(supName, stores)));
        body.add(fms.tagEl("hr"));
        addViewActions(body, urlStart, urlEnd);

        fms.sendToUser(token, openId, card("门店操作预警通知--" + titleDate, body));
        log.info("预警个人 → {} {}家 → {}", supName, stores.size(), openId);
    }

    // ==================== 群卡片（一张消息发完，文本无表格数限制） ====================

    private void sendToGroup(String token, Map<String, List<WarnedStore>> bySupervisor,
                              String titleDate, int storeTotal, int noExpenseTotal, int noLossTotal,
                              String urlStart, String urlEnd) {
        String chatId = fms.getConfig("feishu_supervisor_group_chat_id");
        if (chatId.isEmpty()) {
            log.warn("未配置 feishu_supervisor_group_chat_id，跳过群通知");
            return;
        }

        List<Map<String, Object>> body = new ArrayList<>();
        body.add(fms.mdEl("以下门店上周有支出或报损记录缺失，请对应督导查验和监督到位\n\n"
                + bySupervisor.size() + " 位督导 · " + storeTotal + " 家门店上榜\n"
                + "🔵 无支出 " + noExpenseTotal + " 家 · 🟠 无报损 " + noLossTotal + " 家"));
        body.add(fms.tagEl("hr"));
        // 每督导一段 md（拆开避免单 md 超长被拒，也无需 \n\n 拼缝）
        for (Map.Entry<String, List<WarnedStore>> e : bySupervisor.entrySet()) {
            body.add(fms.mdEl(supervisorBlock(e.getKey(), e.getValue())));
        }
        body.add(fms.tagEl("hr"));
        addViewActions(body, urlStart, urlEnd);

        fms.sendToChat(token, chatId, card("门店操作预警通知--" + titleDate, body));
        log.info("预警群通知 → {}位督导 {}家（无支出{} 无报损{}） → {}",
                bySupervisor.size(), storeTotal, noExpenseTotal, noLossTotal, chatId);
    }

    // ==================== 卡片文本 ====================

    /**
     * 单个督导段（文本分行清单，门店只出现一次）：
     * **张三**（4家）🔵无支出2家·🟠无报损3家
     * 长沙茶百道解放路店｜无支出、无报损
     * 长沙茶百道五一店｜无支出
     */
    private String supervisorBlock(String supName, List<WarnedStore> stores) {
        int noExpense = 0;
        int noLoss = 0;
        for (WarnedStore s : stores) {
            if (s.noExpense()) noExpense++;
            if (s.noLoss()) noLoss++;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("**").append(supName).append("**（").append(stores.size()).append("家）");
        if (noExpense > 0 || noLoss > 0) {
            sb.append(" 🔵无支出").append(noExpense).append("家 · 🟠无报损").append(noLoss).append("家");
        }
        sb.append('\n');
        for (WarnedStore s : stores) {
            sb.append(name(s)).append("｜").append(missingLabels(s)).append('\n');
        }
        return sb.toString();
    }

    private String name(WarnedStore s) {
        return s.name() != null ? s.name() : "";
    }

    /** 缺项标注：无支出 / 无报损 / 无支出、无报损（两项都缺只标一次） */
    private String missingLabels(WarnedStore s) {
        List<String> labels = new ArrayList<>();
        if (s.noExpense()) labels.add("无支出");
        if (s.noLoss()) labels.add("无报损");
        return String.join("、", labels);
    }

    // ==================== 卡片构建 ====================

    /**
     * schema 1.0 卡片（与 LossReportPeriodSummaryJob 同款：schema 2.0 不支持 action 按钮，
     * 带「查看」跳转按钮的卡必须走 1.0，header/markdown/hr/action 均兼容）。
     */
    private Map<String, Object> card(String title, List<Map<String, Object>> els) {
        Map<String, Object> card = new LinkedHashMap<>();
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("wide_screen_mode", true);
        card.put("config", config);
        card.put("header", fms.cardHeader("orange", title));
        card.put("elements", els);
        return card;
    }

    /**
     * 卡片底部操作按钮：「查看支出明细」→ 支出列表（#/expense）、「查看报损明细」→ 报损列表（#/loss），
     * 均带上周日期参数。经无 # 的 302 中介接口跳转（applink 不能带 # 直达 hash 路由）。
     */
    private void addViewActions(List<Map<String, Object>> body, String urlStart, String urlEnd) {
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("tag", "action");

        Map<String, Object> expBtn = new LinkedHashMap<>();
        expBtn.put("tag", "button");
        expBtn.put("text", fms.textObj("查看支出明细"));
        expBtn.put("type", "primary");
        expBtn.put("url", fms.buildApplink("/api/public/weekly-warning/expense-list-link?startDate="
                + urlStart + "&endDate=" + urlEnd));

        Map<String, Object> lossBtn = new LinkedHashMap<>();
        lossBtn.put("tag", "button");
        lossBtn.put("text", fms.textObj("查看报损明细"));
        lossBtn.put("type", "default");
        lossBtn.put("url", fms.buildApplink("/api/public/weekly-warning/loss-list-link?startDate="
                + urlStart + "&endDate=" + urlEnd));

        action.put("actions", List.of(expBtn, lossBtn));
        body.add(action);
    }

    private String formatTitleDate(LocalDate mon, LocalDate sun) {
        return String.format("%d年%d月%d日-%d月%d日",
                mon.getYear(), mon.getMonthValue(), mon.getDayOfMonth(),
                sun.getMonthValue(), sun.getDayOfMonth());
    }
}
