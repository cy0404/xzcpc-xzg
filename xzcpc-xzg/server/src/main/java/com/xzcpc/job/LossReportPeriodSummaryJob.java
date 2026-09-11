package com.xzcpc.job;

import com.xzcpc.common.feishu.FeishuMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 每周三 / 每月1号 到货报损汇总卡片（按物料聚合，非逐条明细）。
 * 每周三 9:30：统计上周三 ~ 本周二；每月1号 9:30：统计上月整月。
 * 统计口径：loss_type='arrival'，按 occurred_date 窗口内全部状态，排除蔬菜水果类（该组不走补发体系，次月发券；
 * 分组口径与卡片 F 一致，经 material.category → loadCategoryGroupMap 归组后剔除「水果蔬菜」组）。
 * 表格按物料聚合三列：物料名 | 到货报损次数 | 报损总数量（牛油果泥「件」×24 换算成「包」，口径同卡片 F）。
 * 收件人：loss_notify_card_config 配置（category='其他类'，card_type=period_loss_summary，周/月共用一行），
 * 可配个人 open_id 或 chat_&lt;群id&gt;，改库即生效无需重启；未配置收件人时跳过。
 */
@Slf4j
@Component
public class LossReportPeriodSummaryJob {

    /** 卡片表格行数上限（飞书消息 payload ~30KB 安全线，超限整条发送失败且错误不明显；按物料聚合后一般远低于此） */
    private static final int MAX_TABLE_ROWS = 100;

    private static final String CATEGORY = "其他类";
    /** 收件人配置 card_type：周/月共用一行（loss_notify_card_config 按 category+card_type 精确匹配） */
    private static final String CARD_TYPE = "period_loss_summary";
    private static final DateTimeFormatter MON_DAY = DateTimeFormatter.ofPattern("MM-dd");

    /** 牛油果泥 1 件 = 24 包（与卡片 F / 补发换算口径一致） */
    private static final BigDecimal AVOCADO_PACKS_PER_CASE = BigDecimal.valueOf(24);

    private final JdbcTemplate jdbcTemplate;
    private final FeishuMessageService fms;

    public LossReportPeriodSummaryJob(JdbcTemplate jdbcTemplate, FeishuMessageService fms) {
        this.jdbcTemplate = jdbcTemplate;
        this.fms = fms;
    }

    /** 每周三 9:30：上周三 ~ 本周二 */
    @Scheduled(cron = "0 30 9 ? * WED")
    public void sendWeeklySummary() {
        log.info("开始周度到货报损汇总...");
        try {
            doSendWeek();
        } catch (Exception e) {
            log.error("周度到货报损汇总失败", e);
        }
    }

    /** 每月1号 9:30：上月整月 */
    @Scheduled(cron = "0 30 9 1 * ?")
    public void sendMonthlySummary() {
        log.info("开始月度到货报损汇总...");
        try {
            doSendMonth();
        } catch (Exception e) {
            log.error("月度到货报损汇总失败", e);
        }
    }

    /** 手动触发周度汇总：上周三 ~ 本周二（手动在任意一天跑都是最近一个已完整结束的周窗口） */
    public void doSendWeek() {
        LocalDate weekEnd = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.TUESDAY));
        doSendRange(weekEnd.minusDays(6), weekEnd, false);
    }

    /** 手动触发月度汇总：上一个完整自然月 */
    public void doSendMonth() {
        YearMonth lastMonth = YearMonth.now().minusMonths(1);
        doSendRange(lastMonth.atDay(1), lastMonth.atEndOfMonth(), true);
    }

    private void doSendRange(LocalDate start, LocalDate end, boolean isMonth) {
        String token = fms.getTenantToken();
        if (token == null) {
            log.warn("无法获取飞书 token，跳过周期汇总");
            return;
        }
        String targets = fms.getCardUserId(CATEGORY, CARD_TYPE);
        if (targets.isEmpty()) {
            log.warn("未配置 {}/{} 收件人，跳过周期汇总", CATEGORY, CARD_TYPE);
            return;
        }

        List<Map<String, Object>> raw = jdbcTemplate.queryForList(
                "SELECT r.occurred_date, r.store_name, r.material_id, r.material_name, " +
                "r.input_qty, r.input_unit, r.status, m.category " +
                "FROM loss_report r LEFT JOIN material m ON r.material_id = m.material_id " +
                "WHERE r.loss_type='arrival' AND r.del_flag=0 AND r.occurred_date BETWEEN ? AND ? " +
                "ORDER BY r.occurred_date, r.store_name",
                start, end);

        // 排除蔬菜水果类（该组不走补发体系，次月汇总发券；分组口径与卡片 F 一致）
        Map<String, String> catGroupMap = fms.loadCategoryGroupMap();
        List<Map<String, Object>> kept = new ArrayList<>();
        int excluded = 0;
        for (Map<String, Object> r : raw) {
            String cat = String.valueOf(r.getOrDefault("category", ""));
            String gk = fms.resolveCategoryGroup(catGroupMap, cat);
            if ("水果蔬菜".equals(gk)) {
                excluded++;
                continue;
            }
            kept.add(r);
        }

        // 按物料聚合（表格：物料名 | 到货报损次数 | 报损总数量），次数降序
        List<Map<String, Object>> stats = aggregateByMaterial(kept);
        log.info("{}周期 {} ~ {} 到货报损 {} 条 / {} 种物料（排除蔬菜水果类 {} 条）",
                isMonth ? "月" : "周", start, end, kept.size(), stats.size(), excluded);

        Map<String, Object> card = buildCard(start, end, isMonth, kept.size(), stats);
        fms.sendToCardTargets(token, targets, card);
        log.info("到货报损{}汇总（{} ~ {}）卡片已发送，n={} 种物料 → {}", isMonth ? "月" : "周", start, end, stats.size(), targets);
    }

    /**
     * 按物料聚合到货报损：每种物料的报损次数与报损总数量。
     * 数量单位按物料内统一（测试口径：同物料仅牛油果泥混「包/件」）；
     * 牛油果泥「件」先 ×24 换算成「包」再加总，口径与卡片 F / 补发一致。
     */
    private List<Map<String, Object>> aggregateByMaterial(List<Map<String, Object>> rows) {
        String avocadoId = fms.getAvocadoMaterialId();
        Map<String, Map<String, Object>> agg = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            String mid = String.valueOf(r.getOrDefault("material_id", ""));
            String unit = r.get("input_unit") == null ? "" : String.valueOf(r.get("input_unit"));
            BigDecimal qty;
            try {
                qty = new BigDecimal(String.valueOf(r.getOrDefault("input_qty", "0")));
            } catch (Exception e) {
                qty = BigDecimal.ZERO;
            }
            if (!mid.isEmpty() && mid.equals(avocadoId) && "件".equals(unit)) {
                qty = qty.multiply(AVOCADO_PACKS_PER_CASE);
                unit = "包";
            }
            Map<String, Object> m = agg.get(mid);
            if (m == null) {
                m = new LinkedHashMap<>();
                m.put("name", String.valueOf(r.getOrDefault("material_name", mid)));
                m.put("unit", unit);
                m.put("cnt", 0);
                m.put("qty", BigDecimal.ZERO);
                agg.put(mid, m);
            }
            m.put("cnt", (int) m.get("cnt") + 1);
            m.put("qty", ((BigDecimal) m.get("qty")).add(qty));
        }
        List<Map<String, Object>> stats = new ArrayList<>(agg.values());
        stats.sort(Comparator.comparingInt((Map<String, Object> m) -> (int) m.get("cnt")).reversed()
                .thenComparing(m -> (BigDecimal) m.get("qty"), Comparator.reverseOrder()));
        return stats;
    }

    private Map<String, Object> buildCard(LocalDate start, LocalDate end, boolean isMonth,
                                          int detailCnt, List<Map<String, Object>> stats) {
        Map<String, Object> card = new LinkedHashMap<>();
        String period = isMonth ? YearMonth.from(start).toString() : start.format(MON_DAY) + " ~ " + end.format(MON_DAY);
        card.put("header", fms.cardHeader(isMonth ? "orange" : "blue",
                (isMonth ? "到货报损月汇总（" : "到货报损周汇总（") + period + "）"));

        List<Map<String, Object>> els = new ArrayList<>();
        StringBuilder info = new StringBuilder();
        info.append("**统计周期：").append(start).append(" ~ ").append(end).append("**\n");
        if (stats.isEmpty()) {
            info.append("该周期暂无到货报损记录");
        } else {
            info.append("到货报损共 **").append(detailCnt).append("** 条 · **").append(stats.size())
                    .append("** 种物料（不含蔬菜水果类）");
            if (stats.size() > MAX_TABLE_ROWS) {
                info.append("\n卡片仅展示前 **").append(MAX_TABLE_ROWS)
                        .append("** 种物料，完整明细请点下方「到象子掌柜查看详情」");
            }
        }
        els.add(fms.mdEl(info.toString()));

        if (!stats.isEmpty()) {
            els.add(fms.tagEl("hr"));
            List<Map<String, Object>> show = stats.size() > MAX_TABLE_ROWS
                    ? stats.subList(0, MAX_TABLE_ROWS) : stats;
            List<Map<String, Object>> columns = List.of(
                    colDef("material", "物料名", "auto"),
                    colDef("cnt", "到货报损次数", "100px"),
                    colDef("qty", "报损总数量", "110px")
            );
            List<Map<String, Object>> data = new ArrayList<>();
            for (Map<String, Object> s : show) {
                data.add(Map.of(
                        "material", str(s.get("name")),
                        "cnt", String.valueOf(s.get("cnt")),
                        "qty", qtyStr(s)
                ));
            }
            Map<String, Object> table = new LinkedHashMap<>();
            table.put("tag", "table");
            table.put("columns", columns);
            table.put("rows", data);
            table.put("row_height", "low");
            table.put("page_size", 10);
            els.add(table);
        }

        // 底部「到象子掌柜查看详情」：跳后台报损列表（/loss），带日期参数自动查询。
        // 经 302 接口跳转：applink 直带 hash 路由（/#/loss?…）会报「重定向 URL 有误」，
        // 按钮先指向无 # 的 loss-list-link，服务端 302 到后台 hash 路由。
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("tag", "action");
        Map<String, Object> btn = new LinkedHashMap<>();
        btn.put("tag", "button");
        btn.put("text", fms.textObj("到象子掌柜查看详情"));
        btn.put("type", "primary");
        btn.put("url", fms.buildApplink("/api/public/loss-report/loss-list-link?startDate=" + start
                + "&endDate=" + end + "&lossType=arrival"));
        action.put("actions", List.of(btn));
        els.add(action);

        card.put("elements", els);
        return card;
    }

    private static String str(Object o) {
        return o == null ? "-" : String.valueOf(o);
    }

    /** 聚合数量显示：去尾零 + 单位（与卡片 F 口径一致，如 1911包） */
    private static String qtyStr(Map<String, Object> s) {
        BigDecimal bd = (BigDecimal) s.get("qty");
        String q = bd == null ? "0" : bd.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        String u = (String) s.get("unit");
        return u == null || u.isEmpty() ? q : q + u;
    }

    /** 表格列定义（照卡片 F 格式：name/display_name/width；若飞书报 200912 需去掉 width 键） */
    private Map<String, Object> colDef(String name, String display, String width) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("name", name);
        c.put("display_name", display);
        c.put("width", width);
        return c;
    }
}
