package com.xzcpc.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xzcpc.common.feishu.FeishuMessageService;
import com.xzcpc.mp.entity.LossReport;
import com.xzcpc.mp.entity.LossReportLog;
import com.xzcpc.mp.mapper.LossReportLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequiredArgsConstructor
public class LossReportH5Controller {

    private final LossReportLogMapper logMapper;
    private final JdbcTemplate jdbcTemplate;
    private final FeishuMessageService fms;

    @Value("${app.upload.path:./upload}")
    private String uploadPath;

    @GetMapping("/api/public/loss-report/daily-summary")
    public Map<String, Object> dailySummary(@RequestParam(defaultValue = "") String date,
                                            @RequestParam(defaultValue = "") String category,
                                            @RequestParam(defaultValue = "") String tab,
                                            @RequestParam(defaultValue = "") String reason,
                                            @RequestParam(defaultValue = "") String materialId,
                                            @RequestParam(defaultValue = "") String uid,
                                            jakarta.servlet.http.HttpServletRequest request) {
        log.info("H5页面访问 | IP={} | category={} | tab={} | reason={} | uid={} | UA={}",
                request.getRemoteAddr(), category, tab, reason, uid,
                request.getHeader("User-Agent") != null ? request.getHeader("User-Agent").substring(0, Math.min(80, request.getHeader("User-Agent").length())) : "");
        String catCondition = "";
        if (!category.isEmpty()) {
            Map<String, String> allGroups = fms.loadCategoryGroupMap();
            if ("其他类".equals(category)) {
                // 排除所有已配置分组的物料分类
                java.util.Set<String> knownCats = new java.util.HashSet<>(allGroups.keySet());
                if (!knownCats.isEmpty()) {
                    String inList = knownCats.stream().map(c -> "'" + c.replace("'", "''") + "'").collect(java.util.stream.Collectors.joining(","));
                    catCondition = " AND (m.category IS NULL OR m.category NOT IN (" + inList + "))";
                }
            } else {
                // 只保留该分组的物料分类
                java.util.List<String> cats = new ArrayList<>();
                for (Map.Entry<String, String> e : allGroups.entrySet()) {
                    if (category.equals(e.getValue())) cats.add(e.getKey());
                }
                if (!cats.isEmpty()) {
                    String inList = cats.stream().map(c -> "'" + c.replace("'", "''") + "'").collect(java.util.stream.Collectors.joining(","));
                    catCondition = " AND m.category IN (" + inList + ")";
                }
            }
        }
        String statusFilter;
        if ("audit".equals(tab)) {
            statusFilter = "AND r.status IN ('pending','registered','rejected')";
        } else if ("resend".equals(tab)) {
            statusFilter = "AND r.status IN ('registered','confirmed_resend','received','not_received')";
        } else {
            statusFilter = "AND r.status != 'pending_approval'";
        }
        String reasonFilter = "";
        if ("运输破损-外包装".equals(reason)) {
            reasonFilter = " AND r.reason = '运输破损-外包装'";
        } else if ("others".equals(reason)) {
            reasonFilter = " AND r.reason != '运输破损-外包装'";
        }
        String materialFilter = materialId.isEmpty() ? "" : " AND r.material_id = '" + materialId.replace("'", "''") + "'";
        // 审核Tab排除牛油果泥（有独立卡片E），补发Tab不排除
        String avoFilter = "";
        if (materialId.isEmpty() && !"resend".equals(tab)) {
            String avoId = fms.getAvocadoMaterialId();
            if (!avoId.isEmpty()) avoFilter = " AND r.material_id != '" + avoId.replace("'", "''") + "'";
        }
        // 按分类组过滤：只保留当前 category 组的数据
        String groupFilter = "";
        if (!category.isEmpty() && materialId.isEmpty()) {
            Map<String, String> allGroups = fms.loadCategoryGroupMap();
            if ("其他类".equals(category)) {
                java.util.Set<String> knownCats = allGroups.keySet();
                if (!knownCats.isEmpty()) {
                    String inList = knownCats.stream().map(c -> "'" + c.replace("'", "''") + "'").collect(Collectors.joining(","));
                    groupFilter = " AND (m.category IS NULL OR m.category NOT IN (" + inList + "))";
                }
            } else {
                // 指定组：收集属于该组的全部子分类
                java.util.List<String> cats = new ArrayList<>();
                for (Map.Entry<String, String> e : allGroups.entrySet()) {
                    if (category.equals(e.getValue())) cats.add(e.getKey());
                }
                if (!cats.isEmpty()) {
                    String inList = cats.stream().map(c -> "'" + c.replace("'", "''") + "'").collect(Collectors.joining(","));
                    groupFilter = " AND m.category IN (" + inList + ")";
                }
            }
        }
        String baseSql = "SELECT r.id, r.material_id, r.material_name, r.input_qty, r.input_unit, r.reason, r.remark, r.voucher_url, r.store_name, r.qimai_order_no, r.status, r.reject_reason, r.urgent, r.occurred_date, m.category, m.parent_category, m.qm_code, r.handler_name, r.submitted_by, " +
                "(SELECT e.mobile FROM employee e WHERE e.openid = r.submitted_by LIMIT 1) AS mobile " +
                "FROM loss_report r LEFT JOIN material m ON r.material_id = m.material_id " +
                "WHERE r.loss_type='arrival' " + statusFilter + reasonFilter + materialFilter + avoFilter + " " +
                catCondition +
                " ORDER BY r.status = 'pending' DESC, r.occurred_date DESC";

        List<Map<String, Object>> allRows = jdbcTemplate.queryForList(baseSql);
        // 批量预加载分类映射，避免逐条查库
        Map<String, String> groupKeyCache = new HashMap<>();
        Map<String, Object> configMap = new HashMap<>();
        try {
            List<Map<String, Object>> configs = jdbcTemplate.queryForList(
                "SELECT DISTINCT category FROM loss_notify_card_config WHERE status=1 AND category != '其他类'");
            for (Map<String, Object> cfg : configs) {
                String cats = (String) cfg.get("category");
                if (cats != null) {
                    for (String c : cats.split(",")) {
                        String tc = c.trim();
                        if (!tc.isEmpty()) configMap.put(tc, cats);
                    }
                }
            }
        } catch (Exception ignored) {}

        List<Map<String, Object>> pending = new ArrayList<>();
        List<Map<String, Object>> done = new ArrayList<>();
        int photos = 0;
        Set<String> storeSet = new LinkedHashSet<>();

        for (Map<String, Object> r : allRows) {
            Map<String, Object> item = new LinkedHashMap<>();
            long id = ((Number) r.get("id")).longValue();
            item.put("id", id);
            item.put("materialName", r.getOrDefault("material_name", ""));
            item.put("storeName", r.getOrDefault("store_name", ""));
            item.put("reason", r.getOrDefault("reason", ""));
            item.put("remark", r.getOrDefault("remark", ""));
            item.put("qimaiOrderNo", r.getOrDefault("qimai_order_no", ""));
            Object occDate = r.get("occurred_date");
            item.put("occurredDate", occDate != null ? occDate.toString().substring(5) : ""); // MM-dd
            item.put("rejectReason", r.getOrDefault("reject_reason", ""));
            Object qty = r.get("input_qty");
            String unit = (String) r.getOrDefault("input_unit", "");
            String qtyStr = "--";
            String mid = String.valueOf(r.getOrDefault("material_id", ""));
            String displayUnit = fms.convertAvocadoUnit(mid, unit);
            if (qty != null) {
                try {
                    java.math.BigDecimal bd = fms.convertAvocadoQty(mid, new java.math.BigDecimal(qty.toString()), unit);
                    qtyStr = bd.setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + " " + displayUnit;
                } catch (Exception e) { qtyStr = qty.toString() + " " + displayUnit; }
            }
            item.put("qtyStr", qtyStr);
            item.put("displayUnit", displayUnit);
            item.put("qmCode", String.valueOf(r.getOrDefault("qm_code", "")));
            String cat = r.get("category") != null ? String.valueOf(r.get("category")) : "";
            String pCat = r.get("parent_category") != null ? String.valueOf(r.get("parent_category")) : "";
            item.put("matCategory", pCat.isEmpty() ? cat : pCat + " · " + cat);
            String voucher = (String) r.getOrDefault("voucher_url", "");
            List<String> imgs = voucher != null && !voucher.isEmpty() ? List.of(voucher.split(",")) : List.of();
            item.put("images", imgs);
            String status = (String) r.getOrDefault("status", "");
            if ("resend".equals(tab)) {
                if ("registered".equals(status)) item.put("result", "待补发");
                else if ("confirmed_resend".equals(status) || "received".equals(status) || "not_received".equals(status)) item.put("result", "已补发");
                item.put("resendStatus", status); // raw status for H5: received/not_received
            } else {
                if ("registered".equals(status)) item.put("result", "已登记");
                else if ("confirmed_resend".equals(status) || "received".equals(status) || "not_received".equals(status)) item.put("result", "补发");
                else if ("rejected".equals(status)) item.put("result", "拒绝");
            }
            String parentCat = (String) r.getOrDefault("parent_category", "");
            String matCat = (String) r.getOrDefault("category", "");
            String gKey = matCat != null ? (String) configMap.getOrDefault(matCat, "其他类") : "其他类";
            item.put("urgent", r.getOrDefault("urgent", 0));
            item.put("isFruitVeg", "水果蔬菜".equals(gKey));
            item.put("handlerName", r.getOrDefault("handler_name", ""));
            item.put("handlerPhone", r.getOrDefault("mobile", ""));
            // 下载标记
            Integer dlCnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM loss_report_log WHERE report_id=? AND action='download'",
                Integer.class, id);
            item.put("downloaded", dlCnt != null && dlCnt > 0);
            photos += imgs.size();
            storeSet.add((String) r.get("store_name"));
            // 门店反馈（收货日志）
            List<Map<String, Object>> logs = jdbcTemplate.queryForList(
                    "SELECT action, operator, remark, created_at FROM loss_report_log WHERE report_id=? AND action IN ('receive','not_receive') ORDER BY created_at DESC LIMIT 1", id);
            if (!logs.isEmpty()) {
                Map<String, Object> log = logs.get(0);
                String act = (String) log.get("action");
                item.put("feedback", ("not_receive".equals(act) ? "门店未收到货" : "门店已收货") +
                        (log.get("remark") != null && !log.get("remark").toString().isEmpty() ? " — " + log.get("remark") : ""));
            }
            if ("pending".equals(status)) pending.add(item); else done.add(item);
        }
        List<Map<String, Object>> list = new ArrayList<>(pending);
        list.addAll(done);
        // 牛油果泥门店统计（仅 resend tab + 全部状态）
        List<Map<String, Object>> avocadoStats = new ArrayList<>();
        if ("其他类".equals(category) && !tab.isEmpty()) {
            String avoId = fms.getAvocadoMaterialId();
            if (!avoId.isEmpty()) {
                List<Map<String, Object>> rawStats = jdbcTemplate.queryForList(
                    "SELECT store_name, COUNT(*) cnt, SUM(input_qty) total_qty, input_unit " +
                    "FROM loss_report WHERE loss_type='arrival' AND material_id=? AND status='registered' " +
                    "AND del_flag=0 AND created_at >= DATE_SUB(NOW(), INTERVAL 30 DAY) " +
                    "GROUP BY store_name, input_unit", avoId);
                // 牛油果泥 件→包 换算并合并同门店行
                Map<String, Map<String, Object>> merged = new LinkedHashMap<>();
                for (Map<String, Object> row : rawStats) {
                    String store = String.valueOf(row.get("store_name"));
                    String u = String.valueOf(row.getOrDefault("input_unit", ""));
                    BigDecimal qty = new BigDecimal(String.valueOf(row.getOrDefault("total_qty", "0")));
                    long cnt = ((Number) row.get("cnt")).longValue();
                    if ("件".equals(u)) { qty = qty.multiply(new BigDecimal("24")); u = "包"; }
                    if (merged.containsKey(store)) {
                        Map<String, Object> ex = merged.get(store);
                        ex.put("cnt", ((Number) ex.get("cnt")).longValue() + cnt);
                        ex.put("total_qty", ((BigDecimal) ex.get("total_qty")).add(qty));
                    } else {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("store_name", store); m.put("cnt", cnt);
                        m.put("total_qty", qty); m.put("input_unit", u);
                        merged.put(store, m);
                    }
                }
                avocadoStats = new ArrayList<>(merged.values());
            }
        }
        return Map.of("pending", pending.size(), "done", done.size(), "stores", storeSet.size(), "photos", photos, "list", list, "avocadoStats", avocadoStats);
    }

    @PostMapping("/api/public/loss-report/confirm-single")
    public Map<String, Object> confirmSingle(@RequestBody Map<String, String> body) {
        long id = Long.parseLong(body.get("id"));
        String action = body.get("action");
        String attachmentUrl = body.getOrDefault("attachmentUrl", "");
        String remark = body.getOrDefault("remark", "");
        if ("发券".equals(action)) {
            jdbcTemplate.update("UPDATE loss_report SET status='confirmed_resend', confirmed_at=? WHERE id=? AND status='registered'", LocalDateTime.now(), id);
            addLog(id, "issue_voucher", "厂家", remark, attachmentUrl);
        } else if ("确认报损登记".equals(action)) {
            String curStatus = jdbcTemplate.queryForList("SELECT status FROM loss_report WHERE id=?", String.class, id).stream().findFirst().orElse(null);
            if ("registered".equals(curStatus)) return Map.of("code", 200, "msg", "已确认过，无需重复操作");
            jdbcTemplate.update("UPDATE loss_report SET status='registered', confirmed_at=? WHERE id=?", LocalDateTime.now(), id);
            addLog(id, "register", "厂家", remark, attachmentUrl);
            if (!isAvocadoMaterial(id)) sendAuditDoneCard(id);
        } else if ("补发".equals(action)) {
            jdbcTemplate.update("UPDATE loss_report SET status='confirmed_resend', confirmed_at=? WHERE id=?", LocalDateTime.now(), id);
            addLog(id, "confirm", "厂家", null, attachmentUrl);
        } else {
            String reason = body.get("reason");
            jdbcTemplate.update("UPDATE loss_report SET status='rejected', reject_reason=? WHERE id=?", reason != null ? reason : "", id);
            addLog(id, "reject", "厂家", reason != null ? reason : "", attachmentUrl);
        }
        return Map.of("code", 200, "msg", "ok");
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/api/public/loss-report/batch-confirm")
    public Map<String, Object> batchConfirm(@RequestBody Map<String, Object> body) {
        List<Map<String, String>> items = (List<Map<String, String>>) body.get("items");
        for (Map<String, String> item : items) {
            long id = Long.parseLong(item.get("id"));
            String action = item.get("action");
            LossReport r = null;
            try { r = jdbcTemplate.queryForObject("SELECT * FROM loss_report WHERE id=" + id, (rs, rowNum) -> {
                LossReport lr = new LossReport();
                lr.setId(rs.getLong("id"));
                lr.setStatus(rs.getString("status"));
                return lr;
            }); } catch (Exception ignored) {}
            if (r == null) continue;

            if ("发券".equals(action)) {
                jdbcTemplate.update("UPDATE loss_report SET status='confirmed_resend', confirmed_at=? WHERE id=? AND status='registered'", LocalDateTime.now(), id);
                addLog(id, "issue_voucher", "厂家", null);
            } else if ("确认报损登记".equals(action)) {
                String st = null;
                try { st = jdbcTemplate.queryForList("SELECT status FROM loss_report WHERE id=?", String.class, id).stream().findFirst().orElse(null); } catch (Exception ignored) {}
                if ("registered".equals(st)) continue;
                jdbcTemplate.update("UPDATE loss_report SET status='registered', confirmed_at=? WHERE id=?", LocalDateTime.now(), id);
                String attUrl = item.getOrDefault("attachmentUrl", "");
                addLog(id, "register", "厂家", null, attUrl);
                if (!isAvocadoMaterial(id)) sendAuditDoneCard(id);
            } else if ("补发".equals(action)) {
                jdbcTemplate.update("UPDATE loss_report SET status='confirmed_resend', confirmed_at=? WHERE id=?", LocalDateTime.now(), id);
                addLog(id, "confirm", "厂家", null);
            } else if ("拒绝".equals(action)) {
                String reason = item.get("reason");
                String attUrl2 = item.getOrDefault("attachmentUrl", "");
                jdbcTemplate.update("UPDATE loss_report SET status='rejected', reject_reason=? WHERE id=?", reason != null ? reason : "", id);
                addLog(id, "reject", "厂家", reason != null ? reason : "", attUrl2);
            }
        }
        return Map.of("code", 200, "msg", "ok");
    }

    private boolean isAvocadoMaterial(long reportId) {
        String avocadoId = fms.getAvocadoMaterialId();
        if (avocadoId.isEmpty()) return false;
        try {
            String mid = jdbcTemplate.queryForObject("SELECT material_id FROM loss_report WHERE id=?", String.class, reportId);
            return avocadoId.equals(mid);
        } catch (Exception e) { return false; }
    }

    /** 每周一早上统计牛油果泥已登记报损，发给补发人 */
    @GetMapping("/api/public/loss-report/avocado-weekly-summary")
    public void avocadoWeeklySummary() {
        String token = fms.getTenantToken();
        if (token == null) return;
        String avocadoId = fms.getAvocadoMaterialId();
        if (avocadoId.isEmpty()) return;
        String userId = fms.getCardUserId("其他类", "avocado_resend");
        if (userId.isEmpty()) return;

        List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "SELECT r.store_name, r.material_name, r.input_qty, r.input_unit, r.occurred_date " +
                "FROM loss_report r WHERE r.loss_type='arrival' AND r.material_id=? AND r.status='registered' " +
                "AND r.del_flag=0 AND r.created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)", avocadoId);

        int stores = (int) list.stream().map(r -> r.get("store_name")).distinct().count();
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("header", fms.cardHeader("green", "到货验收报损 · 牛油果泥 · 周报"));
        List<Map<String, Object>> els = new ArrayList<>();
        String info = "**牛油果泥周报**\n本周已登记 **" + list.size() + "** 条 · 涉及 **" + stores + "** 个门店\n\n请及时确认补发。";
        els.add(fms.mdEl(info));
        els.add(fms.tagEl("hr"));
        try {
            String name = java.net.URLEncoder.encode("其他类", "UTF-8");
            els.add(fms.mdEl("[查看牛油果泥补发](" + fms.buildApplink("/loss-daily-confirm.html?category=" + name + "&tab=resend&materialId=" + avocadoId) + ")"));
        } catch (Exception ignored) {}
        card.put("elements", els);
        fms.sendToUser(token, userId, card);
        log.info("牛油果泥周报 {}条 → {}", list.size(), userId);
    }

    /** 审核完成（确认报损登记）→ 发卡片通知补发人 */
    private void sendAuditDoneCard(long reportId) {
        try {
            Map<String, Object> r = jdbcTemplate.queryForMap(
                    "SELECT r.store_name, r.material_name, r.input_qty, r.input_unit, r.reason, r.urgent, m.category " +
                    "FROM loss_report r LEFT JOIN material m ON r.material_id = m.material_id WHERE r.id=?", reportId);
            String storeName = String.valueOf(r.getOrDefault("store_name", ""));
            String matName = String.valueOf(r.getOrDefault("material_name", ""));
            String qty = String.valueOf(r.getOrDefault("input_qty", ""));
            String unit = String.valueOf(r.getOrDefault("input_unit", ""));
            String qtyStr;
            try {
                java.math.BigDecimal bd = new java.math.BigDecimal(qty);
                qtyStr = bd.setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + unit;
            } catch (Exception e) {
                qtyStr = qty + unit;
            }
            boolean urgent = Integer.valueOf(1).equals(r.get("urgent"));
            String cat = String.valueOf(r.getOrDefault("category", ""));
            String reason = String.valueOf(r.getOrDefault("reason", ""));

            String groupKey = fms.resolveGroupKey(cat);
            if ("水果蔬菜".equals(groupKey)) return; // 水果蔬菜次月发券，不发即时补发通知
            String cardType = "运输破损-外包装".equals(reason) ? "damage_resend" : "other_resend";
            String userId = fms.getCardUserId("其他类", cardType);
            if (userId.isEmpty()) return;

            String token = fms.getTenantToken();
            if (token == null) return;
            String color = urgent ? "red" : "blue";
            String title = "到货验收报损" + (urgent ? " · 加急" : "") + " · " + storeName;
            String info = storeName + "报损 **" + matName + " " + qtyStr + "**，请查看详情并确认补发。";

            Map<String, Object> card = new LinkedHashMap<>();
            card.put("header", fms.cardHeader(color, title));
            List<Map<String, Object>> els = new ArrayList<>();
            els.add(fms.mdEl(info));
            els.add(fms.tagEl("hr"));
            String link = fms.buildApplink("/loss-daily-confirm.html?tab=resend&category="
                    + fms.urlEncode(groupKey) + "&reason=" + ("其他类".equals(groupKey) ? ("运输破损-外包装".equals(reason) ? "运输破损-外包装" : "others") : ""));
            els.add(fms.mdEl("[查看详情](" + link + ")"));
            card.put("elements", els);

            fms.sendToUser(token, userId, card);
            log.info("审核完成通知补发人 reportId={} → {}", reportId, userId);
        } catch (Exception e) {
            log.warn("发送审核完成通知失败 reportId={}", reportId, e);
        }
    }

    /** 加急报损通知：mp 模块创建报损后调此接口发飞书卡片给个人 */
    @PostMapping("/api/public/loss-report/send-urgent-card")
    public Map<String, Object> sendUrgentCard(@RequestBody Map<String, String> body) {
        long reportId = Long.parseLong(body.get("reportId"));
        LossReport r = null;
        try {
            r = jdbcTemplate.queryForObject("SELECT * FROM loss_report WHERE id=" + reportId + " AND del_flag=0", (rs, rowNum) -> {
                LossReport lr = new LossReport();
                lr.setId(rs.getLong("id"));
                lr.setStoreName(rs.getString("store_name"));
                lr.setMaterialName(rs.getString("material_name"));
                lr.setInputQty(rs.getBigDecimal("input_qty"));
                lr.setInputUnit(rs.getString("input_unit"));
                lr.setReason(rs.getString("reason"));
                lr.setQimaiOrderNo(rs.getString("qimai_order_no"));
                lr.setRemark(rs.getString("remark"));
                lr.setCreatedAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null);
                return lr;
            });
        } catch (Exception ignored) {}
        if (r == null) return Map.of("code", 404, "msg", "报损记录不存在");

        String token = fms.getTenantToken();
        if (token == null) return Map.of("code", 500, "msg", "飞书token获取失败");

        // 水果蔬菜类不发加急卡片
        String reportMatCat = null;
        try {
            reportMatCat = jdbcTemplate.queryForObject("SELECT m.category FROM loss_report r LEFT JOIN material m ON r.material_id=m.material_id WHERE r.id=" + reportId, String.class);
        } catch (Exception ignored) {}
        if (reportMatCat != null && "水果蔬菜".equals(fms.resolveGroupKey(reportMatCat))) {
            return Map.of("code", 200, "msg", "水果蔬菜类无需加急");
        }

        // 牛油果泥走独立卡片
        String avoId = fms.getAvocadoMaterialId();
        String userId;
        String cardTitle;
        if (!avoId.isEmpty() && avoId.equals(
                jdbcTemplate.queryForObject("SELECT material_id FROM loss_report WHERE id=?", String.class, reportId))) {
            userId = fms.getCardUserId("其他类", "avocado_audit");
            cardTitle = "加急到货报损 · 牛油果泥";
        } else {
            String cardType = "运输破损-外包装".equals(r.getReason()) ? "damage_audit" : "other_audit";
            userId = fms.getCardUserId("其他类", cardType);
            cardTitle = "加急到货报损清单";
        }
        if (userId.isEmpty()) return Map.of("code", 500, "msg", "未配置加急卡片收件人");

        // 统计加急报损数（牛油果泥只统计该物料）
        String avoFilter = "";
        if (!avoId.isEmpty()) {
            try {
                String mid = jdbcTemplate.queryForObject("SELECT material_id FROM loss_report WHERE id=?", String.class, reportId);
                if (avoId.equals(mid)) avoFilter = " AND material_id='" + avoId.replace("'", "''") + "'";
            } catch (Exception ignored) {}
        }
        int urgentCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM loss_report WHERE loss_type='arrival' AND status='pending' AND urgent=1 AND del_flag=0" + avoFilter, Integer.class);
        int urgentStores = jdbcTemplate.queryForObject(
            "SELECT COUNT(DISTINCT store_id) FROM loss_report WHERE loss_type='arrival' AND status='pending' AND urgent=1 AND del_flag=0" + avoFilter, Integer.class);

        // 构建加急卡片
        List<Map<String, Object>> els = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        sb.append("**加急到货报损清单**\n");
        sb.append("累计待确认 **").append(urgentCount).append("** 条 · 涉及 **").append(urgentStores).append("** 个门店\n\n");
        sb.append("请逐条确认到货验收报损，确认后门店会看到补发或拒绝结果。");
        els.add(fms.mdEl(sb.toString()));
        els.add(fms.tagEl("hr"));
        String linkUrl;
        if (!avoId.isEmpty() && avoId.equals(
                jdbcTemplate.queryForObject("SELECT material_id FROM loss_report WHERE id=?", String.class, reportId))) {
            linkUrl = "/loss-daily-confirm.html?tab=audit&category=" + fms.urlEncode("其他类") + "&materialId=" + avoId + "&urgent=1";
        } else {
            String reason = "运输破损-外包装".equals(r.getReason()) ? "运输破损-外包装" : "others";
            linkUrl = "/loss-daily-confirm.html?tab=audit&category=" + fms.urlEncode("其他类") + "&reason=" + reason + "&urgent=1";
        }
        els.add(fms.mdEl("[查看详情并确认](" + fms.buildApplink(linkUrl) + ")"));

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("header", fms.cardHeader("red", cardTitle));
        card.put("elements", els);

        fms.sendToUser(token, userId, card);
        return Map.of("code", 200, "msg", "已发送加急卡片给 " + userId);
    }

    /** 上传操作附件，代理到 mp 端 */
    @PostMapping("/api/public/loss-report/upload-attachment")
    public Map<String, Object> uploadAttachment(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        try {
            // 代理上传到 mp-server
            org.springframework.core.io.ByteArrayResource resource =
                    new org.springframework.core.io.ByteArrayResource(file.getBytes()) {
                        @Override
                        public String getFilename() { return file.getOriginalFilename(); }
                    };
            org.springframework.util.LinkedMultiValueMap<String, Object> map =
                    new org.springframework.util.LinkedMultiValueMap<>();
            map.add("file", resource);
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA);
            org.springframework.http.HttpEntity<org.springframework.util.MultiValueMap<String, Object>> entity =
                    new org.springframework.http.HttpEntity<>(map, headers);
            String mpUrl = "http://localhost:30261/storeInventory/api/mp/upload/voucher";
            var resp = restTemplate.postForEntity(mpUrl, entity, Map.class);
            Map<String, Object> body = (Map<String, Object>) resp.getBody();
            if (body != null && 200 == (Integer) body.getOrDefault("code", 0)) {
                Map<String, String> data = (Map<String, String>) body.get("data");
                String url = data.get("url");
                boolean isVideo = file.getContentType() != null && file.getContentType().startsWith("video/");
                return Map.of("code", 200, "url", url, "isVideo", isVideo);
            }
            return Map.of("code", 500, "msg", "上传失败");
        } catch (Exception e) {
            log.error("附件上传失败", e);
            return Map.of("code", 500, "msg", "上传失败");
        }
    }

    /** 下载视频时导出配套 Excel */
    @GetMapping("/api/public/loss-report/download-videos-excel")
    public void downloadVideosExcel(@RequestParam String ids, jakarta.servlet.http.HttpServletResponse response) {
        try {
            String[] idArr = ids.split(",");
            List<Map<String, Object>> rows = new ArrayList<>();
            BigDecimal totalQty = BigDecimal.ZERO; String unit = ""; String matName = "";
            for (String sid : idArr) {
                try {
                    long id = Long.parseLong(sid.trim());
                    Map<String, Object> r = jdbcTemplate.queryForMap(
                        "SELECT store_name, material_id, material_name, occurred_date, input_qty, input_unit FROM loss_report WHERE id=? AND del_flag=0", id);
                    rows.add(r);
                    String mid = String.valueOf(r.getOrDefault("material_id", ""));
                    String inputUnit0 = String.valueOf(r.getOrDefault("input_unit", ""));
                    try { totalQty = totalQty.add(fms.convertAvocadoQty(mid, new BigDecimal(String.valueOf(r.get("input_qty"))), inputUnit0)); } catch (Exception ignored) {}
                    if (unit.isEmpty()) unit = fms.convertAvocadoUnit(mid, inputUnit0);
                    if (matName.isEmpty()) matName = String.valueOf(r.getOrDefault("material_name", "明细").toString().replaceAll("\\(.*\\)", "").trim());
                } catch (Exception ignored) {}
            }
            String today = java.time.LocalDate.now().toString().replace("-", "");
            String qtyStr = totalQty.setScale(0, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
            String fname = matName + "_" + today + "_" + qtyStr + unit + ".xls";

            response.setContentType("application/vnd.ms-excel;charset=UTF-8");
            response.setHeader("X-Filename", java.net.URLEncoder.encode(fname, "UTF-8").replace("+", "%20"));
            java.io.PrintWriter w = response.getWriter();
            w.println("<meta charset='UTF-8'><style>td,th{border:1px solid #999;padding:4px 8px}</style><table border='1' cellspacing='0'><tr><th>门店</th><th>物料</th><th>日期</th><th>数量</th><th>单位</th></tr>");
            for (Map<String, Object> r : rows) {
                String mid = String.valueOf(r.getOrDefault("material_id", ""));
                String inputUnit1 = String.valueOf(r.getOrDefault("input_unit", ""));
                String q; try { BigDecimal bd = fms.convertAvocadoQty(mid, new BigDecimal(String.valueOf(r.get("input_qty"))), inputUnit1); q = bd.setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString(); } catch (Exception e) { q = String.valueOf(r.get("input_qty")); }
                String u = fms.convertAvocadoUnit(mid, inputUnit1);
                w.println("<tr><td>" + r.get("store_name") + "</td><td>" + r.get("material_name") + "</td><td>" + r.get("occurred_date") + "</td><td>" + q + "</td><td>" + u + "</td></tr>");
            }
            w.println("</table>");
        } catch (Exception e) { log.error("导出Excel失败", e); }
    }

    /** 牛油果泥导出 Excel */
    @GetMapping("/api/public/loss-report/export-avocado")
    public void exportAvocado(@RequestParam String materialId, @RequestParam(defaultValue = "0") int downloaded,
                              jakarta.servlet.http.HttpServletResponse response) {
        try {
            String sql = "SELECT r.store_name, r.occurred_date, r.input_qty, r.input_unit, r.material_name " +
                    "FROM loss_report r LEFT JOIN loss_report_log l ON r.id = l.report_id AND l.action = 'download' " +
                    "WHERE r.loss_type='arrival' AND r.material_id=? AND r.status='pending' AND r.del_flag=0 " +
                    (downloaded == 1 ? "AND l.id IS NOT NULL" : "AND l.id IS NULL") +
                    " ORDER BY r.store_name, r.occurred_date";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, materialId);

            BigDecimal totalQty = BigDecimal.ZERO; String matName = "";
            for (Map<String, Object> r : rows) {
                try { totalQty = totalQty.add(fms.convertAvocadoQty(materialId, new BigDecimal(String.valueOf(r.get("input_qty"))), String.valueOf(r.getOrDefault("input_unit", "")))); } catch (Exception ignored) {}
                if (matName.isEmpty()) matName = String.valueOf(r.getOrDefault("material_name", "牛油果泥").toString().replaceAll("\\(.*\\)", "").trim());
            }
            String today = java.time.LocalDate.now().toString().replace("-", "");
            String qtyStr = totalQty.setScale(0, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
            String fname = matName + "-" + qtyStr + "包-" + today + ".xls";

            response.setContentType("application/vnd.ms-excel;charset=UTF-8");
            response.setHeader("X-Filename", java.net.URLEncoder.encode(fname, "UTF-8").replace("+", "%20"));

            response.setContentType("application/vnd.ms-excel;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=" + java.net.URLEncoder.encode(fname, "UTF-8").replace("+", "%20"));
            java.io.PrintWriter w = response.getWriter();
            w.println("<meta charset='UTF-8'><style>td,th{border:1px solid #999;padding:4px 8px}</style><table border='1' cellspacing='0'><tr><th>门店</th><th>日期</th><th>数量</th><th>单位</th></tr>");
            for (Map<String, Object> r : rows) {
                String mid = String.valueOf(r.getOrDefault("material_id", ""));
                String avoUnit = String.valueOf(r.getOrDefault("input_unit", ""));
                String qtyDisp;
                try {
                    BigDecimal bd = fms.convertAvocadoQty(materialId, new BigDecimal(String.valueOf(r.get("input_qty"))), avoUnit);
                    qtyDisp = bd.setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
                } catch (Exception e) { qtyDisp = String.valueOf(r.get("input_qty")); }
                String u = fms.convertAvocadoUnit(materialId, avoUnit);
                w.println("<tr><td>" + r.get("store_name") + "</td><td>" + r.get("occurred_date") + "</td><td>" + qtyDisp + "</td><td>" + u + "</td></tr>");
            }
            w.println("</table>");
        } catch (Exception e) {
            log.error("导出Excel失败", e);
        }
    }

    /** 视频下载：按 report IDs 打包 ZIP，按人跳过已下载的 */
    @GetMapping("/api/public/loss-report/download-videos")
    public void downloadVideos(@RequestParam String ids, @RequestParam(defaultValue = "0") int markDownloaded,
                               @RequestParam(defaultValue = "") String uid,
                               jakarta.servlet.http.HttpServletResponse response) {
        String[] idArr = ids.split(",");
        List<Map<String, Object>> reports = new ArrayList<>();
        for (String sid : idArr) {
            try {
                long id = Long.parseLong(sid.trim());
                Map<String, Object> r = jdbcTemplate.queryForMap(
                        "SELECT id, store_name, voucher_url, occurred_date, input_qty, input_unit, material_id, material_name FROM loss_report WHERE id=? AND del_flag=0", id);
                reports.add(r);
            } catch (Exception ignored) {}
        }
        if (reports.isEmpty()) { response.setStatus(404); return; }

        try {
            // 记录下载日志
            if (markDownloaded == 1) {
                for (Map<String, Object> r : reports) {
                    long rid = ((Number) r.get("id")).longValue();
                    try { addLog(rid, "download", uid.isEmpty() ? "系统" : uid, "批量下载"); } catch (Exception ignored) {}
                }
            }
            // 文件名：物料名-总数量.zip
            String matName = null;
            BigDecimal totalQty = BigDecimal.ZERO;
            String u = "";
            for (Map<String, Object> r : reports) {
                if (matName == null) matName = String.valueOf(r.getOrDefault("material_name", ""));
                String mid = String.valueOf(r.getOrDefault("material_id", ""));
                String dvUnit2 = String.valueOf(r.getOrDefault("input_unit", ""));
                try { totalQty = totalQty.add(fms.convertAvocadoQty(mid, new BigDecimal(String.valueOf(r.getOrDefault("input_qty", "0"))), dvUnit2)); } catch (Exception ignored) {}
                if (u.isEmpty()) u = fms.convertAvocadoUnit(mid, dvUnit2);
            }
            String qtyStr = totalQty.setScale(0, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
            String dateStr = java.time.LocalDate.now().toString().replace("-", "");
            String cleanName = (matName != null ? matName : "报损视频").replaceAll("\\(.*\\)", "").trim();
            String fileName = cleanName + "-" + qtyStr + u + "-" + dateStr + ".zip";

            // 先生成到磁盘，再提供下载
            String zipDir = new java.io.File(uploadPath, "zips").getAbsolutePath();
            new java.io.File(zipDir).mkdirs();
            java.io.File zipFile = new java.io.File(zipDir, fileName);
            if (!zipFile.exists()) {
                java.io.File tmpFile = new java.io.File(zipDir, fileName + ".tmp");
                java.io.FileOutputStream fos = new java.io.FileOutputStream(tmpFile);
                java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(fos);
                org.springframework.web.client.RestTemplate rt = new org.springframework.web.client.RestTemplate();

                for (Map<String, Object> r : reports) {
                    String storeName = String.valueOf(r.getOrDefault("store_name", "未知门店"));
                    String date = String.valueOf(r.getOrDefault("occurred_date", ""));
                    String voucher = String.valueOf(r.getOrDefault("voucher_url", ""));
                    String qty = String.valueOf(r.getOrDefault("input_qty", ""));
                    String rawUnit = String.valueOf(r.getOrDefault("input_unit", ""));
                    String unit = fms.convertAvocadoUnit(String.valueOf(r.getOrDefault("material_id", "")), rawUnit);
                    String qtyPart;
                    try {
                        BigDecimal avoQty = fms.convertAvocadoQty(String.valueOf(r.getOrDefault("material_id", "")), new BigDecimal(qty), rawUnit);
                        qtyPart = avoQty.stripTrailingZeros().toPlainString() + unit + "_";
                    } catch (Exception e) { qtyPart = qty.isEmpty() ? "" : qty + unit + "_"; }
                    if (voucher.isEmpty()) continue;

                    String[] urls = voucher.split(",");
                    int idx = 1;
                    for (String url : urls) {
                        url = url.trim();
                        if (url.isEmpty()) continue;
                        String path = url;
                        if (path.startsWith("http")) {
                            int si = path.indexOf("/storeInventory");
                            if (si > 0) path = path.substring(si);
                        }
                        // 优读磁盘，兜底走HTTP
                        try {
                            String relPath = path;
                            if (relPath.startsWith("/storeInventory/upload/")) relPath = relPath.substring("/storeInventory/upload/".length());
                            else if (relPath.startsWith("/upload/")) relPath = relPath.substring("/upload/".length());
                            relPath = java.net.URLDecoder.decode(relPath, "UTF-8");
                            java.io.File localFile = new java.io.File(uploadPath, relPath);
                            if (!localFile.exists()) localFile = new java.io.File("./upload", relPath);
                            if (localFile.exists() && localFile.length() > 0) {
                                log.info("ZIP本地读取: {} ({}KB)", localFile.getAbsolutePath(), localFile.length() / 1024);
                                String ext = url.contains(".") ? url.substring(url.lastIndexOf('.')) : ".mp4";
                                String entryName = storeName + "_" + date + "_" + qtyPart + idx + ext;
                                java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(entryName);
                                zos.putNextEntry(entry);
                                java.nio.file.Files.copy(localFile.toPath(), zos);
                                zos.closeEntry();
                                idx++;
                                continue;
                            } else {
                                log.warn("ZIP本地文件不存在: {} (path={})", localFile.getAbsolutePath(), uploadPath);
                                // 再兜底旧路径
                                java.io.File oldFile = new java.io.File("./upload", relPath);
                                if (oldFile.exists() && oldFile.length() > 0) {
                                    log.info("ZIP旧路径读取: {}", oldFile.getAbsolutePath());
                                    String ext = url.contains(".") ? url.substring(url.lastIndexOf('.')) : ".mp4";
                                    String entryName = storeName + "_" + date + "_" + qtyPart + idx + ext;
                                    java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(entryName);
                                    zos.putNextEntry(entry);
                                    java.nio.file.Files.copy(oldFile.toPath(), zos);
                                    zos.closeEntry();
                                    idx++;
                                    continue;
                                }
                            }
                        } catch (Exception ignored) {}
                        // fallback HTTP
                        try {
                            String ext = url.contains(".") ? url.substring(url.lastIndexOf('.')) : ".mp4";
                            String entryName = storeName + "_" + date + "_" + qtyPart + idx + ext;
                            java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(entryName);
                            zos.putNextEntry(entry);
                            rt.execute("http://localhost:30261" + path, org.springframework.http.HttpMethod.GET, null,
                                (org.springframework.web.client.ResponseExtractor<Void>) clientHttpResponse -> {
                                    org.springframework.util.StreamUtils.copy(clientHttpResponse.getBody(), zos);
                                    return null;
                                });
                            zos.closeEntry();
                            idx++;
                        } catch (Exception e) {
                            log.warn("下载视频失败: ID={}", r.get("id"));
                        }
                    }
                }
                zos.finish();
                zos.close();
                fos.close();
                tmpFile.renameTo(zipFile);
            }

            // 从磁盘读取返回
            response.setContentType("application/zip");
            response.setHeader("Content-Disposition", "attachment; filename=" + java.net.URLEncoder.encode(fileName, "UTF-8").replace("+", "%20"));
            response.setContentLengthLong(zipFile.length());
            java.nio.file.Files.copy(zipFile.toPath(), response.getOutputStream());
        } catch (Exception e) {
            log.error("打包ZIP失败", e);
            response.setStatus(500);
        }
    }

    private void addLog(Long reportId, String action, String operator, String remark) {
        addLog(reportId, action, operator, remark, "");
    }
    private void addLog(Long reportId, String action, String operator, String remark, String attachmentUrl) {
        LossReportLog log = new LossReportLog();
        log.setReportId(reportId);
        log.setAction(action);
        log.setOperator(operator);
        log.setRemark(remark != null ? remark : "");
        log.setAttachmentUrl(attachmentUrl != null ? attachmentUrl : "");
        log.setCreatedAt(LocalDateTime.now());
        logMapper.insert(log);
    }

    /** 手动触发牛油果泥加急卡片（测试用） */
    @GetMapping("/api/public/loss-report/trigger-avocado-urgent")
    @ResponseBody
    public Map<String, Object> triggerAvocadoUrgent() {
        String avoId = fms.getAvocadoMaterialId();
        if (avoId.isEmpty()) return Map.of("code", 500, "msg", "未配置牛油果泥 material_id");
        // 找一条牛油果泥的 pending 加急报损
        List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "SELECT id FROM loss_report WHERE loss_type='arrival' AND material_id=? AND status='pending' AND urgent=1 AND del_flag=0 LIMIT 1", avoId);
        if (list.isEmpty()) return Map.of("code", 500, "msg", "无牛油果泥加急报损");
        String reportId = String.valueOf(list.get(0).get("id"));
        Map<String, String> body = new HashMap<>();
        body.put("reportId", reportId);
        return sendUrgentCard(body);
    }

    /** 手动触发牛油果泥审核卡片（测试用） */
    @PostMapping("/api/public/loss-report/trigger-avocado")
    @ResponseBody
    public Map<String, Object> triggerAvocado() {
        com.xzcpc.job.LossReportDailySummaryJob job =
                new com.xzcpc.job.LossReportDailySummaryJob(jdbcTemplate, fms);
        String token = fms.getTenantToken();
        if (token == null) return Map.of("code", 500, "msg", "飞书token获取失败");
        try {
            var method = com.xzcpc.job.LossReportDailySummaryJob.class.getDeclaredMethod("sendCardE", String.class, String.class);
            method.setAccessible(true);
            method.invoke(job, token, java.time.LocalDate.now().toString());
            return Map.of("code", 200, "msg", "已触发牛油果泥审核卡片");
        } catch (Exception e) {
            return Map.of("code", 500, "msg", "调用失败: " + e.getMessage());
        }
    }

    /** 手动触发每日汇总（测试用） */
    @PostMapping("/api/public/loss-report/trigger-summary")
    @ResponseBody
    public Map<String, Object> triggerSummary() {
        String chatId = fms.getLossChatId();
        if (chatId.isEmpty()) return Map.of("code", 500, "msg", "未配置 feishu_loss_chat_id");

        com.xzcpc.job.LossReportDailySummaryJob job =
                new com.xzcpc.job.LossReportDailySummaryJob(jdbcTemplate, fms);
        job.doSend();
        return Map.of("code", 200, "msg", "已触发每日汇总");
    }

    /** 月度已登记报损汇总（H5 页面数据） */
    @GetMapping("/api/public/loss-report/monthly-registered")
    public Map<String, Object> monthlyRegistered(@RequestParam(defaultValue = "") String month,
                                                  @RequestParam(defaultValue = "") String category,
                                                  @RequestParam(defaultValue = "") String startDate,
                                                  @RequestParam(defaultValue = "") String endDate) {
        LocalDate start, end;
        if (!startDate.isEmpty() && !endDate.isEmpty()) {
            start = LocalDate.parse(startDate);
            end = LocalDate.parse(endDate);
        } else if (!month.isEmpty()) {
            YearMonth ym = YearMonth.parse(month);
            start = ym.atDay(1);
            end = start.plusMonths(1);
        } else {
            start = LocalDate.now().minusMonths(1).withDayOfMonth(1);
            end = LocalDate.now().plusDays(1);
        }

        String sql = "SELECT r.id, r.material_name, r.input_qty, r.input_unit, r.reason, r.remark, r.voucher_url, r.store_name, r.qimai_order_no, r.status, r.occurred_date, m.category, m.parent_category " +
                "FROM loss_report r LEFT JOIN material m ON r.material_id = m.material_id " +
                "WHERE r.loss_type='arrival' AND r.status='registered'" +
                " AND r.occurred_date >= ? AND r.occurred_date < ?" +
                " ORDER BY r.store_name, r.occurred_date DESC";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, start, end);
        // 批量加载分类映射
        Map<String, String> catGroupMap = fms.loadCategoryGroupMap();

        List<Map<String, Object>> list = new ArrayList<>();
        Set<String> storeSet = new LinkedHashSet<>();

        for (Map<String, Object> r : rows) {
            // 按 category 参数过滤
            String matCat = (String) r.getOrDefault("category", "");
            if (category != null && !category.isEmpty()) {
                String gk = matCat != null ? catGroupMap.getOrDefault(matCat, "其他类") : "其他类";
                if (!category.equals(gk)) continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            long id = ((Number) r.get("id")).longValue();
            item.put("id", id);
            item.put("materialName", r.getOrDefault("material_name", ""));
            item.put("storeName", r.getOrDefault("store_name", ""));
            item.put("reason", r.getOrDefault("reason", ""));
            item.put("remark", r.getOrDefault("remark", ""));
            item.put("qimaiOrderNo", r.getOrDefault("qimai_order_no", ""));
            Object occDate = r.get("occurred_date");
            item.put("occurredDate", occDate != null ? occDate.toString().substring(5) : "");
            Object qty = r.get("input_qty");
            String unit = (String) r.getOrDefault("input_unit", "");
            String qtyStr = "--";
            if (qty != null) {
                try {
                    java.math.BigDecimal bd = new java.math.BigDecimal(qty.toString());
                    qtyStr = bd.setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + " " + unit;
                } catch (Exception e) { qtyStr = qty.toString() + " " + unit; }
            }
            item.put("qtyStr", qtyStr);
            String voucher = (String) r.getOrDefault("voucher_url", "");
            List<String> imgs = voucher != null && !voucher.isEmpty() ? List.of(voucher.split(",")) : List.of();
            item.put("images", imgs);
            storeSet.add((String) r.get("store_name"));
            item.put("isFruitVeg", true);
            list.add(item);
        }

        return Map.of("month", month, "total", list.size(), "stores", storeSet.size(), "list", list);
    }

    /** 确认发券（单条）：registered → confirmed_resend */
    @PostMapping("/api/public/loss-report/issue-voucher")
    public Map<String, Object> issueVoucher(@RequestBody Map<String, String> body) {
        long id = Long.parseLong(body.get("id"));
        jdbcTemplate.update("UPDATE loss_report SET status='confirmed_resend', confirmed_at=? WHERE id=? AND status='registered'", LocalDateTime.now(), id);
        addLog(id, "issue_voucher", "厂家", null);
        return Map.of("code", 200, "msg", "ok");
    }

    /** 批量确认发券 */
    @SuppressWarnings("unchecked")
    @PostMapping("/api/public/loss-report/batch-issue-voucher")
    public Map<String, Object> batchIssueVoucher(@RequestBody Map<String, Object> body) {
        List<Map<String, String>> items = (List<Map<String, String>>) body.get("items");
        for (Map<String, String> item : items) {
            long id = Long.parseLong(item.get("id"));
            jdbcTemplate.update("UPDATE loss_report SET status='confirmed_resend', confirmed_at=? WHERE id=? AND status='registered'", LocalDateTime.now(), id);
            addLog(id, "issue_voucher", "厂家", null);
        }
        return Map.of("code", 200, "msg", "ok");
    }

    /** 手动触发月度发券汇总（测试用） */
    @PostMapping("/api/public/loss-report/trigger-monthly-voucher")
    @ResponseBody
    public Map<String, Object> triggerMonthlyVoucher() {
        com.xzcpc.job.LossReportMonthlyVoucherJob job =
                new com.xzcpc.job.LossReportMonthlyVoucherJob(jdbcTemplate, fms);
        job.doSend();
        return Map.of("code", 200, "msg", "已触发月度发券汇总");
    }

    private String resolveGroupKey(String category) {
        if (category == null || category.isEmpty()) return "其他类";
        try {
            // 查 loss_notify_card_config，category 列是逗号分隔的分类列表
            // 匹配当前物料分类属于哪个配置行，排除"其他类"
            String name = jdbcTemplate.queryForObject(
                    "SELECT category FROM loss_notify_card_config WHERE status=1 AND category != '其他类' AND FIND_IN_SET(?, category) > 0 ORDER BY LENGTH(category) ASC LIMIT 1",
                    String.class, category);
            if (name != null && !name.isEmpty()) return name;
        } catch (Exception ignored) {}
        return "其他类";
    }

    private String getAtUserForCategory(String groupName) {
        try {
            // "其他类"直接查自己的
            if ("其他类".equals(groupName)) {
                List<String> uids = jdbcTemplate.queryForList(
                        "SELECT feishu_user_id FROM loss_notify_card_config WHERE category='其他类' AND status=1", String.class);
                uids.removeIf(uid -> uid == null || uid.isEmpty());
                return uids.isEmpty() ? "" : String.join(",", uids);
            }
            // 其他组：收集所有包含这些分类的行，取全部 feishu_user_id
            Set<String> cats = new HashSet<>(Arrays.asList(groupName.split(",")));
            cats.remove("");
            if (cats.isEmpty()) return "";
            List<String> uids = jdbcTemplate.queryForList(
                    "SELECT DISTINCT feishu_user_id FROM loss_notify_card_config WHERE status=1 AND category != '其他类' AND (" +
                    cats.stream().map(c -> "FIND_IN_SET('" + c + "', category) > 0").reduce((a, b) -> a + " OR " + b).orElse("1=0") + ")",
                    String.class);
            uids.removeIf(uid -> uid == null || uid.isEmpty());
            return uids.isEmpty() ? "" : String.join(",", uids);
        } catch (Exception ignored) { return ""; }
    }

    @Value("${feishu.app-id:}") private String appId;
    @Value("${feishu.app-secret:}") private String appSecret;
    @Value("${app.server-url:}") private String serverUrl;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String getFeishuToken() {
        try {
            Map<String, String> req = Map.of("app_id", appId, "app_secret", appSecret);
            ResponseEntity<Map> resp = restTemplate.postForEntity(
                    "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal", req, Map.class);
            Map body = resp.getBody();
            if (body != null && "ok".equals(String.valueOf(body.get("msg"))))
                return (String) body.get("tenant_access_token");
        } catch (Exception ex) { ex.printStackTrace(); }
        return null;
    }

    private void sendSummaryCard(String token, String chatId, String name, String date, int pending, int stores, String color, List<Map<String, Object>> reports, String atUser) {
        Map<String, Object> card = new LinkedHashMap<>();
        Map<String, Object> hdr = new LinkedHashMap<>();
        hdr.put("template", color);
        hdr.put("title", textObj("到货验收报损 · " + name));
        card.put("header", hdr);
        List<Map<String, Object>> els = new ArrayList<>();
        boolean isFruitVegGroup = "水果蔬菜".equals(name);
        String info;
        if (isFruitVegGroup) {
            info = "**待确认报损登记清单**\n累计待确认 **" + pending + "** 条 · 涉及 **" + stores + "** 个门店\n\n请逐条确认报损登记。确认后次月统一发券。";
        } else {
            info = "**待确认到货报损清单**\n累计待确认 **" + pending + "** 条 · 涉及 **" + stores + "** 个门店\n\n请逐条确认到货验收报损。";
        }
        if (atUser != null && !atUser.isEmpty()) {
            for (String uid : atUser.split(",")) {
                String u = uid.trim();
                if (!u.isEmpty()) info += "\n<at id=" + u + "></at>";
            }
        }
        els.add(mdEl(info));
        els.add(tagEl("hr"));
        String base = (serverUrl != null && serverUrl.startsWith("http")) ? serverUrl : "http://localhost:4026";
        try { name = java.net.URLEncoder.encode(name, "UTF-8"); } catch (Exception ignored) {}
        String rawUrl = base + "/loss-daily-confirm.html?date=" + date + "&category=" + name;
        String applink = "https://applink.feishu.cn/client/web_app/open?appId=" + appId + "&lk_target_url=" + urlEncode(rawUrl);
        els.add(mdEl("[查看详情并确认](" + applink + ")"));
        card.put("elements", els);

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("config", Map.of("wide_screen_mode", true));
        content.put("header", card.get("header"));
        content.put("elements", card.get("elements"));
        String json;
        try { json = objectMapper.writeValueAsString(content); } catch (Exception ex) { return; }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("receive_id", chatId);
        body.put("msg_type", "interactive");
        body.put("content", json);
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        try {
            restTemplate.exchange("https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=chat_id",
                    HttpMethod.POST, new HttpEntity<>(body, h), String.class);
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private void sendMonthlyVoucherCard(String token, String chatId, String name, String rangeParam, int total, int stores, String color, List<Map<String, Object>> reports, String atUser) {
        Map<String, Object> card = new LinkedHashMap<>();
        Map<String, Object> hdr = new LinkedHashMap<>();
        hdr.put("template", color);
        hdr.put("title", textObj("月度发券确认 · " + name));
        card.put("header", hdr);
        List<Map<String, Object>> els = new ArrayList<>();
        String info = "**" + "水果蔬菜报损发券清单**\n累计已登记 **" + total + "** 条 · 涉及 **" + stores + "** 个门店\n\n请逐条确认发券。确认后门店会看到补发结果。";
        if (atUser != null && !atUser.isEmpty()) {
            for (String uid : atUser.split(",")) {
                String u = uid.trim();
                if (!u.isEmpty()) info += "\n<at id=" + u + "></at>";
            }
        }
        els.add(mdEl(info));
        els.add(tagEl("hr"));
        String base = (serverUrl != null && serverUrl.startsWith("http")) ? serverUrl : "http://localhost:4026";
        try { name = java.net.URLEncoder.encode(name, "UTF-8"); } catch (Exception ignored) {}
        String rawUrl = base + "/loss-monthly-voucher.html?" + rangeParam + "&category=" + name;
        String applink = "https://applink.feishu.cn/client/web_app/open?appId=" + appId + "&lk_target_url=" + urlEncode(rawUrl);
        els.add(mdEl("[查看详情并确认](" + applink + ")"));
        card.put("elements", els);

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("config", Map.of("wide_screen_mode", true));
        content.put("header", card.get("header"));
        content.put("elements", card.get("elements"));
        String json;
        try { json = objectMapper.writeValueAsString(content); } catch (Exception ex) { return; }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("receive_id", chatId);
        body.put("msg_type", "interactive");
        body.put("content", json);
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        try {
            restTemplate.exchange("https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=chat_id",
                    HttpMethod.POST, new HttpEntity<>(body, h), String.class);
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private String getCardColor(String groupName) { if (groupName.contains("水果")) return "yellow"; if ("其他类".equals(groupName)) return "blue"; return "green"; }
    private String urlEncode(String s) { try { return java.net.URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; } }
    private Map<String, Object> textObj(String c) { Map<String, Object> t = new LinkedHashMap<>(); t.put("tag", "plain_text"); t.put("content", c); return t; }
    private Map<String, Object> mdEl(String c) { Map<String, Object> m = new LinkedHashMap<>(); m.put("tag", "markdown"); m.put("content", c); return m; }
    private Map<String, Object> tagEl(String t) { Map<String, Object> e = new LinkedHashMap<>(); e.put("tag", t); return e; }

}
