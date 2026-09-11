package com.xzcpc.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xzcpc.common.exception.BusinessException;
import com.xzcpc.common.feishu.FeishuMessageService;
import com.xzcpc.mp.client.QmaiClient;
import com.xzcpc.mp.entity.Issue;
import com.xzcpc.mp.entity.LossReport;
import com.xzcpc.mp.entity.LossReportLog;
import com.xzcpc.mp.mapper.LossReportLogMapper;
import com.xzcpc.mp.service.IssueService;
import com.xzcpc.mp.util.ConversionFactorUtil;
import com.xzcpc.template.entity.MaterialConversionRule;
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
import org.springframework.web.client.HttpStatusCodeException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.client.ResourceAccessException;
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
    private final QmaiClient qmaiClient;
    private final IssueService issueService;

    @Value("${app.upload.path:./upload}")
    private String uploadPath;

    @Value("${xiangmu.base-url:https://www.xzcpc-9pd.top}")
    private String xiangmuBaseUrl;

    @GetMapping("/api/public/loss-report/daily-summary")
    public Map<String, Object> dailySummary(@RequestParam(defaultValue = "") String date,
                                            @RequestParam(defaultValue = "") String category,
                                            @RequestParam(defaultValue = "") String tab,
                                            @RequestParam(defaultValue = "") String reason,
                                            @RequestParam(defaultValue = "") String materialId,
                                            @RequestParam(defaultValue = "") String uid,
                                            @RequestParam(defaultValue = "") String recheck,
                                            @RequestParam(defaultValue = "") String region,
                                            jakarta.servlet.http.HttpServletRequest request,
                                            jakarta.servlet.http.HttpServletResponse response) {
        // GET 接口禁止缓存：审核结果实时变化，浏览器/飞书 webview 缓存旧响应会导致看到过期数据
        response.setHeader("Cache-Control", "no-store");
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
        // 前端把 recheck 解析成布尔后 encodeURIComponent 会传 "true"，此处兼容 "1"/"true" 两种值
        boolean isRecheck = "1".equals(recheck) || "true".equalsIgnoreCase(recheck);
        // 蓝蛙二次复核页入口判定（与 HTML 端 recheckOp 一致）：E2 审核人卡片链接带 uid=审核人个人 open_id；
        // 蓝蛙群知会卡片不带 uid（或 uid=群 chat_ 开头）。知会入口只展示二次审核已通过单，作为结果查阅页
        boolean isRecheckAudit = "audit".equals(tab) && isRecheck;
        boolean recheckAuditor = isRecheckAudit && !uid.isEmpty() && !uid.startsWith("chat_");
        String statusFilter;
        if (recheckAuditor) {
            // E2 审核人入口：只展示蓝蛙拒绝待复核 + 二次审核已通过单。
            // 只显示二次复核启用（2026-09-01）后新拒绝进入复核队列的单——历史一审拒绝单（8/17 批、8/29 店长拒）不再展示；
            // 已确认不通过（recheck_reject）的单不展示（门店重新提交，无复核意义）
            // 复核人自己一审拒的单不展示（与 E2 Job 选单同口径）：uid 支持多复核人逗号分隔，逐个匹配 IN
            String selfRejectIds = java.util.Arrays.stream(uid.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .map(s -> "'" + s.replace("'", "''") + "'")
                    .collect(java.util.stream.Collectors.joining(","));
            statusFilter = "AND ("
                    + "  (r.status = 'rejected' AND EXISTS (SELECT 1 FROM loss_report_log l2 WHERE l2.report_id = r.id"
                    + "     AND l2.action IN ('reject','audit_reject') AND l2.created_at >= '2026-09-01 00:00:00'))"
                    + "  OR EXISTS (SELECT 1 FROM loss_report_log l WHERE l.report_id = r.id AND l.action = 'recheck_pass')"
                    + ") AND r.remark LIKE '厂家：蓝蛙%'"
                    + " AND NOT EXISTS (SELECT 1 FROM loss_report_log l3 WHERE l3.report_id = r.id"
                    + "     AND l3.action IN ('reject','audit_reject') AND l3.operator IN (" + selfRejectIds + "))";
        } else if (isRecheckAudit) {
            // 蓝蛙群知会入口：只展示二次审核已通过（recheck_pass）的单——待复核队列与已确认不通过均不展示，
            // 蓝蛙被拒的单门店直接重新提交，知会页仅作结果查阅
            statusFilter = "AND EXISTS (SELECT 1 FROM loss_report_log l WHERE l.report_id = r.id AND l.action = 'recheck_pass')"
                    + " AND r.remark LIKE '厂家：蓝蛙%'";
        } else if ("audit".equals(tab)) {
            statusFilter = "AND r.status IN ('pending','registered','rejected')";
            // 已审批（registered/rejected）只展示近一个月：registered 按确认时间 confirmed_at；
            // 一审直接拒绝的单 confirmed_at 为空（拒绝不写确认时间），按最近 reject/audit_reject 日志时间判定展示
            statusFilter += " AND (r.status = 'pending'"
                    + " OR r.confirmed_at >= DATE_SUB(NOW(), INTERVAL 1 MONTH)"
                    + " OR (r.status = 'rejected' AND EXISTS (SELECT 1 FROM loss_report_log l2 WHERE l2.report_id = r.id"
                    + "     AND l2.action IN ('reject','audit_reject') AND l2.created_at >= DATE_SUB(NOW(), INTERVAL 1 MONTH))))";
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
        // 审核Tab排除牛油果泥（有独立卡片E），补发Tab不排除；复核入口（recheck=1）本就只看蓝蛙牛油果泥，不排除
        String avocadoId = fms.getAvocadoMaterialId();
        String avoFilter = "";
        if (materialId.isEmpty() && !"resend".equals(tab) && !isRecheck && !avocadoId.isEmpty()) {
            avoFilter = " AND r.material_id != '" + avocadoId.replace("'", "''") + "'";
        }
        // 牛油果泥审核页按厂家正匹配（无厂家标记的老单归入 HASS 入口）：
        //   入口判定优先 region 参数（卡片链接带 region=lanwa/hass，飞书打开可能丢 uid）；region 缺失时回退 uid：
        //   uid=群 → 蓝蛙；uid=个人/未带 → hass；复核页（recheck=1）走上方蓝蛙复核 SQL，不重复过滤
        if ("audit".equals(tab) && !isRecheck && !materialId.isEmpty() && materialId.equals(avocadoId)) {
            boolean lanwaEntry = "lanwa".equals(region) || (region.isEmpty() && uid.startsWith("chat_"));
            materialFilter += lanwaEntry
                    ? " AND r.remark LIKE '厂家：蓝蛙%'"
                    : " AND (r.remark LIKE '厂家：hass牛油果%' OR r.remark IS NULL OR r.remark = '' OR r.remark NOT LIKE '厂家：%')";
        }
        String baseSql = "SELECT r.id, r.material_id, r.material_name, r.input_qty, r.input_unit, r.orig_qty, r.reason, r.remark, r.voucher_url, r.store_name, r.qimai_order_no, r.status, r.reject_reason, r.urgent, r.occurred_date, m.category, m.parent_category, m.qm_code, r.handler_name, r.submitted_by, " +
                "o.outbound_no, o.status AS outbound_status, o.error AS outbound_error, o.warehouse_no AS outbound_warehouse_no " +
                "FROM loss_report r LEFT JOIN material m ON r.material_id = m.material_id " +
                "LEFT JOIN outbound_order o ON o.id = r.outbound_order_id " +
                "WHERE r.loss_type='arrival' " + statusFilter + reasonFilter + materialFilter + avoFilter + " " +
                catCondition +
                (isRecheckAudit && !recheckAuditor
                    // 知会入口只展示二次审核已通过单，按二次审核通过时间（recheck_pass 时写入 confirmed_at）倒序
                    ? " ORDER BY r.confirmed_at DESC"
                    // 待审核按发生日期排序（原逻辑）；已审批按审核确认时间倒序（最新审批在前）
                    : " ORDER BY CASE WHEN r.status = 'pending' THEN 0 ELSE 1 END, "
                    + "CASE WHEN r.status = 'pending' THEN r.occurred_date ELSE r.confirmed_at END DESC");

        List<Map<String, Object>> allRows = jdbcTemplate.queryForList(baseSql);
        // 批量预加载：下载标记 + 收货反馈日志 + 提交人手机号，避免逐条查库（原实现每条 2 次查询，量大时很慢）
        java.util.Set<Long> idSet = new java.util.HashSet<>();
        List<Object> idList = new ArrayList<>();
        for (Map<String, Object> r : allRows) {
            long id = ((Number) r.get("id")).longValue();
            idSet.add(id);
            idList.add(id);
        }
        java.util.Set<Long> downloadedIds = new java.util.HashSet<>();
        Map<Long, Map<String, Object>> feedbackMap = new HashMap<>();
        Map<Long, String> recheckMap = new HashMap<>(); // 蓝蛙二次审核标记：pass/reject
        Map<Long, String> rejectReasonLogMap = new HashMap<>(); // 厂家拒绝原因（reject 日志，最新一条）
        Map<Long, String> hqRemarkMap = new HashMap<>();        // 总部复核备注（recheck_* 日志）
        Map<String, String> mobileMap = new HashMap<>();
        if (!idList.isEmpty()) {
            String inSql = idList.stream().map(String::valueOf).collect(Collectors.joining(","));
            List<Map<String, Object>> logs = jdbcTemplate.queryForList(
                    "SELECT report_id, action, operator, remark, created_at FROM loss_report_log " +
                    "WHERE report_id IN (" + inSql + ") AND action IN ('download','receive','not_receive','reject','recheck_pass','recheck_reject') " +
                    "ORDER BY created_at ASC");
            for (Map<String, Object> log : logs) {
                long rid = ((Number) log.get("report_id")).longValue();
                String action = (String) log.get("action");
                if ("download".equals(action)) { downloadedIds.add(rid); continue; }
                if ("reject".equals(action)) {
                    // 厂家拒绝原因：复核通过后 reject_reason 列被清空，从日志兜底（created_at 升序，后写=最新）
                    Object rj = log.get("remark");
                    if (rj != null && !rj.toString().isEmpty()) rejectReasonLogMap.put(rid, rj.toString());
                    continue;
                }
                if ("recheck_pass".equals(action)) {
                    recheckMap.put(rid, "pass");
                    hqRemarkMap.put(rid, logRemarkPart((String) log.get("remark"), "总部备注："));
                    continue;
                }
                if ("recheck_reject".equals(action)) {
                    recheckMap.put(rid, "reject");
                    hqRemarkMap.put(rid, logRemarkPart((String) log.get("remark"), "总部备注："));
                    continue;
                }
                feedbackMap.put(rid, log); // created_at 升序，后写覆盖 = 最新一条
            }
            List<String> openids = new ArrayList<>();
            for (Map<String, Object> r : allRows) {
                Object ob = r.get("submitted_by");
                if (ob != null && !ob.toString().isEmpty()) openids.add(ob.toString());
            }
            if (!openids.isEmpty()) {
                String openIn = openids.stream().distinct()
                        .map(o -> "'" + o.replace("'", "''") + "'").collect(Collectors.joining(","));
                List<Map<String, Object>> emps = jdbcTemplate.queryForList(
                        "SELECT openid, mobile FROM employee WHERE openid IN (" + openIn + ")");
                for (Map<String, Object> e : emps) {
                    Object o = e.get("openid");
                    Object mob = e.get("mobile");
                    if (o != null && mob != null) mobileMap.put(o.toString(), mob.toString());
                }
            }
        }
        // 分类分组映射：统一走 fms（含「类」后缀别名容错），避免与 Job 侧口径分叉
        Map<String, String> configMap = fms.loadCategoryGroupMap();

        List<Map<String, Object>> pending = new ArrayList<>();
        List<Map<String, Object>> done = new ArrayList<>();
        int photos = 0;
        Set<String> storeSet = new LinkedHashSet<>();
        Set<String> supplierSet = new LinkedHashSet<>();
        boolean hasNoSupplier = false;
        // 牛油果泥待补发统计（resend tab 累加，与待补发列表同口径）
        Map<String, Map<String, Object>> avoStatMap = new LinkedHashMap<>();
        // 出库仓库编码 → 名称（补发联动 9.2.4，预加载一次）
        Map<String, String> whNameMap = new HashMap<>();
        String whFrozenCfg = getConfig("loss_outbound_wh_frozen", "");
        String whFreshCfg = getConfig("loss_outbound_wh_fresh_milk", "");
        String whCentralCfg = getConfig("loss_outbound_wh_central", "");
        if (!whFrozenCfg.isEmpty()) whNameMap.put(whFrozenCfg, "冷冻仓");
        if (!whFreshCfg.isEmpty()) whNameMap.put(whFreshCfg, "鲜奶仓");
        if (!whCentralCfg.isEmpty()) whNameMap.put(whCentralCfg, "总仓");

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
            // 拒绝原因：优先取 reject 日志（复核通过后列被清空），无日志回退列值
            String rejectReasonLog = rejectReasonLogMap.get(id);
            item.put("rejectReason", rejectReasonLog != null && !rejectReasonLog.isEmpty()
                    ? rejectReasonLog : r.getOrDefault("reject_reason", ""));
            item.put("hqRemark", hqRemarkMap.getOrDefault(id, ""));
            Object qty = r.get("input_qty");
            String unit = (String) r.getOrDefault("input_unit", "");
            String qtyStr = "--";
            String mid = String.valueOf(r.getOrDefault("material_id", ""));
            item.put("materialId", mid);
            // 牛油果泥判定：material_id 配置命中，或物料名称兜底（配置缺失/不对时也能分开）
            boolean isAvo = avocadoId != null && !avocadoId.isEmpty() && avocadoId.equals(mid)
                    || String.valueOf(r.getOrDefault("material_name", "")).contains("牛油果泥");
            item.put("isAvocado", isAvo);
            String displayUnit = unit;
            java.math.BigDecimal convQty = null;
            if (qty != null) {
                try {
                    java.math.BigDecimal bd = new java.math.BigDecimal(qty.toString());
                    if (isAvo && "件".equals(unit)) { bd = bd.multiply(new java.math.BigDecimal("24")); displayUnit = "包"; }
                    qtyStr = bd.setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + " " + displayUnit;
                    convQty = bd;
                } catch (Exception e) { qtyStr = qty.toString() + " " + displayUnit; }
            }
            // 确认登记时修改过数量：input_qty 已是修改后数量，orig_qty 记录原始数量（按包）供前端红色标注
            item.put("origQtyStr", "");
            Object origQty = r.get("orig_qty");
            if (origQty != null) {
                try {
                    java.math.BigDecimal obd = new java.math.BigDecimal(origQty.toString());
                    item.put("origQtyStr", obd.setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + " 包");
                } catch (Exception ignored) {}
            }
            item.put("qtyStr", qtyStr);
            item.put("displayUnit", displayUnit);
            item.put("qtyNum", convQty != null ? convQty.doubleValue() : 0.0); // 换算后数量（牛油果泥已折成包），供前端统计兜底
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
            // 出库单信息（补发联动 9.2.4，所有 tab 返回）：success=出库单号+仓库名；failed=失败原因
            String obNo = r.get("outbound_no") != null ? String.valueOf(r.get("outbound_no")) : "";
            String obStatus = r.get("outbound_status") != null ? String.valueOf(r.get("outbound_status")) : "";
            String obError = r.get("outbound_error") != null ? String.valueOf(r.get("outbound_error")) : "";
            String obWhNo = r.get("outbound_warehouse_no") != null ? String.valueOf(r.get("outbound_warehouse_no")) : "";
            item.put("outboundNo", obNo);
            item.put("outboundStatus", obStatus);
            item.put("outboundError", obError);
            item.put("outboundWarehouse", whNameMap.getOrDefault(obWhNo, obWhNo));
            // 牛油果泥待补发统计累加（与待补发列表同一批数据，口径完全一致）
            if ("resend".equals(tab) && isAvo && "registered".equals(status)) {
                String sn = String.valueOf(r.get("store_name"));
                Map<String, Object> ex = avoStatMap.get(sn);
                if (ex == null) {
                    ex = new LinkedHashMap<>();
                    ex.put("store_name", sn);
                    ex.put("cnt", 0L);
                    ex.put("total_qty", java.math.BigDecimal.ZERO);
                    ex.put("input_unit", "包");
                    avoStatMap.put(sn, ex);
                }
                ex.put("cnt", ((Number) ex.get("cnt")).longValue() + 1);
                ex.put("total_qty", ((java.math.BigDecimal) ex.get("total_qty")).add(convQty != null ? convQty : java.math.BigDecimal.ZERO));
            }
            String parentCat = (String) r.getOrDefault("parent_category", "");
            String matCat = (String) r.getOrDefault("category", "");
            String gKey = fms.resolveCategoryGroup(configMap, matCat);
            // 分类或父分类命中水果蔬菜分组都算水果蔬菜（部分物料分类未配置时靠父分类兜底）
            String pKey = parentCat != null ? configMap.getOrDefault(parentCat, "") : "";
            item.put("urgent", r.getOrDefault("urgent", 0));
            item.put("isFruitVeg", "水果蔬菜".equals(gKey) || "水果蔬菜".equals(pKey));
            item.put("handlerName", r.getOrDefault("handler_name", ""));
            item.put("handlerPhone", mobileMap.get(String.valueOf(r.getOrDefault("submitted_by", ""))));
            // 下载标记（批量预加载）
            item.put("downloaded", downloadedIds.contains(id));
            // 供应商提取：从 remark 解析"厂家：XXX"
            String remarkText = (String) r.getOrDefault("remark", "");
            String supplier = extractSupplier(remarkText);
            item.put("supplier", supplier); // 空字符串表示无厂家，H5不显示标签
            if (!supplier.isEmpty()) {
                supplierSet.add(supplier);
            } else {
                hasNoSupplier = true;
            }
            photos += imgs.size();
            storeSet.add((String) r.get("store_name"));
            // 门店反馈（收货日志，批量预加载）
            Map<String, Object> log = feedbackMap.get(id);
            if (log != null) {
                String act = (String) log.get("action");
                item.put("feedback", ("not_receive".equals(act) ? "门店未收到货" : "门店已收货") +
                        (log.get("remark") != null && !log.get("remark").toString().isEmpty() ? " — " + log.get("remark") : ""));
            }
            // 蓝蛙拒绝二次审核标记（pass=已通过 / reject=已确认不通过 / 空=待复核）
            item.put("rechecked", recheckMap.getOrDefault(id, ""));
            if ("pending".equals(status)) pending.add(item); else done.add(item);
        }
        List<Map<String, Object>> list = new ArrayList<>(pending);
        list.addAll(done);
        // 牛油果泥门店统计：来自待补发列表累加，供 H5「24包及以上/24包以下」筛选
        List<Map<String, Object>> avocadoStats = new ArrayList<>(avoStatMap.values());
        if (hasNoSupplier) supplierSet.add("无厂家");
        return Map.of("pending", pending.size(), "done", done.size(), "stores", storeSet.size(), "photos", photos, "list", list, "suppliers", new ArrayList<>(supplierSet), "avocadoStats", avocadoStats);
    }

    /** 从 remark 提取供应商：优先"厂家：XXX"手打格式，其次到货登记页的"【蓝蛙牛油果】"前缀格式，无则返回空字符串 */
    private String extractSupplier(String remark) {
        if (remark == null || remark.isEmpty()) return "";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("厂家[:：]([^，,\\s]{1,30})");
        java.util.regex.Matcher m = p.matcher(remark);
        if (m.find()) return m.group(1);
        java.util.regex.Matcher m2 = java.util.regex.Pattern.compile("^【(.+?)】").matcher(remark);
        if (m2.find()) return m2.group(1);
        return "";
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
            String logRemark = remark;
            String confirmQtyStr = body.get("confirmQty");
            if (confirmQtyStr != null && !confirmQtyStr.isEmpty()) {
                // 审核确认时修改数量：修改后数量直接写回 input_qty（单位改为换算后单位，如"包"），
                // 下游补发/日报/导出继续读 input_qty 无需改动；原始数量记入 orig_qty
                try {
                    Map<String, Object> cur = jdbcTemplate.queryForMap("SELECT input_qty, input_unit, material_id FROM loss_report WHERE id=?", id);
                    BigDecimal origConv = fms.convertAvocadoQty(String.valueOf(cur.getOrDefault("material_id", "")),
                            new BigDecimal(String.valueOf(cur.get("input_qty"))), String.valueOf(cur.getOrDefault("input_unit", "")));
                    String origUnitDisp = fms.convertAvocadoUnit(String.valueOf(cur.getOrDefault("material_id", "")), String.valueOf(cur.getOrDefault("input_unit", "")));
                    BigDecimal cfQty = new BigDecimal(confirmQtyStr);
                    if (cfQty.compareTo(origConv) == 0) {
                        // 与原始一致，不记录修改
                        jdbcTemplate.update("UPDATE loss_report SET status='registered', confirmed_at=? WHERE id=?", LocalDateTime.now(), id);
                    } else {
                        jdbcTemplate.update("UPDATE loss_report SET status='registered', confirmed_at=?, input_qty=?, input_unit=?, orig_qty=? WHERE id=?",
                                LocalDateTime.now(), cfQty, origUnitDisp, origConv, id);
                        String origStr = origConv.setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
                        String cfStr = cfQty.setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
                        logRemark = "数量修改：原始" + origStr + origUnitDisp + " → 确认" + cfStr + origUnitDisp + (remark.isEmpty() ? "" : "；备注：" + remark);
                    }
                } catch (Exception e) {
                    // 数量解析失败则不修改数量，仅确认登记
                    jdbcTemplate.update("UPDATE loss_report SET status='registered', confirmed_at=? WHERE id=?", LocalDateTime.now(), id);
                    log.warn("确认登记数量修改失败 id={}", id, e);
                }
            } else {
                jdbcTemplate.update("UPDATE loss_report SET status='registered', confirmed_at=? WHERE id=?", LocalDateTime.now(), id);
            }
            addLog(id, "register", "厂家", logRemark, attachmentUrl);
            if (!isAvocadoMaterial(id)) sendAuditDoneCard(id);
        } else if ("补发".equals(action)) {
            jdbcTemplate.update("UPDATE loss_report SET status='confirmed_resend', confirmed_at=? WHERE id=?", LocalDateTime.now(), id);
            addLog(id, "confirm", "厂家", null, attachmentUrl);
        } else {
            String reason = body.get("reason");
            // 一审拒绝操作人：页面免登采集到 open_id 则按人记录（识别"谁拒的"供 E2 排除自己拒的单）；
            // 外部联系人/非飞书环境拿不到身份 → 回落"厂家"（保持原语义）
            String operator = body.getOrDefault("operator", "");
            jdbcTemplate.update("UPDATE loss_report SET status='rejected', reject_reason=? WHERE id=?", reason != null ? reason : "", id);
            addLog(id, "reject", operator.isEmpty() ? "厂家" : operator, reason != null ? reason : "", attachmentUrl);
        }
        return Map.of("code", 200, "msg", "ok");
    }

    /**
     * H5 免登 code 换 open_id（审核页采集一审操作人身份）。
     * 外部联系人/飞书外浏览器无 code → 返回 code=500，页面回落不带 operator。
     */
    @PostMapping("/api/public/loss-report/operator-open-id")
    public Map<String, Object> operatorOpenId(@RequestBody Map<String, String> body) {
        String openId = fms.exchangeCodeOpenId(body.getOrDefault("code", ""));
        return Map.of("code", openId == null ? 500 : 0, "openId", openId == null ? "" : openId);
    }

    /** 蓝蛙拒绝二次审核：直接通过（rejected→registered，进入补发流程） */
    @PostMapping("/api/public/loss-report/recheck-pass")
    public Map<String, Object> recheckPass(@RequestBody Map<String, String> body) {
        return recheckLanwa(body, true);
    }

    /** 蓝蛙拒绝二次审核：确定不通过（保持 rejected，门店重新提交） */
    @PostMapping("/api/public/loss-report/recheck-reject")
    public Map<String, Object> recheckReject(@RequestBody Map<String, String> body) {
        return recheckLanwa(body, false);
    }

    /**
     * 蓝蛙群拒绝的单，审核人二次审核。仅蓝蛙牛油果泥单、status=rejected、且未复核过（幂等）。
     * 通过：status→registered，清 reject_reason；不通过：保持 rejected（门店端重新提交，现状已支持）。
     */
    private Map<String, Object> recheckLanwa(Map<String, String> body, boolean pass) {
        long id = Long.parseLong(body.get("id"));
        String uid = body.getOrDefault("uid", "");
        String operator = uid.isEmpty() ? "审核人" : uid;
        String hqRemark = body.getOrDefault("remark", "").trim(); // 总部复核备注（选填）
        Map<String, Object> cur;
        try {
            cur = jdbcTemplate.queryForMap("SELECT status, remark FROM loss_report WHERE id=?", id);
        } catch (Exception e) {
            return Map.of("code", 500, "msg", "报损单不存在");
        }
        if (!"rejected".equals(String.valueOf(cur.get("status")))) return Map.of("code", 500, "msg", "仅已拒绝的单可二次审核");
        String remark = cur.get("remark") != null ? String.valueOf(cur.get("remark")) : "";
        if (!remark.startsWith("厂家：蓝蛙")) return Map.of("code", 500, "msg", "仅蓝蛙厂家单可二次审核");
        List<Long> dup = jdbcTemplate.queryForList(
                "SELECT id FROM loss_report_log WHERE report_id=? AND action IN ('recheck_pass','recheck_reject') LIMIT 1",
                Long.class, id);
        if (!dup.isEmpty()) return Map.of("code", 500, "msg", "该单已复核过");
        String remarkSuffix = hqRemark.isEmpty() ? "" : "；总部备注：" + hqRemark;
        if (pass) {
            jdbcTemplate.update("UPDATE loss_report SET status='registered', confirmed_at=?, reject_reason=NULL WHERE id=?",
                    LocalDateTime.now(), id);
            addLog(id, "recheck_pass", operator, "二次审核直接通过" + remarkSuffix, "");
            notifyLanwaRecheckPass(id); // 即时知会蓝蛙群（厂家不补发，仅告知复核结论）
        } else {
            addLog(id, "recheck_reject", operator, "二次审核确定不通过（门店可重新提交）" + remarkSuffix, "");
        }
        return Map.of("code", 200, "msg", pass ? "已通过，进入补发流程" : "已确认不通过，门店可重新提交");
    }

    /** 取日志 remark 中某标记之后的部分（如「总部备注：」）；无标记返回空串 */
    private static String logRemarkPart(String logRemark, String marker) {
        if (logRemark == null) return "";
        int i = logRemark.indexOf(marker);
        return i < 0 ? "" : logRemark.substring(i + marker.length()).trim();
    }

    /** 二次审核通过后即时知会蓝蛙群：厂家只审核不补发，补发由总部补发人走每日卡片 G；失败不阻断主流程 */
    private void notifyLanwaRecheckPass(long id) {
        try {
            String chatTargets = fms.getCardUserId("其他类", "avocado_audit_lanwa");
            if (chatTargets.isEmpty()) return;
            Map<String, Object> cur = jdbcTemplate.queryForMap(
                    "SELECT store_name, material_name, material_id, input_qty, input_unit, reason, "
                    + "DATE_FORMAT(occurred_date, '%Y-%m-%d') AS occurred_date FROM loss_report WHERE id=?", id);
            String token = fms.getTenantToken();
            if (token == null) return;
            String store = String.valueOf(cur.getOrDefault("store_name", ""));
            String name = String.valueOf(cur.getOrDefault("material_name", ""));
            String mid = String.valueOf(cur.getOrDefault("material_id", ""));
            String unit = String.valueOf(cur.getOrDefault("input_unit", ""));
            String qtyStr = "--";
            Object qty = cur.get("input_qty");
            if (qty != null) {
                try {
                    java.math.BigDecimal bd = fms.convertAvocadoQty(mid, new java.math.BigDecimal(qty.toString()), unit);
                    qtyStr = bd.setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                            + " " + fms.convertAvocadoUnit(mid, unit);
                } catch (Exception ignored) { qtyStr = qty + " " + unit; }
            }
            String reason = String.valueOf(cur.getOrDefault("reason", ""));
            // 厂家拒绝原因：复核通过后 reject_reason 列被清空，只能取 reject 日志 remark
            String rejectReason = "";
            try {
                List<String> rs = jdbcTemplate.queryForList(
                        "SELECT remark FROM loss_report_log WHERE report_id=? AND action='reject' ORDER BY id DESC LIMIT 1",
                        String.class, id);
                if (!rs.isEmpty() && rs.get(0) != null) rejectReason = rs.get(0).trim();
            } catch (Exception ignored) {}
            // 总部复核备注：取 recheck_pass 日志 remark 的「总部备注：」之后部分（人工补发历史单同样适用）
            String hqRemark = "";
            try {
                List<String> rs = jdbcTemplate.queryForList(
                        "SELECT remark FROM loss_report_log WHERE report_id=? AND action='recheck_pass' ORDER BY id DESC LIMIT 1",
                        String.class, id);
                if (!rs.isEmpty()) hqRemark = logRemarkPart(rs.get(0), "总部备注：");
            } catch (Exception ignored) {}
            String occurredDate = cur.get("occurred_date") == null ? "" : String.valueOf(cur.get("occurred_date"));
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("header", fms.cardHeader("blue", "到货验收报损 · 牛油果泥（蓝蛙）二次审核通过"));
            List<Map<String, Object>> els = new ArrayList<>();
            els.add(fms.mdEl("**" + store + "**\n" + qtyStr + " · " + name +
                    "\n报损原因：" + (reason.isEmpty() ? "--" : reason) +
                    (rejectReason.isEmpty() ? "" : "\n厂家拒绝原因：" + rejectReason) +
                    (hqRemark.isEmpty() ? "" : "\n总部复核备注：" + hqRemark) +
                    "\n\n该单已通过总部复核，报损成立。"));
            if (!occurredDate.isEmpty() && !"null".equals(occurredDate)) {
                String avocadoId = fms.getAvocadoMaterialId();
                if (!avocadoId.isEmpty()) {
                    els.add(fms.mdEl("[查看详情](" + fms.buildApplink("/loss-daily-confirm.html?date=" + occurredDate
                            + "&category=其他类&tab=audit&materialId=" + avocadoId + "&recheck=1") + ")"));
                }
            }
            card.put("elements", els);
            fms.sendToCardTargets(token, chatTargets, card);
            log.info("蓝蛙二次审核通过知会 id={} → {}", id, chatTargets);
        } catch (Exception e) {
            log.warn("蓝蛙二次审核通过知会失败 id={}", id, e);
        }
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
            // 与每日 B2 其他类补发卡片一致：固定 category=其他类、不带 reason/分组过滤，展示全部其他类补发
            String today = LocalDate.now().toString();
            String link = fms.buildApplink("/loss-daily-confirm.html?date=" + today + "&category="
                    + java.net.URLEncoder.encode("其他类", "UTF-8") + "&tab=resend&uid=" + userId);
            els.add(fms.mdEl("[查看详情](" + link + ")"));
            card.put("elements", els);

            fms.sendToCardTargets(token, userId, card);
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

        // 牛油果泥走独立卡片（按 remark"厂家："前缀拆分：蓝蛙 → avocado_audit_lanwa，其余 → avocado_audit）
        String avoId = fms.getAvocadoMaterialId();
        String userId;
        String cardTitle;
        if (!avoId.isEmpty() && avoId.equals(
                jdbcTemplate.queryForObject("SELECT material_id FROM loss_report WHERE id=?", String.class, reportId))) {
            String remark = null;
            try {
                remark = jdbcTemplate.queryForObject("SELECT remark FROM loss_report WHERE id=?", String.class, reportId);
            } catch (Exception ignored) {}
            boolean lanwa = remark != null && remark.startsWith("厂家：蓝蛙");
            userId = fms.getCardUserId("其他类", lanwa ? "avocado_audit_lanwa" : "avocado_audit");
            cardTitle = "加急到货报损 · 牛油果泥" + (lanwa ? "（蓝蛙）" : "");
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

        fms.sendToCardTargets(token, userId, card);
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
    public void downloadVideosExcel(@RequestParam String ids,
                                    @RequestParam(defaultValue = "") String supplier,
                                    jakarta.servlet.http.HttpServletResponse response) {
        try {
            String[] idArr = ids.split(",");
            List<Map<String, Object>> rows = new ArrayList<>();
            BigDecimal totalQty = BigDecimal.ZERO; String unit = ""; String matName = "";
            for (String sid : idArr) {
                try {
                    long id = Long.parseLong(sid.trim());
                    Map<String, Object> r = jdbcTemplate.queryForMap(
                        "SELECT store_name, material_id, material_name, occurred_date, input_qty, input_unit, remark FROM loss_report WHERE id=? AND del_flag=0", id);
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
            String fname = matName + (supplier.isEmpty() ? "" : "-" + supplier) + "_" + today + "_" + qtyStr + unit + ".xls";

            response.setContentType("application/vnd.ms-excel;charset=UTF-8");
            response.setHeader("X-Filename", java.net.URLEncoder.encode(fname, "UTF-8").replace("+", "%20"));
            java.io.PrintWriter w = response.getWriter();
            w.println("<meta charset='UTF-8'><style>td,th{border:1px solid #999;padding:4px 8px}</style><table border='1' cellspacing='0'><tr><th>门店</th><th>物料</th><th>日期</th><th>数量</th><th>单位</th><th>厂家</th></tr>");
            for (Map<String, Object> r : rows) {
                String mid = String.valueOf(r.getOrDefault("material_id", ""));
                String inputUnit1 = String.valueOf(r.getOrDefault("input_unit", ""));
                String q; try { BigDecimal bd = fms.convertAvocadoQty(mid, new BigDecimal(String.valueOf(r.get("input_qty"))), inputUnit1); q = bd.setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString(); } catch (Exception e) { q = String.valueOf(r.get("input_qty")); }
                String u = fms.convertAvocadoUnit(mid, inputUnit1);
                String sup = extractSupplier(String.valueOf(r.getOrDefault("remark", "")));
                if (sup.isEmpty()) sup = "无厂家";
                w.println("<tr><td>" + r.get("store_name") + "</td><td>" + r.get("material_name") + "</td><td>" + r.get("occurred_date") + "</td><td>" + q + "</td><td>" + u + "</td><td>" + sup + "</td></tr>");
            }
            w.println("</table>");
        } catch (Exception e) { log.error("导出Excel失败", e); }
    }

    /** 牛油果泥导出 Excel */
    @GetMapping("/api/public/loss-report/export-avocado")
    public void exportAvocado(@RequestParam String materialId, @RequestParam(defaultValue = "0") int downloaded,
                              @RequestParam(defaultValue = "") String supplier,
                              jakarta.servlet.http.HttpServletResponse response) {
        try {
            String sql = "SELECT r.store_name, r.occurred_date, r.input_qty, r.input_unit, r.material_name, r.remark " +
                    "FROM loss_report r LEFT JOIN loss_report_log l ON r.id = l.report_id AND l.action = 'download' " +
                    "WHERE r.loss_type='arrival' AND r.material_id=? AND r.status='pending' AND r.del_flag=0 " +
                    (downloaded == 1 ? "AND l.id IS NOT NULL" : "AND l.id IS NULL") +
                    " ORDER BY r.store_name, r.occurred_date";
            List<Map<String, Object>> allRows = jdbcTemplate.queryForList(sql, materialId);
            // 按供应商过滤
            List<Map<String, Object>> rows = allRows;
            if (!supplier.isEmpty()) {
                rows = new ArrayList<>();
                for (Map<String, Object> r : allRows) {
                    String sup = extractSupplier(String.valueOf(r.getOrDefault("remark", "")));
                    if (supplier.equals(sup) || ("无厂家".equals(supplier) && sup.isEmpty())) rows.add(r);
                }
            }

            BigDecimal totalQty = BigDecimal.ZERO; String matName = "";
            for (Map<String, Object> r : rows) {
                try { totalQty = totalQty.add(fms.convertAvocadoQty(materialId, new BigDecimal(String.valueOf(r.get("input_qty"))), String.valueOf(r.getOrDefault("input_unit", "")))); } catch (Exception ignored) {}
                if (matName.isEmpty()) matName = String.valueOf(r.getOrDefault("material_name", "牛油果泥").toString().replaceAll("\\(.*\\)", "").trim());
            }
            String today = java.time.LocalDate.now().toString().replace("-", "");
            String qtyStr = totalQty.setScale(0, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
            String fname = matName + "-" + qtyStr + "包-" + (supplier.isEmpty() ? "" : supplier + "-") + today + ".xls";

            response.setContentType("application/vnd.ms-excel;charset=UTF-8");
            response.setHeader("X-Filename", java.net.URLEncoder.encode(fname, "UTF-8").replace("+", "%20"));

            response.setContentType("application/vnd.ms-excel;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename=" + java.net.URLEncoder.encode(fname, "UTF-8").replace("+", "%20"));
            java.io.PrintWriter w = response.getWriter();
            w.println("<meta charset='UTF-8'><style>td,th{border:1px solid #999;padding:4px 8px}</style><table border='1' cellspacing='0'><tr><th>门店</th><th>日期</th><th>数量</th><th>单位</th></tr>");
            for (Map<String, Object> r : rows) {
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

    /** 视频下载拆分阈值：压缩包超过 990MB 时拆分为多个 ZIP */
    private static final long ZIP_SPLIT_SIZE = 990L * 1024 * 1024;

    /** 单个视频条目（url + 本地文件定位 + 大小 + ZIP 内文件名） */
    private static class VideoEntry {
        String url;             // 相对路径（/storeInventory/... 或 /upload/...）
        java.io.File localFile; // 本地文件，可能为 null（走 HTTP 兜底）
        long size;              // 字节数
        String entryName;       // ZIP 内文件名
    }

    /** 下载规划：统计视频总大小，超过 990MB 时拆分为多个 ZIP，前端分包依次下载 */
    @GetMapping("/api/public/loss-report/download-videos-plan")
    public Map<String, Object> downloadVideosPlan(@RequestParam String ids, @RequestParam(defaultValue = "0") int markDownloaded,
                                                  @RequestParam(defaultValue = "") String uid,
                                                  @RequestParam(defaultValue = "") String supplier) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> reports = parseReports(ids);
        if (reports.isEmpty()) { result.put("code", 404); result.put("msg", "无有效记录"); return result; }
        try {
            // 记录下载日志（与单包下载同口径）
            if (markDownloaded == 1) {
                for (Map<String, Object> r : reports) {
                    long rid = ((Number) r.get("id")).longValue();
                    try { addLog(rid, "download", uid.isEmpty() ? "系统" : uid, "批量下载"); } catch (Exception ignored) {}
                }
            }
            List<VideoEntry> entries = flattenVideoEntries(reports);
            List<List<VideoEntry>> parts = packVideoParts(entries, ZIP_SPLIT_SIZE);
            int partCnt = Math.max(parts.size(), 1);
            String base = fileNameBase(reports, supplier);
            result.put("code", 200);
            result.put("split", partCnt > 1);
            result.put("parts", partCnt);
            result.put("base", "/api/public/loss-report/download-videos?ids=" + ids + "&supplier=" + java.net.URLEncoder.encode(supplier, "UTF-8"));
            long totalSize = 0;
            for (VideoEntry e : entries) totalSize += Math.max(e.size, 0);
            result.put("totalSize", totalSize);
            List<String> names = new ArrayList<>();
            for (int i = 1; i <= partCnt; i++) names.add(zipPartFileName(base, i, partCnt));
            result.put("names", names);
            return result;
        } catch (Exception e) {
            log.error("视频下载规划失败", e);
            result.put("code", 500);
            result.put("msg", "规划失败");
            return result;
        }
    }

    /** 视频下载：按 report IDs 打包 ZIP；part>0 时只生成/返回指定分包 */
    @GetMapping("/api/public/loss-report/download-videos")
    public void downloadVideos(@RequestParam String ids, @RequestParam(defaultValue = "0") int markDownloaded,
                               @RequestParam(defaultValue = "") String uid,
                               @RequestParam(defaultValue = "") String supplier,
                               @RequestParam(defaultValue = "0") int part,
                               jakarta.servlet.http.HttpServletResponse response) {
        List<Map<String, Object>> reports = parseReports(ids);
        if (reports.isEmpty()) { response.setStatus(404); return; }

        try {
            // 记录下载日志
            if (markDownloaded == 1) {
                for (Map<String, Object> r : reports) {
                    long rid = ((Number) r.get("id")).longValue();
                    try { addLog(rid, "download", uid.isEmpty() ? "系统" : uid, "批量下载"); } catch (Exception ignored) {}
                }
            }
            List<VideoEntry> entries = flattenVideoEntries(reports);
            List<List<VideoEntry>> parts = packVideoParts(entries, ZIP_SPLIT_SIZE);
            int partCnt = Math.max(parts.size(), 1);
            String base = fileNameBase(reports, supplier);

            java.io.File zipFile;
            String fileName;
            if (part > 0) {
                // 指定分包下载
                if (part > partCnt) { response.setStatus(404); return; }
                zipFile = buildPartZip(base, parts.get(part - 1), part, partCnt);
                fileName = zipPartFileName(base, part, partCnt);
            } else {
                // 未指定分包：全部打成一个包（兼容旧调用）
                zipFile = buildPartZip(base, entries, 1, 1);
                fileName = base + ".zip";
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

    /** 解析 ids → 报损记录（保持顺序，忽略无效 id） */
    private List<Map<String, Object>> parseReports(String ids) {
        String[] idArr = ids.split(",");
        List<Map<String, Object>> reports = new ArrayList<>();
        for (String sid : idArr) {
            try {
                long id = Long.parseLong(sid.trim());
                Map<String, Object> r = jdbcTemplate.queryForMap(
                        "SELECT id, store_name, voucher_url, occurred_date, input_qty, input_unit, material_id, material_name, remark FROM loss_report WHERE id=? AND del_flag=0", id);
                reports.add(r);
            } catch (Exception ignored) {}
        }
        return reports;
    }

    /** ZIP 文件名主体：物料名-总数量[单位]-供应商-日期（不含 .zip） */
    private String fileNameBase(List<Map<String, Object>> reports, String supplier) {
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
        return cleanName + "-" + qtyStr + u + (supplier.isEmpty() ? "" : "-" + supplier) + "-" + dateStr;
    }

    /** 分包文件名：多个分包时加 -partNofM 后缀 */
    private String zipPartFileName(String base, int partNo, int partCnt) {
        return partCnt > 1 ? base + "-part" + partNo + "of" + partCnt + ".zip" : base + ".zip";
    }

    /** 把 reports 展开成视频条目列表（含本地文件定位与大小） */
    private List<VideoEntry> flattenVideoEntries(List<Map<String, Object>> reports) {
        List<VideoEntry> list = new ArrayList<>();
        for (Map<String, Object> r : reports) {
            String remark = String.valueOf(r.getOrDefault("remark", ""));
            String sup = extractSupplier(remark);
            String supplierPrefix = sup.isEmpty() ? "无厂家/" : sup + "/";
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
                VideoEntry ve = new VideoEntry();
                ve.url = path;
                ve.localFile = resolveLocalFile(path);
                ve.size = ve.localFile != null ? ve.localFile.length() : remoteFileSize(path);
                String ext = url.contains(".") ? url.substring(url.lastIndexOf('.')) : ".mp4";
                ve.entryName = supplierPrefix + storeName + "_" + date + "_" + qtyPart + idx + ext;
                list.add(ve);
                idx++;
            }
        }
        return list;
    }

    /** 本地文件定位（优读磁盘，找不到返回 null 走 HTTP 兜底） */
    private java.io.File resolveLocalFile(String path) {
        try {
            String relPath = path;
            if (relPath.startsWith("/storeInventory/upload/")) relPath = relPath.substring("/storeInventory/upload/".length());
            else if (relPath.startsWith("/upload/")) relPath = relPath.substring("/upload/".length());
            relPath = java.net.URLDecoder.decode(relPath, "UTF-8");
            java.io.File f = new java.io.File(uploadPath, relPath);
            if (f.exists() && f.length() > 0) return f;
            f = new java.io.File("./upload", relPath);
            if (f.exists() && f.length() > 0) return f;
        } catch (Exception ignored) {}
        return null;
    }

    /** 兜底 HTTP URL：path 是已编码的（中文门店名 %E8...），先解码再交给 RestTemplate 重新编码一次，
     *  避免双重编码（请求行出现 %25E8）导致 mp 端静态资源 404 */
    private String httpUrl(String path) {
        try { return "http://localhost:30261" + java.net.URLDecoder.decode(path, "UTF-8"); }
        catch (Exception e) { return "http://localhost:30261" + path; }
    }

    /** HTTP 兜底文件大小（HEAD Content-Length，失败按 0 计） */
    private long remoteFileSize(String path) {
        try {
            long len = new RestTemplate().headForHeaders(httpUrl(path)).getContentLength();
            return len > 0 ? len : 0;
        } catch (Exception e) { return 0; }
    }

    /** 按阈值把条目拆成多个分包（贪心；单条目超阈值时独占一个分包） */
    private List<List<VideoEntry>> packVideoParts(List<VideoEntry> entries, long maxSize) {
        List<List<VideoEntry>> parts = new ArrayList<>();
        List<VideoEntry> cur = new ArrayList<>();
        long curSize = 0;
        for (VideoEntry e : entries) {
            long sz = Math.max(e.size, 0);
            if (!cur.isEmpty() && curSize + sz > maxSize) {
                parts.add(cur);
                cur = new ArrayList<>();
                curSize = 0;
            }
            cur.add(e);
            curSize += sz;
        }
        if (!cur.isEmpty()) parts.add(cur);
        return parts;
    }

    /** 生成（或复用磁盘缓存）指定分包 ZIP */
    private java.io.File buildPartZip(String base, List<VideoEntry> entries, int partNo, int partCnt) throws Exception {
        String zipDir = new java.io.File(uploadPath, "zips").getAbsolutePath();
        new java.io.File(zipDir).mkdirs();
        String fileName = zipPartFileName(base, partNo, partCnt);
        java.io.File zipFile = new java.io.File(zipDir, fileName);
        if (!zipFile.exists()) {
            java.io.File tmpFile = new java.io.File(zipDir, fileName + ".tmp");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(tmpFile);
            java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(fos);
            org.springframework.web.client.RestTemplate rt = new org.springframework.web.client.RestTemplate();
            for (VideoEntry ve : entries) {
                try {
                    boolean viaHttp = ve.localFile == null;
                    if (!viaHttp) {
                        // 本地读取失败转 HTTP 兜底（对齐旧行为，避免条目直接丢失）；开流验证放在 putNextEntry 之前
                        try (java.io.InputStream in = java.nio.file.Files.newInputStream(ve.localFile.toPath())) {
                            java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(ve.entryName);
                            zos.putNextEntry(entry);
                            log.info("ZIP本地读取: {} ({}KB)", ve.localFile.getAbsolutePath(), ve.localFile.length() / 1024);
                            in.transferTo(zos);
                            zos.closeEntry();
                        } catch (Exception ce) {
                            viaHttp = true;
                            log.warn("ZIP本地读取失败，转HTTP兜底: {} - {}", ve.url, ce.getMessage());
                        }
                    }
                    if (viaHttp) {
                        java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(ve.entryName);
                        zos.putNextEntry(entry);
                        log.warn("ZIP走HTTP兜底: {}", ve.url);
                        rt.execute(httpUrl(ve.url), org.springframework.http.HttpMethod.GET, null,
                            (org.springframework.web.client.ResponseExtractor<Void>) clientHttpResponse -> {
                                org.springframework.util.StreamUtils.copy(clientHttpResponse.getBody(), zos);
                                return null;
                            });
                        zos.closeEntry();
                    }
                } catch (Exception e) {
                    log.warn("下载视频失败: {} - {}", ve.entryName, e.getMessage());
                }
            }
            zos.finish();
            zos.close();
            fos.close();
            tmpFile.renameTo(zipFile);
        }
        return zipFile;
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

    /** 手动触发蓝蛙拒绝二次复核卡片 E2（测试用）：与 trigger-avocado 同模式，只发 E2 */
    @PostMapping("/api/public/loss-report/trigger-avocado-recheck")
    @ResponseBody
    public Map<String, Object> triggerAvocadoRecheck() {
        com.xzcpc.job.LossReportDailySummaryJob job =
                new com.xzcpc.job.LossReportDailySummaryJob(jdbcTemplate, fms);
        String token = fms.getTenantToken();
        if (token == null) return Map.of("code", 500, "msg", "飞书token获取失败");
        try {
            var method = com.xzcpc.job.LossReportDailySummaryJob.class.getDeclaredMethod("sendCardLanwaRecheck", String.class, String.class);
            method.setAccessible(true);
            method.invoke(job, token, java.time.LocalDate.now().toString());
            return Map.of("code", 200, "msg", "已触发蓝蛙拒绝待复核卡片 E2");
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

    /** 手动触发出库失败告警卡片（测试用）：仅测试环境可用——必须配置 loss_outbound_alert_chat_id，防止生产误发个人 */
    @PostMapping("/api/public/loss-report/trigger-outbound-alert")
    @ResponseBody
    public Map<String, Object> triggerOutboundAlert() {
        String chatId = getConfig("loss_outbound_alert_chat_id", "");
        if (chatId.isEmpty()) {
            return Map.of("code", 500, "msg", "未配置 loss_outbound_alert_chat_id（测试环境限定，防止误发个人）");
        }
        List<Map<String, Object>> fake = new ArrayList<>();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("store_name", "测试门店-象子茶铺大理泰业二店");
        row.put("material_name", "冷冻牛油果泥(24包/件)");
        row.put("input_qty", new java.math.BigDecimal("2.0000"));
        row.put("input_unit", "包");
        row.put("id", 0L);
        fake.add(row);
        sendOutboundAlert(fake, "手动触发测试：模拟出库单创建失败");
        return Map.of("code", 200, "msg", "已发送测试告警卡片到群 " + chatId);
    }

    /** 测试用：把"其他类补发"卡片（B2 样式）发到测试群——链接指向本地 H5（test=1 禁止真实补发/企迈），仅查看样式 */
    @PostMapping("/api/public/loss-report/trigger-resend-card-test")
    @ResponseBody
    public Map<String, Object> triggerResendCardTest() {
        String chatId = getConfig("loss_outbound_alert_chat_id", "");
        if (chatId.isEmpty()) {
            return Map.of("code", 500, "msg", "未配置 loss_outbound_alert_chat_id（测试环境限定）");
        }
        String token = fms.getTenantToken();
        if (token == null) return Map.of("code", 500, "msg", "飞书token获取失败");
        try {
            String today = java.time.LocalDate.now().toString();
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT r.store_name, r.status FROM loss_report r " +
                    "LEFT JOIN material m ON r.material_id = m.material_id " +
                    "WHERE r.loss_type='arrival' AND r.status IN ('registered','confirmed_resend','received','not_received') AND r.del_flag=0");
            if (rows.isEmpty()) return Map.of("code", 500, "msg", "无补发数据（待补发/已补发均为空）");
            long pendingCnt = rows.stream().filter(r -> "registered".equals(r.get("status"))).count();
            long doneCnt = rows.size() - pendingCnt;
            int stores = (int) rows.stream().map(r -> r.get("store_name")).distinct().count();

            Map<String, Object> card = new LinkedHashMap<>();
            card.put("header", fms.cardHeader("green", "到货验收报损 · 补发 · 其他类"));
            List<Map<String, Object>> els = new ArrayList<>();
            els.add(fms.mdEl("**补发清单 其他类（本地测试）**\n待补发 **" + pendingCnt + "** 条 · 已补发 **" + doneCnt
                    + "** 条 · 涉及门店 **" + stores + "** 个"));
            els.add(fms.tagEl("hr"));
            String name = java.net.URLEncoder.encode("其他类", "UTF-8");
            els.add(fms.mdEl("[查看补发详情（本地测试 · test 模式不可真实补发）](http://localhost:4026/loss-daily-confirm.html?date="
                    + today + "&category=" + name + "&tab=resend&test=1)"));
            card.put("elements", els);
            fms.sendToChat(token, chatId, card);
            return Map.of("code", 200, "msg", "已发送补发测试卡片到群 " + chatId);
        } catch (Exception e) {
            return Map.of("code", 500, "msg", "发送失败: " + e.getMessage());
        }
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
                String gk = fms.resolveCategoryGroup(catGroupMap, matCat);
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

    /** 确认发券（单条）：registered → confirmed_resend，联动企迈建出库单。
     *  防重复：UPDATE 影响 0 行（已补发/状态非 registered）则跳过建单，避免重复扣库存 */
    @PostMapping("/api/public/loss-report/issue-voucher")
    public Map<String, Object> issueVoucher(@RequestBody Map<String, String> body) {
        long id = Long.parseLong(body.get("id"));
        int n = jdbcTemplate.update("UPDATE loss_report SET status='confirmed_resend', confirmed_at=? WHERE id=? AND status='registered'", LocalDateTime.now(), id);
        if (n == 0) {
            log.warn("LOSS_OUTBOUND 重复补发拦截 id={}（状态非 registered，跳过建单）", id);
            return Map.of("code", 200, "msg", "ok");
        }
        addLog(id, "issue_voucher", "厂家", null);
        try { createOutboundForLoss(List.of(id)); }
        catch (Exception e) { log.error("LOSS_OUTBOUND 单条建单异常 id={}", id, e); }
        return Map.of("code", 200, "msg", "ok");
    }

    /** 批量确认发券：registered → confirmed_resend，按仓库分组建企迈出库单（一仓一单）。
     *  防重复：只对本次真正从 registered 流转的记录建单，已补发过的自动跳过 */
    @SuppressWarnings("unchecked")
    @PostMapping("/api/public/loss-report/batch-issue-voucher")
    public Map<String, Object> batchIssueVoucher(@RequestBody Map<String, Object> body) {
        List<Map<String, String>> items = (List<Map<String, String>>) body.get("items");
        List<Long> ids = new ArrayList<>();
        for (Map<String, String> item : items) {
            long id = Long.parseLong(item.get("id"));
            int n = jdbcTemplate.update("UPDATE loss_report SET status='confirmed_resend', confirmed_at=? WHERE id=? AND status='registered'", LocalDateTime.now(), id);
            if (n == 0) {
                log.warn("LOSS_OUTBOUND 批量重复补发跳过 id={}（状态非 registered）", id);
                continue;
            }
            ids.add(id);
            addLog(id, "issue_voucher", "厂家", null);
        }
        if (!ids.isEmpty()) {
            try { createOutboundForLoss(ids); }
            catch (Exception e) { log.error("LOSS_OUTBOUND 批量建单异常 ids={}", ids, e); }
        }
        return Map.of("code", 200, "msg", "ok");
    }

    /** 出库单失败重试：对已 confirmed_resend 的行重新建单。
     *  insertOutboundOrder 为 upsert，覆盖原 failed 记录不撞唯一键；状态不变不重复扣库存。 */
    @PostMapping("/api/public/loss-report/retry-outbound")
    public Map<String, Object> retryOutbound(@RequestBody Map<String, String> body) {
        long id = Long.parseLong(body.get("id"));
        log.warn("LOSS_OUTBOUND 手动重试建单 id={}", id);
        try { createOutboundForLoss(List.of(id)); }
        catch (Exception e) { log.error("LOSS_OUTBOUND 重试建单异常 id={}", id, e); }
        return Map.of("code", 200, "msg", "ok");
    }

    // ==================== 补发联动企迈出库单（9.2.4 创建出库单） ====================

    /** 冷冻仓指定物料（名称包含即命中；与分类"冷冻类"合并判定） */
    private static final List<String> FROZEN_MATERIAL_KEYS = List.of("安佳淡奶油", "酸奶", "咸法干酪乳");

    /**
     * 补发联动建出库单：按物料归属仓库分组，每仓一张单（outboundType=29 其他出库、autoOutbound=1 立即扣库存）。
     * 不阻断补发：失败仅标记 outbound_status=failed + 飞书告警，由人工处理。
     */
    private void createOutboundForLoss(List<Long> reportIds) {
        if (reportIds == null || reportIds.isEmpty()) return;
        String ids = reportIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT r.id, r.material_id, r.input_qty, r.input_unit, r.store_name, " +
                "m.material_name, m.qm_code, m.category, ir.purchase_price, ir.purchase_unit, ir.stock_unit " +
                "FROM loss_report r " +
                "LEFT JOIN material m ON r.material_id = m.material_id " +
                "LEFT JOIN material_inventory_rule ir ON ir.material_id = r.material_id AND ir.del_flag = 0 " +
                "WHERE r.id IN (" + ids + ")");
        if (rows.isEmpty()) return;

        String whFrozen = getConfig("loss_outbound_wh_frozen", "");
        String whFresh = getConfig("loss_outbound_wh_fresh_milk", "");
        String whCentral = getConfig("loss_outbound_wh_central", "");
        if (whFrozen.isEmpty() && whFresh.isEmpty() && whCentral.isEmpty()) {
            log.warn("LOSS_OUTBOUND 未配置出库仓库编码（sys_config loss_outbound_wh_*），跳过建单 ids={}", ids);
            return;
        }

        // 按仓库分组（顺带校验单价、换算数量）
        Map<String, List<Map<String, Object>>> byWh = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            String wh = resolveOutboundWarehouse(r, whFrozen, whFresh, whCentral);
            if (wh == null || wh.isEmpty()) {
                markOutboundFailed(r, null, "未匹配出库仓库编码");
                continue;
            }
            if (r.get("purchase_price") == null) {
                markOutboundFailed(r, wh, "物料未配置采购单价（material_inventory_rule.purchase_price）");
                continue;
            }
            if (r.get("qm_code") == null || String.valueOf(r.get("qm_code")).isBlank()) {
                markOutboundFailed(r, wh, "物料缺少企迈编码 qm_code");
                continue;
            }
            BigDecimal outNum = convertQty(r);
            if (outNum == null) {
                // 换算失败：标记出库失败（不降级建单），本地补发状态不受影响，H5 已补发 tab 可重新补发
                markOutboundFailed(r, wh, "物料缺少换算链（" + r.get("input_unit") + " → " + r.get("stock_unit") + "），请在 material_inventory_rule 配置换算关系后重试");
                continue;
            }
            r.put("_outboundNum", outNum);
            byWh.computeIfAbsent(wh, k -> new ArrayList<>()).add(r);
        }

        long base = reportIds.get(0);
        int seq = 0;
        for (Map.Entry<String, List<Map<String, Object>>> e : byWh.entrySet()) {
            seq++;
            List<Map<String, Object>> group = e.getValue();
            String externalNo = "BF" + base + "-" + seq;
            List<Map<String, Object>> products = new ArrayList<>();
            for (Map<String, Object> r : group) {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("productCode", r.get("qm_code"));
                p.put("productName", r.get("material_name"));
                p.put("num", r.get("_outboundNum"));
                p.put("price", r.get("purchase_price"));
                products.add(p);
            }
            Map<String, Object> biz = new LinkedHashMap<>();
            biz.put("matchType", 1);
            biz.put("autoOutbound", 1);
            biz.put("bizId", externalNo);
            biz.put("externalNo", externalNo);
            biz.put("outboundType", 29);
            biz.put("warehouseNo", e.getKey());
            biz.put("remark", "补发出库");
            biz.put("productList", products);
            String groupIds = group.stream().map(r -> String.valueOf(r.get("id"))).collect(Collectors.joining(","));
            try {
                long outboundNo = qmaiClient.createOutboundOrder(biz);
                Long orderId = insertOutboundOrder(externalNo, e.getKey(), String.valueOf(outboundNo), "success", null);
                if (orderId != null) {
                    jdbcTemplate.update("UPDATE loss_report SET outbound_order_id=? WHERE id IN (" + groupIds + ")", orderId);
                }
                addLog(group.stream().map(r -> ((Number) r.get("id")).longValue()).min(Long::compareTo).orElse(base),
                        "outbound_create", "厂家", "出库单号 " + outboundNo);
                log.info("LOSS_OUTBOUND_OK externalNo={} outboundNo={} ids={}", externalNo, outboundNo, groupIds);
            } catch (Exception ex) {
                String err = ex.getMessage();
                if (err == null) err = ex.getClass().getSimpleName();
                if (err.length() > 490) err = err.substring(0, 490);
                Long orderId = insertOutboundOrder(externalNo, e.getKey(), null, "failed", err);
                if (orderId != null) {
                    jdbcTemplate.update("UPDATE loss_report SET outbound_order_id=? WHERE id IN (" + groupIds + ")", orderId);
                }
                log.error("LOSS_OUTBOUND_FAIL externalNo={} err={}", externalNo, err);
                sendOutboundAlert(group, err);
            }
        }
    }

    /** 物料归属仓库：冷冻（分类=冷冻类 或 指定物料）→ 冷冻仓；鲜奶类 → 鲜奶仓；其余 → 总仓 */
    private String resolveOutboundWarehouse(Map<String, Object> r, String whFrozen, String whFresh, String whCentral) {
        String cat = (String) r.get("category");
        String name = (String) r.get("material_name");
        if ("冷冻类".equals(cat)) return whFrozen;
        if (name != null) {
            for (String key : FROZEN_MATERIAL_KEYS) {
                if (name.contains(key)) return whFrozen;
            }
        }
        if ("鲜奶类".equals(cat)) return whFresh;
        return whCentral;
    }

    /** 报损数量（input_unit）→ 企迈库存单位数量（stock_unit，回退 purchase_unit）：material_conversion_rule 换算链。
     *  同单位返回原数量；换算失败（无换算链/换算异常/单位缺失）返回 null，调用方标记出库失败——不降级建单，避免数量单位错误 */
    private BigDecimal convertQty(Map<String, Object> r) {
        BigDecimal qty = toDecimal(r.get("input_qty"));
        if (qty == null) qty = BigDecimal.ZERO;
        String fromUnit = (String) r.get("input_unit");
        // 9.2.4 num 按库存单位理解：目标单位优先 stock_unit，其次 purchase_unit
        String toUnit = (String) r.get("stock_unit");
        if (!org.springframework.util.StringUtils.hasText(toUnit)) toUnit = (String) r.get("purchase_unit");
        if (fromUnit == null || toUnit == null) {
            log.warn("LOSS_OUTBOUND 单位缺失 input_unit={} stock_unit={} purchase_unit={} material={}，标记出库失败",
                    fromUnit, r.get("stock_unit"), r.get("purchase_unit"), r.get("material_name"));
            return null;
        }
        if (fromUnit.equals(toUnit)) return qty;
        try {
            // material_conversion_rule 无 material_id 列，按 rule_id 关联 material_inventory_rule 取物料换算链
            List<MaterialConversionRule> rules = jdbcTemplate.query(
                    "SELECT c.conversion_type, c.from_quantity, c.from_unit, c.to_quantity, c.to_unit " +
                    "FROM material_conversion_rule c " +
                    "JOIN material_inventory_rule ir ON ir.rule_id = c.rule_id " +
                    "WHERE ir.material_id = ? AND ir.del_flag = 0 AND c.del_flag = 0 " +
                    "ORDER BY c.sort_no",
                    (rs, i) -> {
                        MaterialConversionRule rule = new MaterialConversionRule();
                        rule.setConversionType(rs.getString("conversion_type"));
                        rule.setFromQuantity(rs.getBigDecimal("from_quantity"));
                        rule.setFromUnit(rs.getString("from_unit"));
                        rule.setToQuantity(rs.getBigDecimal("to_quantity"));
                        rule.setToUnit(rs.getString("to_unit"));
                        return rule;
                    }, r.get("material_id"));
            BigDecimal factor = ConversionFactorUtil.computeConversionFactor(fromUnit, toUnit, rules);
            if (factor != null) return qty.multiply(factor);
            log.warn("LOSS_OUTBOUND 无换算链 input_unit={} target_unit={} material={}，标记出库失败",
                    fromUnit, toUnit, r.get("material_name"));
        } catch (Exception e) {
            log.warn("LOSS_OUTBOUND 换算异常，标记出库失败: {}", e.getMessage());
        }
        return null;
    }

    /** 单条标记出库失败（未真正建单）+ 告警（记录仓库编码便于回看选仓） */
    private void markOutboundFailed(Map<String, Object> r, String warehouseNo, String reason) {
        String externalNo = "BF" + r.get("id") + "-0";
        Long orderId = insertOutboundOrder(externalNo, warehouseNo, null, "failed", reason);
        if (orderId != null) {
            jdbcTemplate.update("UPDATE loss_report SET outbound_order_id=? WHERE id=?", orderId, r.get("id"));
        }
        log.warn("LOSS_OUTBOUND_SKIP id={} wh={} reason={}", r.get("id"), warehouseNo, reason);
        sendOutboundAlert(List.of(r), reason);
    }

    /** 插入 outbound_order 记录并返回 id。
     *  upsert 按 external_no 覆盖：重试场景（上次建单失败已留 failed 记录）复用同一条记录更新，
     *  保持 loss_report.outbound_order_id 指向不变，避免唯一键冲突 */
    private Long insertOutboundOrder(String externalNo, String warehouseNo, String outboundNo,
                                     String status, String error) {
        int updated = jdbcTemplate.update(
                "UPDATE outbound_order SET warehouse_no=?, outbound_no=?, status=?, error=? WHERE external_no=?",
                warehouseNo, outboundNo, status, error, externalNo);
        if (updated > 0) {
            List<Long> ids = jdbcTemplate.queryForList(
                    "SELECT id FROM outbound_order WHERE external_no=? LIMIT 1", Long.class, externalNo);
            return ids.isEmpty() ? null : ids.get(0);
        }
        org.springframework.jdbc.support.KeyHolder kh = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbcTemplate.update(conn -> {
            java.sql.PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO outbound_order (external_no, warehouse_no, outbound_no, status, error) VALUES (?,?,?,?,?)",
                    java.sql.Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, externalNo);
            ps.setString(2, warehouseNo);
            ps.setString(3, outboundNo);
            ps.setString(4, status);
            ps.setString(5, error);
            return ps;
        }, kh);
        return kh.getKey() != null ? kh.getKey().longValue() : null;
    }

    /** 出库单创建失败 → 飞书告警（不阻断，人工处理）。测试环境优先发群：sys_config loss_outbound_alert_chat_id 有值则只发该群，不发个人 */
    private void sendOutboundAlert(List<Map<String, Object>> rows, String reason) {
        try {
            String token = fms.getTenantToken();
            if (token == null) { log.warn("无法获取飞书 token，出库失败告警跳过"); return; }
            String alertChatId = getConfig("loss_outbound_alert_chat_id", "");
            if (!alertChatId.isEmpty()) {
                sendOutboundAlertCard(token, alertChatId, true, rows, reason);
                return;
            }
            String userId = fms.getCardUserId("其他类", "other_resend");
            if (userId == null || userId.isEmpty()) { log.warn("未配置其他类补发收件人，出库失败告警跳过"); return; }
            sendOutboundAlertCard(token, userId, false, rows, reason);
        } catch (Exception e) {
            log.error("LOSS_OUTBOUND 告警发送异常", e);
        }
    }

    /** 组装并发送出库失败告警卡片：isChat=true 发群（chat_id），否则发个人（open_id） */
    private void sendOutboundAlertCard(String token, String target, boolean isChat,
                                       List<Map<String, Object>> rows, String reason) {
        StringBuilder sb = new StringBuilder("**补发出库单创建失败，请人工处理**\n\n");
        for (Map<String, Object> r : rows) {
            sb.append("· ").append(r.get("store_name")).append("｜").append(r.get("material_name"))
              .append(" ×").append(r.get("input_qty")).append(r.get("input_unit"))
              .append("（记录#").append(r.get("id")).append("）\n");
        }
        sb.append("\n**原因**：").append(reason);
        sb.append("\n\n请到企迈控制台核对出库情况，或检查物料采购单价/采购单位配置后重新补发。");
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("header", fms.cardHeader("red", "补发出库单创建失败"));
        List<Map<String, Object>> els = new ArrayList<>();
        els.add(fms.mdEl(sb.toString()));
        card.put("elements", els);
        if (isChat) {
            fms.sendToChat(token, target, card);
        } else {
            fms.sendToUser(token, target, card);
        }
    }

    private String getConfig(String key, String defaultValue) {
        try {
            String val = jdbcTemplate.queryForObject(
                    "SELECT config_value FROM sys_config WHERE config_key=? LIMIT 1", String.class, key);
            return (val != null && !val.isBlank()) ? val : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private BigDecimal toDecimal(Object v) {
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        if (v instanceof String s) {
            try { return new BigDecimal(s); } catch (Exception ignored) {}
        }
        return null;
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

    /** 手动触发每周门店操作预警（测试用） */
    @PostMapping("/api/public/loss-report/trigger-weekly-warning")
    @ResponseBody
    public Map<String, Object> triggerWeeklyWarning() {
        com.xzcpc.job.WeeklyStoreWarningJob job =
                new com.xzcpc.job.WeeklyStoreWarningJob(jdbcTemplate, fms);
        job.doSend();
        return Map.of("code", 200, "msg", "已触发每周门店操作预警");
    }

    /** 手动触发周期到货报损汇总卡片（测试用）：type=week 上周三~本周二；type=month 上月整月 */
    @PostMapping("/api/public/loss-report/trigger-period-summary")
    @ResponseBody
    public Map<String, Object> triggerPeriodSummary(@RequestParam(defaultValue = "week") String type) {
        com.xzcpc.job.LossReportPeriodSummaryJob job =
                new com.xzcpc.job.LossReportPeriodSummaryJob(jdbcTemplate, fms);
        if ("month".equals(type)) {
            job.doSendMonth();
            return Map.of("code", 200, "msg", "已触发月度到货报损汇总（上月整月）");
        }
        job.doSendWeek();
        return Map.of("code", 200, "msg", "已触发周度到货报损汇总（上周三~本周二）");
    }

    /**
     * 周期汇总卡「到象子掌柜查看详情」跳转中介。
     * 原因：卡片按钮经飞书 applink 打开时，lk_target_url 带 # 的 hash 路由（/#/loss?…）会被解析失败，
     * 飞书报「重定向 URL 有误」；改为按钮先指向本无 # 接口，服务端 302 到后台 hash 路由（相对 Location 同 host 生效）。
     */
    @GetMapping("/api/public/loss-report/loss-list-link")
    public void lossListLink(@RequestParam String startDate, @RequestParam String endDate,
                             @RequestParam(defaultValue = "arrival") String lossType,
                             jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        response.sendRedirect("/#/loss?startDate=" + startDate + "&endDate=" + endDate + "&lossType=" + lossType);
    }

    /**
     * 每周门店操作预警卡「查看支出明细」跳转中介。
     * 同 lossListLink：applink 不能带 # 直达 hash 路由，按钮先指向本无 # 接口，服务端 302 到 #/expense。
     * 不带 lossType/门店参数，落到支出列表后由督导在页内自行筛选门店核实。
     */
    @GetMapping("/api/public/weekly-warning/expense-list-link")
    public void weeklyWarningExpenseListLink(@RequestParam String startDate, @RequestParam String endDate,
                                             jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        response.sendRedirect("/#/expense?startDate=" + startDate + "&endDate=" + endDate);
    }

    /**
     * 每周门店操作预警卡「查看报损明细」跳转中介。
     * 报损页不传 lossType（区别于 lossListLink 默认 arrival），展示该周全部类型报损记录。
     */
    @GetMapping("/api/public/weekly-warning/loss-list-link")
    public void weeklyWarningLossListLink(@RequestParam String startDate, @RequestParam String endDate,
                                          jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        response.sendRedirect("/#/loss?startDate=" + startDate + "&endDate=" + endDate);
    }

    /**
     * 手动触发未验收问题提醒（测试用）。
     * 带 chatId 参数时只发指定门店群（按钮带 ?chatId= 过滤，仅该群可见本店问题）；
     * 不带参数时走完整逻辑（测试模式发测试群 / 门店模式发全部有问题的门店群）。
     */
    @PostMapping("/api/public/issue/trigger-acceptance-reminder")
    @ResponseBody
    public Map<String, Object> triggerAcceptanceReminder(@RequestParam(value = "chatId", required = false) String chatId) {
        com.xzcpc.job.IssueAcceptanceReminderJob job =
                new com.xzcpc.job.IssueAcceptanceReminderJob(jdbcTemplate, fms);
        if (chatId != null && !chatId.isBlank()) {
            job.doSendToGroup(chatId.trim());
            return Map.of("code", 200, "msg", "已触发未验收问题提醒（单群 " + chatId.trim() + "）");
        }
        job.doSend();
        return Map.of("code", 200, "msg", "已触发未验收问题提醒");
    }

    /**
     * 查询某日未验收问题提醒卡片的已读情况（每店取当天最后一次发送的消息）。
     * 飞书已读回执仅对机器人自身 7 天内发送的消息有效，超过 7 天查不到已读用户。
     * GET /api/public/issue/reminder-read?date=2026-08-26 （date 缺省为今天）
     */
    @GetMapping("/api/public/issue/reminder-read")
    @ResponseBody
    public Map<String, Object> reminderRead(@RequestParam(value = "date", required = false) String date) {
        String token = fms.getTenantToken();
        if (token == null) return Map.of("code", 500, "msg", "无法获取飞书 token");
        LocalDate d;
        try {
            d = (date == null || date.isBlank()) ? LocalDate.now() : LocalDate.parse(date);
        } catch (Exception e) {
            return Map.of("code", 400, "msg", "date 格式应为 yyyy-MM-dd，如 2026-08-26");
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT send_date, chat_id, store_name, message_id, issue_count, created_at " +
                "FROM issue_reminder_send_log WHERE send_date = ? ORDER BY created_at DESC", d);

        // 同一天可能多次发送，每店（chat_id）只取最新一条
        Map<String, Map<String, Object>> latestByChat = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            String chatId = String.valueOf(r.get("chat_id"));
            if (chatId != null && !latestByChat.containsKey(chatId)) latestByChat.put(chatId, r);
        }

        List<Map<String, Object>> stores = new ArrayList<>();
        int readTotal = 0;
        for (Map<String, Object> r : latestByChat.values()) {
            List<Map<String, Object>> readers = fms.getReadUsers(token, (String) r.get("message_id"));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("store_name", r.get("store_name"));
            item.put("issue_count", r.get("issue_count"));
            item.put("sent_at", String.valueOf(r.get("created_at")));
            item.put("read_count", readers.size());
            item.put("read_users", readers);
            readTotal += readers.size();
            stores.add(item);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("date", d.toString());
        data.put("store_count", stores.size());
        data.put("read_total", readTotal);
        data.put("stores", stores);
        return Map.of("code", 200, "msg", "ok", "data", data);
    }

    /** 未验收问题详情（供「未解决」H5 表单页展示） */
    @GetMapping("/api/public/issue/detail")
    @ResponseBody
    public Map<String, Object> issueDetail(@RequestParam Long id) {
        try {
            Issue issue = issueService.detailForAdmin(id);
            if (issue == null) return Map.of("code", 404, "msg", "问题不存在");
            return Map.of("code", 200, "msg", "ok", "data", Map.of(
                    "id", issue.getId(),
                    "title", issue.getTitle() == null ? "" : issue.getTitle(),
                    "issueType", issue.getIssueType() == null ? "" : issue.getIssueType(),
                    "storeName", issue.getStoreName() == null ? "" : issue.getStoreName(),
                    "status", issue.getStatus() == null ? "" : issue.getStatus(),
                    "createdAt", String.valueOf(issue.getCreatedAt())));
        } catch (Exception e) {
            log.error("未验收问题详情查询失败 id={}", id, e);
            return Map.of("code", 500, "msg", "查询失败");
        }
    }

    /**
     * 未解决提交：与小程序「门店确认未解决」同一链路 —— 先 store-confirm(reject)，再回复原因+附件。
     * 与 MpIssueController.storeConfirm + reply 保持一致，外部系统才能正确流转问题状态。
     */
    @PostMapping("/api/public/issue/unresolve")
    @ResponseBody
    public Map<String, Object> issueUnresolve(@RequestBody Map<String, Object> body) {
        try {
            long id = Long.parseLong(String.valueOf(body.getOrDefault("issueId", 0)));
            String reason = String.valueOf(body.getOrDefault("reason", "")).trim();
            String mediaUrls = String.valueOf(body.getOrDefault("mediaUrls", "")).trim();
            if (id <= 0) return Map.of("code", 400, "msg", "缺少问题ID");
            if (reason.isEmpty()) return Map.of("code", 400, "msg", "请填写未解决原因");
            // Step 1: 门店确认未解决（与小程序 storeConfirm(id,'reject') 同一外部接口）
            confirmStoreAction(id, "reject");
            // Step 2: 回复未解决原因 + 附件（与小程序 replyIssue 同一外部接口）
            issueService.replyExternal(id, reason, mediaUrls);
            log.info("飞书未解决提交：id={} reason={}", id, reason);
            return Map.of("code", 200, "msg", "已提交，问题将继续处理");
        } catch (BusinessException e) {
            return Map.of("code", 500, "msg", e.getMessage());
        } catch (Exception e) {
            log.error("飞书未解决提交失败", e);
            return Map.of("code", 500, "msg", "提交失败，请稍后重试");
        }
    }

    /** 门店确认（已解决/未解决）：代理外部 /api/store-issue/{xiangmuId}/store-confirm，action=accept|reject（与 MpIssueController.storeConfirm 同接口） */
    private void confirmStoreAction(long issueId, String action) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT xiangmu_id FROM issue WHERE id=? AND del_flag=0", issueId);
        String xiangmuId = rows.isEmpty() || rows.get(0).get("xiangmu_id") == null
                ? "" : String.valueOf(rows.get(0).get("xiangmu_id"));
        if (xiangmuId.isBlank()) {
            throw new BusinessException("未关联外部问题单");
        }
        String url = xiangmuBaseUrl + "/api/store-issue/" + xiangmuId + "/store-confirm";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> reqBody = Map.of("action", action);
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(reqBody, headers), Map.class);
            log.info("飞书 store-confirm：url={} action={} status={} body={}", url, action, resp.getStatusCode(), resp.getBody());
            if (!resp.getStatusCode().is2xxSuccessful()) {
                log.warn("飞书 store-confirm 非2xx：url={} body={}", url, resp.getBody());
                throw new BusinessException("外部服务返回异常，请稍后重试");
            }
        } catch (HttpStatusCodeException e) {
            log.error("飞书 store-confirm 外部拒绝 {}：url={} action={} resp={}", e.getStatusCode(), url, action, e.getResponseBodyAsString());
            throw new BusinessException("外部服务拒绝(" + e.getStatusCode().value() + ")，请稍后重试");
        } catch (ResourceAccessException e) {
            log.error("飞书 store-confirm 网络错误：url={} action={}", url, action, e);
            throw new BusinessException("外部服务暂时不可达，请稍后重试");
        }
    }

    /**
     * 待验收问题列表（供 H5 验收页展示；按时间倒序）。
     * 可选参数 chatId：门店飞书群 chat_id，传入时只返回该群对应门店的问题（卡片按门店群发送，H5 只展示本店问题）；
     * 不传则返回全部（测试模式/总部查看）。
     */
    @GetMapping("/api/public/issue/pending-list")
    @ResponseBody
    public Map<String, Object> issuePendingList(@RequestParam(value = "chatId", required = false) String chatId) {
        try {
            List<Map<String, Object>> rows;
            if (chatId != null && !chatId.isBlank()) {
                rows = jdbcTemplate.queryForList(
                        "SELECT i.id, i.store_id, i.store_name, i.title, i.issue_type, i.processed_by, i.process_result, i.updated_at, i.created_at " +
                        "FROM issue i JOIN store_info s ON i.store_id = s.store_id AND s.del_flag = 0 " +
                        "WHERE i.status = 'pending_acceptance' AND i.del_flag = 0 AND s.chat_id = ? ORDER BY i.created_at DESC",
                        chatId.trim());
            } else {
                rows = jdbcTemplate.queryForList(
                        "SELECT id, store_id, store_name, title, issue_type, processed_by, process_result, updated_at, created_at FROM issue " +
                        "WHERE status = 'pending_acceptance' AND del_flag = 0 ORDER BY created_at DESC");
            }
            List<Map<String, Object>> list = new ArrayList<>();
            for (Map<String, Object> r : rows) {
                Map<String, Object> it = new LinkedHashMap<>();
                it.put("id", r.get("id"));
                it.put("title", String.valueOf(r.getOrDefault("title", "")));
                it.put("issueType", String.valueOf(r.getOrDefault("issue_type", "")));
                it.put("storeName", String.valueOf(r.getOrDefault("store_name", "")));
                it.put("processedBy", String.valueOf(r.getOrDefault("processed_by", "")));
                it.put("processResult", String.valueOf(r.getOrDefault("process_result", "")));
                it.put("updatedAt", String.valueOf(r.getOrDefault("updated_at", "")));
                it.put("createdAt", String.valueOf(r.getOrDefault("created_at", "")));
                list.add(it);
            }
            return Map.of("code", 200, "msg", "ok", "data", list);
        } catch (Exception e) {
            log.error("待验收问题列表查询失败", e);
            return Map.of("code", 500, "msg", "查询失败");
        }
    }

    /** H5 验收上传图片：与门店小程序回复图片同一通道（代理外部系统 /api/store-issue/upload，chatId 取 issue 的 store_id） */
    @PostMapping("/api/public/issue/upload-media")
    @ResponseBody
    public Map<String, Object> issueUploadMedia(@RequestPart("files") org.springframework.web.multipart.MultipartFile file,
                                                @RequestParam(value = "issueId", defaultValue = "0") long issueId) {
        try {
            // chatId：小程序回复图片用门店 chat_id（store_info.chat_id，外部系统门店标识），
            // H5 这里通过 issue.store_id 关联查出来
            String chatId = "";
            if (issueId > 0) {
                try {
                    List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                            "SELECT s.chat_id FROM issue i " +
                            "LEFT JOIN store_info s ON i.store_id = s.store_id AND s.del_flag = 0 " +
                            "WHERE i.id=? AND i.del_flag=0", issueId);
                    if (!rows.isEmpty() && rows.get(0).get("chat_id") != null) {
                        chatId = String.valueOf(rows.get(0).get("chat_id"));
                    }
                } catch (Exception ignored) {}
            }
            java.nio.file.Path tmp = java.nio.file.Files.createTempFile("h5-issue-upload-", "-" + file.getOriginalFilename());
            try {
                file.transferTo(tmp.toFile());
                org.springframework.util.LinkedMultiValueMap<String, Object> map =
                        new org.springframework.util.LinkedMultiValueMap<>();
                map.add("files", new org.springframework.core.io.FileSystemResource(tmp.toFile()));
                map.add("chatId", chatId);
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.MULTIPART_FORM_DATA);
                HttpEntity<org.springframework.util.MultiValueMap<String, Object>> entity =
                        new HttpEntity<>(map, headers);
                String url = xiangmuBaseUrl + "/api/store-issue/upload";
                ResponseEntity<Map> resp = restTemplate.postForEntity(url, entity, Map.class);
                Map<String, Object> rb = resp.getBody();
                // 外部系统成功码是 0（code:0 = success），不是 200
                if (rb != null && 0 == ((Number) rb.getOrDefault("code", 0)).intValue()) {
                    Object data = rb.getOrDefault("data", rb);
                    return Map.of("code", 200, "data", data == null ? Map.of() : data);
                }
                log.warn("外部系统上传失败响应: {}", rb);
                return Map.of("code", 500, "msg", "上传失败");
            } finally {
                try { java.nio.file.Files.deleteIfExists(tmp); } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            log.error("H5 问题图片上传失败", e);
            return Map.of("code", 500, "msg", "上传失败，请稍后重试");
        }
    }

    /** H5 验收：与小程序「确认已解决」同一链路 —— 先 store-confirm(accept)，再本地 CLOSED */
    @PostMapping("/api/public/issue/accept")
    @ResponseBody
    public Map<String, Object> issueAccept(@RequestBody Map<String, Object> body) {
        try {
            long id = Long.parseLong(String.valueOf(body.getOrDefault("issueId", 0)));
            if (id <= 0) return Map.of("code", 400, "msg", "缺少问题ID");
            // Step 1: 门店确认已解决（与小程序 storeConfirm(id,'accept') 同一外部接口）
            confirmStoreAction(id, "accept");
            // Step 2: 本地状态 → CLOSED
            issueService.acceptFromFeishu(id);
            log.info("H5 验收通过：id={}", id);
            return Map.of("code", 200, "msg", "验收成功，问题已关闭");
        } catch (BusinessException e) {
            return Map.of("code", 500, "msg", e.getMessage());
        } catch (Exception e) {
            log.error("H5 验收失败 id={}", body.get("issueId"), e);
            return Map.of("code", 500, "msg", "验收失败，请稍后重试");
        }
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
