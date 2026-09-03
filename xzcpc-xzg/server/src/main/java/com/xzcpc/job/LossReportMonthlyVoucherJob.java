package com.xzcpc.job;

import com.xzcpc.common.feishu.FeishuMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

/** 每月3号 10:00 统计上月已登记的水果蔬菜类报损，发送月度发券飞书卡片给个人 */
@Slf4j
@Component
public class LossReportMonthlyVoucherJob {

    private final JdbcTemplate jdbcTemplate;
    private final FeishuMessageService fms;

    public LossReportMonthlyVoucherJob(JdbcTemplate jdbcTemplate, FeishuMessageService fms) {
        this.jdbcTemplate = jdbcTemplate;
        this.fms = fms;
    }

    @Scheduled(cron = "0 0 10 3 * ?")
    public void sendMonthlyVoucher() {
        log.info("开始月度发券汇总...");
        try { doSend(); }
        catch (Exception e) { log.error("月度发券汇总失败", e); }
    }

    public void doSend() {
        String token = fms.getTenantToken();
        if (token == null) { log.warn("无法获取飞书 token"); return; }

        String userId = fms.getCardUserId("水果蔬菜", "pending");
        if (userId.isEmpty()) { log.warn("未配置水果蔬菜 pending 收件人"); return; }

        LocalDate today = LocalDate.now();
        LocalDate start = today.minusMonths(1).withDayOfMonth(1);
        LocalDate end = today.plusDays(1);

        List<Map<String, Object>> reports = jdbcTemplate.queryForList(
                "SELECT r.id, r.material_name, r.input_qty, r.input_unit, r.reason, r.remark, r.store_name, r.qimai_order_no, r.occurred_date, m.category, m.parent_category " +
                "FROM loss_report r LEFT JOIN material m ON r.material_id = m.material_id " +
                "WHERE r.loss_type='arrival' AND r.status='registered' " +
                "AND r.occurred_date >= ? AND r.occurred_date < ?",
                start, end);

        if (reports.isEmpty()) { log.info("无已登记报损"); return; }

        // 仅保留水果蔬菜组
        Map<String, String> catGroupMap = fms.loadCategoryGroupMap();
        List<Map<String, Object>> fruitList = new ArrayList<>();
        for (Map<String, Object> r : reports) {
            String cat = (String) r.getOrDefault("category", "");
            String gk = cat != null ? catGroupMap.getOrDefault(cat, "其他类") : "其他类";
            if ("水果蔬菜".equals(gk)) fruitList.add(r);
        }
        if (fruitList.isEmpty()) { log.info("无水果蔬菜已登记报损"); return; }

        String rangeParam = "startDate=" + start + "&endDate=" + end;
        int stores = (int) fruitList.stream().map(r -> r.get("store_name")).distinct().count();

        // 构建卡片
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("header", fms.cardHeader("yellow", "月度发券确认 · 水果蔬菜"));
        List<Map<String, Object>> els = new ArrayList<>();
        String info = "**水果蔬菜报损发券清单**\n累计已登记 **" + fruitList.size() + "** 条 · 涉及 **" + stores + "** 个门店\n\n请逐条确认发券。确认后门店会看到补发结果。";
        els.add(fms.mdEl(info));
        els.add(fms.tagEl("hr"));
        try {
            String name = java.net.URLEncoder.encode("水果蔬菜", "UTF-8");
            els.add(fms.mdEl("[查看详情并确认](" + fms.buildApplink("/loss-monthly-voucher.html?" + rangeParam + "&category=" + name) + ")"));
        } catch (Exception ignored) {}
        card.put("elements", els);

        fms.sendToUser(token, userId, card);
        log.info("月度发券卡片 水果蔬菜 {}条 → {}", fruitList.size(), userId);
    }
}
