package com.xzcpc.job;

import com.xzcpc.common.feishu.FeishuMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

/** 每日 9:30 汇总到货验收报损，按卡片类型发个人，统计卡发群 */
@Slf4j
@Component
public class LossReportDailySummaryJob {

    private final JdbcTemplate jdbcTemplate;
    private final FeishuMessageService fms;

    public LossReportDailySummaryJob(JdbcTemplate jdbcTemplate, FeishuMessageService fms) {
        this.jdbcTemplate = jdbcTemplate;
        this.fms = fms;
    }

    @Scheduled(cron = "0 30 9 * * ?")
    public void sendDailySummary() {
        log.info("开始每日到货报损汇总...");
        try { doSend(); }
        catch (Exception e) { log.error("每日汇总失败", e); }
    }

    public void doSend() {
        String token = fms.getTenantToken();
        if (token == null) { log.warn("无法获取飞书 token"); return; }

        String today = LocalDate.now().toString();

        // 卡片E2：蓝蛙拒绝待复核（蓝蛙群拒绝的单，提醒审核人二次审核；无 pending 单时也要发）
        sendCardLanwaRecheck(token, today);

        List<Map<String, Object>> allReports = jdbcTemplate.queryForList(
                "SELECT r.id, r.material_id, r.material_name, r.input_qty, r.input_unit, r.reason, r.remark, " +
                "r.store_name, r.qimai_order_no, r.occurred_date, r.status, r.urgent, " +
                "m.category, m.parent_category " +
                "FROM loss_report r LEFT JOIN material m ON r.material_id = m.material_id " +
                "WHERE r.loss_type='arrival' AND r.status='pending' AND r.del_flag=0 " +
                "");

        if (allReports.isEmpty()) { log.info("无待确认到货报损"); return; }

        Map<String, String> catGroupMap = fms.loadCategoryGroupMap();
        for (Map<String, Object> r : allReports) {
            String cat = (String) r.getOrDefault("category", "");
            r.put("_group", cat != null ? catGroupMap.getOrDefault(cat, "其他类") : "其他类");
        }

        // 卡片 A：水果蔬菜 pending
        sendCardA(token, today, allReports);
        // 卡片 B：其他类按原因拆分（审核2张+补发1张）
        sendCardsB(token, today, allReports);
        // 卡片 E：牛油果泥审核
        sendCardE(token, today);
        // 卡片 G：牛油果泥补发
        sendCardG(token, today);
        // 卡片 F：群统计
        sendCardF(token, today, allReports);
    }

    // ==================== 卡片 A：水果蔬菜 pending ====================
    private void sendCardA(String token, String today, List<Map<String, Object>> all) {
        String userId = fms.getCardUserId("水果蔬菜", "pending");
        if (userId.isEmpty()) { log.info("卡片A 无收件人，跳过"); return; }

        List<Map<String, Object>> list = all.stream()
                .filter(r -> "水果蔬菜".equals(r.get("_group"))).toList();
        if (list.isEmpty()) return;

        int stores = (int) list.stream().map(r -> r.get("store_name")).distinct().count();

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("header", fms.cardHeader("yellow", "到货验收报损 · 审核 · 水果蔬菜"));
        List<Map<String, Object>> els = new ArrayList<>();
        String info = "**待确认报损登记清单**\n累计待确认 **" + list.size() + "** 条 · 涉及 **" + stores + "** 个门店\n\n请逐条确认报损登记。确认后次月统一发券。";
        els.add(fms.mdEl(info));
        els.add(fms.tagEl("hr"));
        try { String name = java.net.URLEncoder.encode("水果蔬菜", "UTF-8");
            els.add(fms.mdEl("[查看详情并确认](" + fms.buildApplink("/loss-daily-confirm.html?date=" + today + "&category=" + name + "&uid=" + userId) + ")"));
        } catch (Exception ignored) {}
        card.put("elements", els);
        fms.sendToCardTargets(token, userId, card);
        log.info("卡片A 水果蔬菜 pending → {}", userId);
    }

    // ==================== 卡片 B：其他类按原因拆分（审核+补发各2张） ====================
    private void sendCardsB(String token, String today, List<Map<String, Object>> all) {
        String[][] configs = {
            {"运输破损-外包装", "damage_audit", "damage_resend"},
            {"其他原因",       "other_audit",  "other_resend"}
        };

        for (int idx = 0; idx < configs.length; idx++) {
            String label = configs[idx][0];
            String auditType = configs[idx][1];
            String resendType = configs[idx][2];
            // 审核卡片（外包装+其他原因各一张）
            String auditUid = fms.getCardUserId("其他类", auditType);
            if (!auditUid.isEmpty()) {
                sendOtherAuditOrResend(token, today, auditUid, label, "audit");
                log.info("卡片B{}-审核 {} → {}", idx+1, label, auditUid);
            }
            // 补发卡片：只有其他原因发（外包装合并到其他原因）
            if (idx == 1) {
                String resendUid = fms.getCardUserId("其他类", "other_resend");
                if (!resendUid.isEmpty()) {
                    sendOtherAuditOrResend(token, today, resendUid, "其他原因", "resend");
                    log.info("卡片B-补发 其他原因 → {}", resendUid);
                }
            }
        }
    }

    private void sendOtherAuditOrResend(String token, String today, final String userId, String label, String mode) {
        boolean isOuterPackage = "运输破损-外包装".equals(label);
        String avocadoId = fms.getAvocadoMaterialId();
        String statusIn = "audit".equals(mode)
                ? "('pending','registered','rejected')"
                : "('registered','confirmed_resend','received','not_received')";
        // 审核按原因分，补发不区分原因
        String reasonFilter = "";
        if ("audit".equals(mode)) {
            reasonFilter = isOuterPackage
                ? "AND r.reason = '运输破损-外包装'"
                : "AND r.reason != '运输破损-外包装'";
        }

        // 审核卡片排除牛油果泥（有独立卡片E），补发卡片也排除（有独立卡片G）
        String avoFilter = "";
        if (!avocadoId.isEmpty()) {
            avoFilter = "AND r.material_id != '" + avocadoId.replace("'", "''") + "' ";
        }

        String sql = "SELECT r.store_name, r.status, m.category FROM loss_report r " +
                "LEFT JOIN material m ON r.material_id = m.material_id " +
                "WHERE r.loss_type='arrival' AND r.status IN " + statusIn + " " + reasonFilter + " " + avoFilter +
                "AND r.del_flag=0";
        // 排除不属于"其他类"的分组（水果蔬菜等）
        Map<String, String> catGroupMap = fms.loadCategoryGroupMap();
        java.util.Set<String> notOtherCats = new java.util.HashSet<>();
        for (Map.Entry<String, String> e : catGroupMap.entrySet()) {
            if (!"其他类".equals(e.getValue())) notOtherCats.add(e.getKey());
        }
        if (!notOtherCats.isEmpty()) {
            String notIn = notOtherCats.stream().map(c -> "'" + c.replace("'", "''") + "'").collect(java.util.stream.Collectors.joining(","));
            sql += " AND (m.category IS NULL OR m.category NOT IN (" + notIn + "))";
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

        long pendingCnt, doneCnt;
        if (rows.isEmpty()) return; // 无数据不发卡片
        if ("audit".equals(mode)) {
            pendingCnt = rows.stream().filter(r -> "pending".equals(r.get("status"))).count();
        } else {
            pendingCnt = rows.stream().filter(r -> "registered".equals(r.get("status"))).count();
        }
        doneCnt = rows.size() - pendingCnt;
        // 待审核/待补发为 0 时不再发卡片（只剩历史已处理记录，不值得打扰）
        if (pendingCnt == 0) { log.info("卡片B {} {} 待处理为 0，跳过", label, mode); return; }
        int stores = (int) rows.stream().map(r -> r.get("store_name")).distinct().count();

        boolean isAudit = "audit".equals(mode);
        String tabMode = isAudit ? "审核" : "补发";
        String pendingLabel = isAudit ? "待审核" : "待补发";
        String doneLabel = isAudit ? "已审核" : "已补发";

        Map<String, Object> card = new LinkedHashMap<>();
        String cardTitle = isAudit ? ("到货验收报损 · 审核 · " + label) : "到货验收报损 · 补发 · 其他类";
        card.put("header", fms.cardHeader(isAudit ? (isOuterPackage ? "yellow" : "blue") : "green", cardTitle));
        List<Map<String, Object>> els = new ArrayList<>();
        String info = "**" + tabMode + "清单 其他类**\n" + pendingLabel + " **" + pendingCnt + "** 条 · "
                + doneLabel + " **" + doneCnt + "** 条 · 涉及门店 **" + stores + "** 个";
        els.add(fms.mdEl(info));
        els.add(fms.tagEl("hr"));
        try {
            String name = java.net.URLEncoder.encode("其他类", "UTF-8");
            String reasonParam = "";
            if ("audit".equals(mode)) {
                reasonParam = isOuterPackage ? "&reason=运输破损-外包装" : "&reason=others";
            }
            String link = fms.buildApplink("/loss-daily-confirm.html?date=" + today + "&category=" + name + "&tab=" + mode + reasonParam + "&uid=" + userId);
            els.add(fms.mdEl("[查看" + tabMode + "详情](" + link + ")"));
        } catch (Exception ignored) {}
        card.put("elements", els);
        fms.sendToCardTargets(token, userId, card);
    }

    // ==================== 卡片 C：审核（按分类，各一张，跳过其他类） ====================
    private void sendCardsC(String token, String today) {
        List<Map<String, Object>> auditAll = jdbcTemplate.queryForList(
                "SELECT r.store_name, r.status, m.category " +
                "FROM loss_report r LEFT JOIN material m ON r.material_id = m.material_id " +
                "WHERE r.loss_type='arrival' AND r.status IN ('pending','registered','rejected') AND r.del_flag=0 " +
                "");

        Map<String, String> catGroupMap = fms.loadCategoryGroupMap();
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> r : auditAll) {
            String cat = (String) r.getOrDefault("category", "");
            String gk = cat != null ? catGroupMap.getOrDefault(cat, "其他类") : "其他类";
            if ("其他类".equals(gk)) continue;
            grouped.computeIfAbsent(gk, k -> new ArrayList<>()).add(r);
        }

        for (Map.Entry<String, List<Map<String, Object>>> e : grouped.entrySet()) {
            String gk = e.getKey();
            if ("其他类".equals(gk) || "水果蔬菜".equals(gk)) continue;
            String uid = fms.getCardUserId(gk, "audit");
            if (uid.isEmpty()) continue;

            long pendingCnt = e.getValue().stream().filter(r -> "pending".equals(r.get("status"))).count();
            long doneCnt = e.getValue().size() - pendingCnt;
            int stores = (int) e.getValue().stream().map(r -> r.get("store_name")).distinct().count();

            Map<String, Object> card = new LinkedHashMap<>();
            card.put("header", fms.cardHeader("blue", "到货验收报损 · " + gk));
            List<Map<String, Object>> els = new ArrayList<>();
            String info = "**审核清单 " + gk + "**\n待审核 **" + pendingCnt + "** 条 · 已审核 **" + doneCnt + "** 条 · 涉及门店 " + stores + " 个";
            els.add(fms.mdEl(info));
            els.add(fms.tagEl("hr"));
            try {
                String name = java.net.URLEncoder.encode(gk, "UTF-8");
                els.add(fms.mdEl("[查看审核详情](" + fms.buildApplink("/loss-daily-confirm.html?date=" + today + "&category=" + name + "&tab=audit") + ")"));
            } catch (Exception ex) {}
            card.put("elements", els);
            fms.sendToCardTargets(token, uid, card);
            log.info("卡片C 审核 {} → {}", gk, uid);
        }
    }

    // ==================== 卡片 D：补发（按分类，各一张，跳过其他类） ====================
    private void sendCardsD(String token, String today) {
        List<Map<String, Object>> statsAll = jdbcTemplate.queryForList(
                "SELECT r.store_name, r.status, m.category " +
                "FROM loss_report r LEFT JOIN material m ON r.material_id = m.material_id " +
                "WHERE r.loss_type='arrival' AND r.status IN ('confirmed_resend','received','not_received') AND r.del_flag=0 " +
                "");

        Map<String, String> catGroupMap = fms.loadCategoryGroupMap();
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> r : statsAll) {
            String cat = (String) r.getOrDefault("category", "");
            String gk = cat != null ? catGroupMap.getOrDefault(cat, "其他类") : "其他类";
            if ("其他类".equals(gk)) continue;
            grouped.computeIfAbsent(gk, k -> new ArrayList<>()).add(r);
        }

        for (Map.Entry<String, List<Map<String, Object>>> e : grouped.entrySet()) {
            String gk = e.getKey();
            if ("其他类".equals(gk)) continue;
            String uid = fms.getCardUserId(gk, "resend");
            if (uid.isEmpty()) continue;

            long pendingCnt = e.getValue().stream().filter(r -> "confirmed_resend".equals(r.get("status"))).count();
            long doneCnt = e.getValue().size() - pendingCnt;
            int stores = (int) e.getValue().stream().map(r -> r.get("store_name")).distinct().count();

            Map<String, Object> card = new LinkedHashMap<>();
            card.put("header", fms.cardHeader("green", "到货验收报损 · " + gk));
            List<Map<String, Object>> els = new ArrayList<>();
            String info = "**补发清单 " + gk + "**\n待补发 **" + pendingCnt + "** 条 · 已补发 **" + doneCnt + "** 条 · 涉及门店 " + stores + " 个";
            els.add(fms.mdEl(info));
            els.add(fms.tagEl("hr"));
            try {
                String name = java.net.URLEncoder.encode(gk, "UTF-8");
                els.add(fms.mdEl("[查看补发详情](" + fms.buildApplink("/loss-daily-confirm.html?date=" + today + "&category=" + name + "&tab=resend") + ")"));
            } catch (Exception ex) {}
            card.put("elements", els);
            fms.sendToCardTargets(token, uid, card);
            log.info("卡片D 补发 {} → {}", gk, uid);
        }
    }

    // ==================== 卡片 E：牛油果泥审核 ====================
    // 按厂家拆分：蓝蛙牛油果（remark 带"厂家：蓝蛙"前缀）→ 配置 avocado_audit_lanwa（值可为 chat_ 群）；
    // 其余（hass 等）→ 配置 avocado_audit（个人 open_id）
    private void sendCardE(String token, String today) {
        String avocadoId = fms.getAvocadoMaterialId();
        if (avocadoId.isEmpty()) { log.info("卡片E 未配置 avocado_material_id，跳过"); return; }
        sendCardERegion(token, today, avocadoId, true, "avocado_audit_lanwa", "蓝蛙");
        sendCardERegion(token, today, avocadoId, false, "avocado_audit", "其他");
    }

    private void sendCardERegion(String token, String today, String avocadoId, boolean lanwa, String cardType, String label) {
        String uid = fms.getCardUserId("其他类", cardType);
        if (uid.isEmpty()) { log.info("卡片E-{} 未配置 {} 收件人，跳过", label, cardType); return; }

        // 蓝蛙入口只收蓝蛙厂家；HASS 入口收 hass 厂家 + 无厂家标记的老单（remark 空/非"厂家："前缀）
        String manufacturerFilter = lanwa
                ? "AND r.remark LIKE '厂家：蓝蛙%' "
                : "AND (r.remark LIKE '厂家：hass牛油果%' OR r.remark IS NULL OR r.remark = '' OR r.remark NOT LIKE '厂家：%') ";
        List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "SELECT r.store_name, r.status FROM loss_report r " +
                "WHERE r.loss_type='arrival' AND r.material_id=? AND r.status IN ('pending','registered','rejected') " +
                manufacturerFilter +
                "AND r.del_flag=0", avocadoId);
        long pendingCnt = list.stream().filter(r -> "pending".equals(r.get("status"))).count();
        // 待审核为 0 时不发卡片
        if (pendingCnt == 0) { log.info("卡片E-{} 无待审核数据，跳过", label); return; }
        long doneCnt = list.size() - pendingCnt;
        int stores = (int) list.stream().map(r -> r.get("store_name")).distinct().count();

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("header", fms.cardHeader("blue", "到货验收报损 · 牛油果泥" + (lanwa ? "（蓝蛙）" : "（HASS）")));
        List<Map<String, Object>> els = new ArrayList<>();
        String info = "**牛油果泥审核清单" + (lanwa ? "（蓝蛙）" : "（HASS）") + "**\n待审核 **" + pendingCnt + "** 条 · 已审核 **" + doneCnt + "** 条 · 涉及 **" + stores + "** 个门店";
        els.add(fms.mdEl(info));
        els.add(fms.tagEl("hr"));
        try {
            String name = java.net.URLEncoder.encode("其他类", "UTF-8");
            // region 参数标注厂家入口（lanwa=蓝蛙群 / hass=个人），页面过滤不依赖 uid（飞书打开可能丢 uid）
            // 蓝蛙=外部公司（租户外无自建应用访问权），web_app applink 会报「应用页面已失效」→ 直链裸 http 打开；
            // hass 个人卡收件人是本租户内部，仍走 applink
            String path = "/loss-daily-confirm.html?date=" + today + "&category=" + name
                    + "&tab=audit&materialId=" + avocadoId + "&uid=" + uid + "&region=" + (lanwa ? "lanwa" : "hass");
            els.add(fms.mdEl("[查看审核详情](" + (lanwa ? fms.buildWebUrl(path) : fms.buildApplink(path)) + ")"));
        } catch (Exception ignored) {}
        card.put("elements", els);
        fms.sendToCardTargets(token, uid, card);
        log.info("卡片E-{} 牛油果泥审核 pending={} done={} → {}", label, pendingCnt, doneCnt, uid);
    }

    // ==================== 卡片 E2：蓝蛙拒绝待复核（审核人二次审核） ====================
    // 蓝蛙群审核拒绝的单，每天 9:30 提醒牛油果泥审核人（个人）复核：
    // 不同意 = 报损成立（rejected→registered 进补发，recheck_pass）；同意 = 维持拒绝（保持 rejected，recheck_reject）。
    // 已复核过（recheck_pass/recheck_reject 日志）的不再提醒；近一个月提交的蓝蛙被拒单。
    private void sendCardLanwaRecheck(String token, String today) {
        String avocadoId = fms.getAvocadoMaterialId();
        if (avocadoId.isEmpty()) { log.info("卡片E2 未配置 avocado_material_id，跳过"); return; }
        String uid = fms.getCardUserId("其他类", "avocado_audit");
        if (uid.isEmpty()) { log.info("卡片E2 未配置 avocado_audit 审核人，跳过"); return; }
        // 只显示二次复核启用（2026-09-01）后新拒绝的单——历史一审拒绝单（8/17 批等）不进复核队列
        // 复核人自己在群里一审拒的单，不再推给自己二核（一审日志 operator=免登采集的 open_id）。
        // 配置支持多复核人（uid 逗号分隔），按每人逐个匹配，避免整串 uid 比单 operator 永远不等
        String selfRejectIds = java.util.Arrays.stream(uid.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .map(s -> "'" + s.replace("'", "''") + "'")
                .collect(java.util.stream.Collectors.joining(","));
        String selfRejectClause = selfRejectIds.isEmpty()
                ? " AND 1=1 "
                : " AND NOT EXISTS (SELECT 1 FROM loss_report_log l3 WHERE l3.report_id=r.id "
                    + "  AND l3.action IN ('reject','audit_reject') AND l3.operator IN (" + selfRejectIds + "))";
        List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "SELECT r.id, r.store_name FROM loss_report r " +
                "WHERE r.loss_type='arrival' AND r.material_id=? AND r.status='rejected' " +
                "AND r.remark LIKE '厂家：蓝蛙%' AND r.del_flag=0 " +
                "AND EXISTS (SELECT 1 FROM loss_report_log l2 WHERE l2.report_id=r.id " +
                "  AND l2.action IN ('reject','audit_reject') AND l2.created_at >= '2026-09-01 00:00:00') " +
                "AND NOT EXISTS (SELECT 1 FROM loss_report_log l WHERE l.report_id=r.id " +
                "  AND l.action IN ('recheck_pass','recheck_reject')) " +
                selfRejectClause, avocadoId);
        if (list.isEmpty()) { log.info("卡片E2 无蓝蛙拒绝待复核数据，跳过"); return; }
        int stores = (int) list.stream().map(r -> r.get("store_name")).distinct().count();
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("header", fms.cardHeader("red", "到货验收报损 · 牛油果泥（蓝蛙）拒绝待复核"));
        List<Map<String, Object>> els = new ArrayList<>();
        String info = "**蓝蛙拒绝待复核**\n待复核 **" + list.size() + "** 条 · 涉及 **" + stores + "** 个门店\n\n不同意 = 报损成立，进入补发；同意 = 报损维持拒绝";
        els.add(fms.mdEl(info));
        els.add(fms.tagEl("hr"));
        try {
            String name = java.net.URLEncoder.encode("其他类", "UTF-8");
            els.add(fms.mdEl("[查看并复核](" + fms.buildApplink("/loss-daily-confirm.html?date=" + today + "&category=" + name + "&tab=audit&materialId=" + avocadoId + "&recheck=1&uid=" + uid) + ")"));
        } catch (Exception ignored) {}
        card.put("elements", els);
        fms.sendToCardTargets(token, uid, card);
        log.info("卡片E2 蓝蛙拒绝待复核 n={} stores={} → {}", list.size(), stores, uid);
    }

    // ==================== 卡片 G：牛油果泥补发 ====================
    private void sendCardG(String token, String today) {
        String avocadoId = fms.getAvocadoMaterialId();
        if (avocadoId.isEmpty()) { log.info("卡片G 未配置 avocado_material_id，跳过"); return; }

        String uid = fms.getCardUserId("其他类", "avocado_resend");
        if (uid.isEmpty()) { log.info("卡片G 未配置 avocado_resend 收件人，跳过"); return; }

        List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "SELECT r.store_name, r.status FROM loss_report r " +
                "WHERE r.loss_type='arrival' AND r.material_id=? AND r.status IN ('registered','confirmed_resend','received','not_received') " +
                "AND r.del_flag=0", avocadoId);
        // 无待补发（全部已补发完毕）则不打扰
        long pendingCnt = list.stream().filter(r -> "registered".equals(r.get("status"))).count();
        if (pendingCnt == 0) { log.info("卡片G 无牛油果泥待补发数据，跳过"); return; }
        long doneCnt = list.size() - pendingCnt;
        int stores = (int) list.stream().map(r -> r.get("store_name")).distinct().count();

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("header", fms.cardHeader("yellow", "到货验收报损 · 补发 · 牛油果泥"));
        List<Map<String, Object>> els = new ArrayList<>();
        String info = "**牛油果泥补发清单**\n待补发 **" + pendingCnt + "** 条 · 已补发 **" + doneCnt + "** 条 · 涉及 **" + stores + "** 个门店";
        els.add(fms.mdEl(info));
        els.add(fms.tagEl("hr"));
        try {
            String name = java.net.URLEncoder.encode("其他类", "UTF-8");
            els.add(fms.mdEl("[查看补发详情](" + fms.buildApplink("/loss-daily-confirm.html?date=" + today + "&category=" + name + "&tab=resend&materialId=" + avocadoId + "&uid=" + uid) + ")"));
        } catch (Exception ignored) {}
        card.put("elements", els);
        fms.sendToCardTargets(token, uid, card);
        log.info("卡片G 牛油果泥补发 pending={} done={} → {}", pendingCnt, doneCnt, uid);
    }

    // ==================== 卡片 F：群统计（唯一发到群的消息） ====================
    private void sendCardF(String token, String today, List<Map<String, Object>> all) {
        String chatId = fms.getLossChatId();
        if (chatId.isEmpty()) { log.info("卡片F 未配置群 chat_id，跳过"); return; }

        long totalPending = all.size();

        // 查询所有待审核+待补发，带分类信息
        List<Map<String, Object>> statsAll = jdbcTemplate.queryForList(
                "SELECT r.store_name, r.material_name, r.input_qty, r.input_unit, r.occurred_date, r.status, m.category, m.parent_category " +
                "FROM loss_report r LEFT JOIN material m ON r.material_id = m.material_id " +
                "WHERE r.loss_type='arrival' AND r.status IN ('pending','registered') " +
                "AND r.del_flag=0");
        Map<String, String> catGroupMap = fms.loadCategoryGroupMap();

        // 过滤：水果蔬菜类的 registered 只统计上月
        LocalDate lastMonth = LocalDate.now().minusMonths(1);
        List<Map<String, Object>> filtered = new ArrayList<>();
        long pendingResend = 0;
        for (Map<String, Object> r : statsAll) {
            String status = (String) r.get("status");
            String cat = (String) r.getOrDefault("category", "");
            String gk = cat != null ? catGroupMap.getOrDefault(cat, "其他类") : "其他类";

            if ("registered".equals(status) && "水果蔬菜".equals(gk)) {
                // 水果蔬菜类只统计上个月的
                Object od = r.get("occurred_date");
                if (od != null) {
                    try {
                        String ds = od.toString().substring(0, 10);
                        LocalDate d = LocalDate.parse(ds);
                        if (d.getYear() == lastMonth.getYear() && d.getMonthValue() == lastMonth.getMonthValue()) {
                            filtered.add(r);
                            pendingResend++;
                        }
                    } catch (Exception e) { /* skip */ }
                }
            } else {
                filtered.add(r);
                if ("registered".equals(status)) pendingResend++;
            }
        }

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("header", fms.cardHeader("blue", "到货验收报损日报 " + today.substring(5)));

        List<Map<String, Object>> els = new ArrayList<>();
        els.add(fms.mdEl("待审核：**" + totalPending + "**　｜　待补发：**" + pendingResend + "**（水果蔬菜仅统计上月）"));

        if (!filtered.isEmpty()) {
            List<Map<String, Object>> columns = List.of(
                    colDef("store", "门店", "auto"),
                    colDef("material", "物料", "auto"),
                    colDef("category", "类型", "80px"),
                    colDef("qty", "数量", "80px"),
                    colDef("date", "报损日期", "100px"),
                    colDef("status", "当前进度", "80px")
            );
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Map<String, Object> r : filtered) {
                String st = "pending".equals(r.get("status")) ? "待审核" : "registered".equals(r.get("status")) ? "待补发" : "-";
                String qtyStr;
                try {
                    java.math.BigDecimal bd = new java.math.BigDecimal(String.valueOf(r.getOrDefault("input_qty", "0")));
                    qtyStr = bd.setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
                } catch (Exception e) {
                    qtyStr = String.valueOf(r.getOrDefault("input_qty", ""));
                }
                qtyStr += String.valueOf(r.getOrDefault("input_unit", ""));
                String catDisplay = String.valueOf(r.getOrDefault("category", ""));
                rows.add(Map.of(
                        "store", String.valueOf(r.getOrDefault("store_name", "-")),
                        "material", String.valueOf(r.getOrDefault("material_name", "-")),
                        "category", catDisplay,
                        "qty", qtyStr,
                        "date", String.valueOf(r.getOrDefault("occurred_date", "-")),
                        "status", st
                ));
            }
            Map<String, Object> table = new LinkedHashMap<>();
            table.put("tag", "table");
            table.put("columns", columns);
            table.put("rows", rows);
            table.put("row_height", "low");
            table.put("page_size", 10);
            els.add(table);
        } else {
            els.add(fms.mdEl("暂无待完成补发明细。"));
        }

        card.put("elements", els);
        fms.sendToChat(token, chatId, card);
        log.info("卡片F 群统计 pending={} pendingResend={} → {}", totalPending, pendingResend, chatId);
    }

    private Map<String, Object> colDef(String name, String display, String width) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("name", name);
        c.put("display_name", display);
        c.put("width", width);
        return c;
    }
}
