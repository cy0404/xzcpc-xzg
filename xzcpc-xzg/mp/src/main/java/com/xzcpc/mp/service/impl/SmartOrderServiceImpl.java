package com.xzcpc.mp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.common.exception.BusinessException;
import com.xzcpc.common.model.StoreInfo;
import com.xzcpc.common.util.BizCodeUtil;
import com.xzcpc.mp.client.QmaiClient;
import com.xzcpc.mp.context.UserContextHolder;
import com.xzcpc.mp.dto.SmartOrderAddItemReq;
import com.xzcpc.mp.dto.SmartOrderConfirmReq;
import com.xzcpc.mp.entity.InboundOrder;
import com.xzcpc.mp.entity.InboundOrderItem;
import com.xzcpc.mp.entity.MaterialOrderConstraint;
import com.xzcpc.mp.entity.SmartOrder;
import com.xzcpc.mp.entity.SmartOrderItem;
import com.xzcpc.mp.mapper.InboundOrderItemMapper;
import com.xzcpc.mp.mapper.InboundOrderMapper;
import com.xzcpc.mp.mapper.MaterialOrderConstraintMapper;
import com.xzcpc.mp.mapper.SmartOrderItemMapper;
import com.xzcpc.mp.mapper.SmartOrderMapper;
import com.xzcpc.mp.service.MpStaffService;
import com.xzcpc.mp.service.NotificationService;
import com.xzcpc.mp.service.SmartOrderService;
import com.xzcpc.mp.util.ConversionFactorUtil;
import com.xzcpc.task.entity.Task;
import com.xzcpc.task.entity.TaskMaterialSummary;
import com.xzcpc.task.mapper.TaskMapper;
import com.xzcpc.task.mapper.TaskMaterialSummaryMapper;
import com.xzcpc.task.service.StoreService;
import com.xzcpc.template.entity.Material;
import com.xzcpc.template.entity.MaterialConversionRule;
import com.xzcpc.template.entity.MaterialInventoryRule;
import com.xzcpc.template.mapper.MaterialConversionRuleMapper;
import com.xzcpc.template.mapper.MaterialInventoryRuleMapper;
import com.xzcpc.template.mapper.MaterialMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

/**
 * P1 + P2B: 智能订货
 *
 * 生成算法（2026-09 PG 失真期改造后）：
 * ① 需求预测：dailyUse × (cycleDays + safetyDays)；日均来源（库存差/客观口径优先）：
 *    库存差日均(盘点+到货倒推) → 订货节奏日均(店长按需订货≈真实消耗) → sys_config 默认日均
 *    PG 近4周日均仅在 smart_order_use_pg=1（企迈修复验收后）时参与互证：
 *    周盘与库存差偏差 ≤30% 取均值 blend，偏差 >30% 信库存差（PG 失真期低估 30~40%，倒推才是真实消耗）
 *    去年同周×趋势系数分支同样受 use_pg 开关控制（数据源：plan/2026-09-smart-order-source-plan.md）
 * ② 损耗修正：+ 近4周报损周均（loss_report，按物料×门店）
 * ③ 调货修正：± 近4周调货周均（transfer_order 净调出−调入，净调出 → 多订）
 * ④ 在途减项：− PG 未终态订单订货量（当前 PG 仅终态行 → 恒 0，配送 2 天可忽略）
 * ⑤ 建议 = max(0, 预测 + 损耗 ± 调货 − 当前库存 − 在途)，其中预测已含安全库存（沿用 P1 语义）
 * ⑥ 单位修正 suggest 从 base_unit 折算为 stock_unit，统一向上取整为整数（仓库按整件发货）
 * ⑦ 店长意图对照：建议量与店长近期单次订货量偏差 >30% → needs_review 提示确认
 * ⑧ 只入 suggest > 0 且企迈有编码、非半成品/淘汰类的物料
 * ② 损耗修正：+ 近4周报损周均（loss_report，按物料×门店）
 * ③ 调货修正：± 近4周调货周均（transfer_order 净调出−调入，净调出 → 多订）
 * ④ 在途减项：− Σ累计订货 + Σ累计到货（差值法，PG 全历史，不依赖 order_status）
 * ⑤ 建议 = max(0, 预测 + 损耗 ± 调货 − 当前库存 − 在途)，其中预测已含安全库存（沿用 P1 语义）
 * ⑥ 单位修正 suggest 从 base_unit 折算为 stock_unit，统一向上取整为整数（仓库按整件发货）
 * ⑦ 只入 suggest > 0 且企迈有编码、非半成品/淘汰类的物料
 *
 * 数据源（P2B）：
 * - 物料池与当前库存：本周周盘任务（task_type=weekly 且本周 task_week 已提交）的 task_material_summary；
 *   周盘未提交 → 跳过生成（先盘后订）
 * - 销量/在途：PG 库（warehouse_code = store_info.cangkuid，item_code = material.qm_code）
 * - 损耗/调货：自有 MySQL（loss_report / transfer_order(+item)，material_id = material.material_id 编码）
 * - 配送周期/安全天数：sys_config（预留企迈按店拉取）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmartOrderServiceImpl implements SmartOrderService {

    private final SmartOrderMapper orderMapper;
    private final SmartOrderItemMapper itemMapper;
    private final InboundOrderMapper inboundOrderMapper;
    private final InboundOrderItemMapper inboundOrderItemMapper;
    private final TaskMapper taskMapper;
    private final TaskMaterialSummaryMapper summaryMapper;
    private final MaterialMapper materialMapper;
    private final MaterialInventoryRuleMapper ruleMapper;
    private final MaterialOrderConstraintMapper orderConstraintMapper;
    private final MaterialConversionRuleMapper conversionMapper;
    private final StoreService storeService;
    private final QmaiClient qmaiClient;
    private final MpStaffService staffService;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final NotificationService notificationService;

    /** 企迈 PG 数据源（dwd.store_item_sales / purchase_order / purchase） */
    @Resource(name = "pgJdbcTemplate")
    private JdbcTemplate pgJdbc;

    private static final DateTimeFormatter ISO_WEEK_FMT =
            DateTimeFormatter.ofPattern("YYYY-'W'ww", Locale.ROOT);

    private static final String CFG_CYCLE_DAYS = "smart_order_delivery_cycle_days";
    private static final String CFG_SAFETY_DAYS = "smart_order_safety_days";
    private static final String CFG_DEFAULT_DAILY_USE = "smart_order_default_daily_use";
    private static final String CFG_ONLINE_PAY = "smart_order_online_pay";
    private static final String CFG_DEADLINE_TIME = "smart_order_deadline_time";
    /** 企迈总仓仓库编码（9.2.13 实时库存查询用，逗号分隔最多 5 个，空=不查库存） */
    private static final String CFG_CENTRAL_WAREHOUSE_NO = "smart_order_central_warehouse_no";
    /** PG 销量预测源开关：企迈 PG 销量失真期（2026-09 验证系统性低估 30~40%）默认 0=关闭（只用库存差/订货节奏）；
     *  企迈侧数据修复且按 plan/2026-09-smart-order-source-plan.md 验收达标后置 1 切回 PG 互证。 */
    private static final String CFG_USE_PG = "smart_order_use_pg";
    /** 理论消耗日均候选源（costcard 域 store_material_consume_daily，销售×成本卡算得） */
    private static final String CFG_USE_CONSUME = "smart_order_use_consume";
    /** 店长手动订货抑制窗口天数（2026-09-09）：窗口内店长在企迈手动下过单（source=1）且系统无 PG 订货节奏
     *  （低频手动管理料，如南姜/香茅/鲜果）→ 本次不重复建议，避免"刚订过又让订" */
    private static final String CFG_MANUAL_SUPPRESS_DAYS = "smart_order_manual_suppress_days";

    /** 拆单批次：1=首批（周盘提交即触发，库存=实盘）；2=次批（第二订货日自动生成，库存=估算值） */
    private static final int BATCH_1 = 1;
    private static final int BATCH_2 = 2;

    // ==================== 生成 ====================

    @Override
    public Map<String, Object> generateAll() {
        List<StoreInfo> stores = storeService.getAllStores();
        int generated = 0, skipped = 0;
        List<String> failedStores = new ArrayList<>();
        for (StoreInfo s : stores) {
            // 未配置订货周期（store_order_cycle 无记录）或暂停周盘的门店不参与
            if (!StringUtils.hasText(s.getOrderDays())) { skipped++; continue; }
            if (s.getWeeklyPaused() != null && s.getWeeklyPaused() == 1) { skipped++; continue; }
            try {
                if (generateForStore(s, BATCH_1)) generated++;
                else skipped++;
            } catch (Exception e) {
                log.error("SMART_ORDER_GEN 门店生成失败 storeId={}: {}", s.getId(), e.getMessage(), e);
                failedStores.add(s.getId());
            }
        }
        log.info("SMART_ORDER_GEN done: generated={} skipped={} failed={}", generated, skipped, failedStores.size());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("generated", generated);
        result.put("skipped", skipped);
        result.put("failedStores", failedStores);
        return result;
    }

    /**
     * 为单门店生成某批建议单（每日扫描用）：周盘任务已提交才生成（先盘后订，决策#8），返回是否新建。
     * 批1=周盘提交后的首个订货日（现有 3:00 job / 提交补触发）；批2=第二订货日 9:00 job（generateSecondBatchAll）触发。
     */
    private boolean generateForStore(StoreInfo store, int batchNo) {
        LocalDate today = LocalDate.now();
        // 周盘任务定位：
        // 批1 = 本周（当前 ISO 周）已提交的周盘任务——盘点锚定首个订货日，批1 与盘点同周；
        // 批2 = 最近 8 天内已提交的周盘任务——'3,7' 店批2 周日与盘点同周（周二盘）；
        //      '7,3' 店（周日盘、周三批2）批2 落在盘点周的下一个 ISO 周，须跨周找上周六盘的任务；
        //      超 8 天说明该盘点周期未盘（先盘后订，不生成）
        Task weekly;
        if (batchNo == BATCH_2) {
            weekly = taskMapper.selectOne(new LambdaQueryWrapper<Task>()
                    .eq(Task::getStoreId, store.getId())
                    .eq(Task::getTaskType, "weekly")
                    .eq(Task::getStatus, "submitted")
                    .ge(Task::getSubmittedAt, LocalDateTime.now().minusDays(8))
                    .orderByDesc(Task::getSubmittedAt)
                    .last("LIMIT 1"));
        } else {
            String taskWeek = ISO_WEEK_FMT.format(today);
            weekly = taskMapper.selectOne(new LambdaQueryWrapper<Task>()
                    .eq(Task::getStoreId, store.getId())
                    .eq(Task::getTaskType, "weekly")
                    .eq(Task::getTaskWeek, taskWeek)
                    .eq(Task::getStatus, "submitted")
                    .orderByDesc(Task::getSubmittedAt)
                    .last("LIMIT 1"));
        }
        if (weekly == null) {
            log.info("SMART_ORDER_GEN storeId={} batch={} 本周周盘未提交，跳过生成", store.getId(), batchNo);
            return false;
        }
        List<TaskMaterialSummary> summaries = summaryMapper.selectList(
                new LambdaQueryWrapper<TaskMaterialSummary>().eq(TaskMaterialSummary::getTaskId, weekly.getId()));
        if (summaries.isEmpty()) {
            log.info("SMART_ORDER_GEN storeId={} batch={} 本周周盘无盘点物料，跳过生成", store.getId(), batchNo);
            return false;
        }
        return buildOrder(store, weekly, summaries, batchNo);
    }

    /**
     * 批2 全店扫描（第二订货日 9:00 job 调用）：仅在「今天是本店推导的第二订货日」时生成。
     * 第二订货日 = (首个订货日 + 3) % 7 + 1（7 天周期拆 4+3；试点店 order_days='3,7' → 周三订 → 周日订）。
     */
    @Override
    public Map<String, Object> generateSecondBatchAll() {
        List<StoreInfo> stores = storeService.getAllStores();
        int generated = 0, skipped = 0;
        List<String> failedStores = new ArrayList<>();
        int today = LocalDate.now().getDayOfWeek().getValue(); // 1=周一 … 7=周日（与 order_days 同制）
        for (StoreInfo s : stores) {
            // 未配置订货周期（store_order_cycle 无记录）或暂停周盘的门店不参与
            if (!StringUtils.hasText(s.getOrderDays())) { skipped++; continue; }
            if (s.getWeeklyPaused() != null && s.getWeeklyPaused() == 1) { skipped++; continue; }
            if (secondOrderDayOf(s) != today) { skipped++; continue; }
            try {
                if (generateForStore(s, BATCH_2)) generated++;
                else skipped++;
            } catch (Exception e) {
                log.error("SMART_ORDER_GEN 批2门店生成失败 storeId={}: {}", s.getId(), e.getMessage(), e);
                failedStores.add(s.getId());
            }
        }
        log.info("SMART_ORDER_GEN 批2 done: generated={} skipped={} failed={}", generated, skipped, failedStores.size());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("generated", generated);
        result.put("skipped", skipped);
        result.put("failedStores", failedStores);
        return result;
    }

    /** 周盘任务提交后补触发（afterCommit 调用）：直接以已提交任务为物料池生成批1，返回是否新建。 */
    @Override
    public boolean generateByWeeklyTask(Integer taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null || !"submitted".equals(task.getStatus())
                || !"weekly".equals(task.getTaskType())) {
            log.info("SMART_ORDER_GEN 跳过：taskId={} 非已提交周盘任务", taskId);
            return false;
        }
        StoreInfo store = storeService.getStoreById(task.getStoreId());
        if (store == null) {
            log.warn("SMART_ORDER_GEN 跳过：taskId={} 门店不存在", taskId);
            return false;
        }
        List<TaskMaterialSummary> summaries = summaryMapper.selectList(
                new LambdaQueryWrapper<TaskMaterialSummary>().eq(TaskMaterialSummary::getTaskId, task.getId()));
        if (summaries.isEmpty()) {
            log.info("SMART_ORDER_GEN taskId={} 周盘无盘点物料，跳过生成", taskId);
            return false;
        }
        return buildOrder(store, task, summaries, BATCH_1);
    }

    /**
     * P2B 回测：以历史周为基准"模拟当时生成"（不落库），与窗口内实际消耗对比评估预测准确性。
     * weekStart 即模拟生成日（通常为某周一）；actual 窗口 = [weekStart, weekStart + cycleDays+safetyDays)，
     * actual = 已送达订货量 + 报损 + 调出−调入 + 还出−收回 − 自购（窗口内合计，不 ÷4）。
     * 2026-09 PG 失真期起：actual 以"窗口内已送达订货"为主口径（店长按需订货、配送 2 天，
     * 订货≈真实消耗，验证贴合度中位 12.9%），PG 销量仅作无订货记录时的兜底。
     * 输出全量物料对比（含未入单物料 → 暴露漏订），及偏差率统计（|suggest−actual| / actual）。
     */
    @Override
    public Map<String, Object> backtest(Integer taskId, LocalDate weekStart) {
        Map<String, Object> result = new LinkedHashMap<>();
        Task task = taskMapper.selectById(taskId);
        if (task == null) throw new BusinessException(4040, "任务不存在");
        if (!"submitted".equals(task.getStatus())) throw new BusinessException(4032, "任务未提交，无法回测");
        StoreInfo store = storeService.getStoreById(task.getStoreId());
        if (store == null) throw new BusinessException(4040, "门店不存在");
        List<TaskMaterialSummary> summaries = summaryMapper.selectList(
                new LambdaQueryWrapper<TaskMaterialSummary>().eq(TaskMaterialSummary::getTaskId, task.getId()));
        if (summaries.isEmpty()) throw new BusinessException(4032, "任务无盘点物料");

        int cycleDays = parseInt(getConfig(CFG_CYCLE_DAYS, "7"), 7);
        int safetyDays = parseInt(getConfig(CFG_SAFETY_DAYS, "3"), 3);
        LocalDate end = weekStart.plusDays(cycleDays + safetyDays); // 实际消耗窗口与预测周期对齐
        MaterialCtx ctx = loadMaterialContext(summaries);

        // ① 模拟生成：与正式生成同款计算（computeItems 不落库），weekStart 作为生成基准日；
        //    predictOut 收集每个物料（含未入单）的预测上下文，用于日均预测 vs 日均实际对比
        Map<String, Map<String, Object>> predictOut = new HashMap<>();
        List<SmartOrderItem> items = computeItems(store, task, summaries, weekStart, 0, predictOut);
        Map<Long, SmartOrderItem> itemByMid = items.stream()
                .collect(Collectors.toMap(SmartOrderItem::getMaterialId, i -> i, (a, b) -> a));

        // ② 窗口内实际消耗（只统计本次任务物料池的）
        List<String> midList = summaries.stream().map(TaskMaterialSummary::getMaterialId)
                .filter(Objects::nonNull).distinct().toList();
        Map<String, PgSales> pgMap = pgSalesSum(store, weekStart, end, ctx.materialMap(), ctx.ruleMap(), ctx.convMap());
        Map<String, BigDecimal> lossMap = lossInWindow(store.getId(), midList, weekStart, end);
        Map<String, BigDecimal> transferMap = transferInWindow(store.getId(), midList, weekStart, end);
        Map<String, BigDecimal> returnMap = returnInWindow(store.getId(), midList, weekStart, end, ctx.ruleMap(), ctx.convMap());
        Map<String, BigDecimal> selfPurchaseMap = selfPurchaseInWindow(store.getId(), midList, weekStart, end, ctx.ruleMap(), ctx.convMap());
        Map<String, BigDecimal> orderMap = orderInWindow(store, weekStart, end, ctx.materialMap(), ctx.ruleMap(), ctx.convMap()); // 耗材需求代理

        // ③ 全量物料对比（未入单物料也输出 → 暴露漏订）
        int windowDays = cycleDays + safetyDays;
        List<Map<String, Object>> records = new ArrayList<>();
        List<BigDecimal> errors = new ArrayList<>();      // 决策偏差：|suggest − actual| / actual
        List<BigDecimal> predErrors = new ArrayList<>();  // 预测偏差：|predictedDaily − actualDaily| / actualDaily
        int compared = 0, within30 = 0, missed = 0, conservative = 0, stopped = 0,
                correctSkip = 0, stoppedMissed = 0;
        int predCompared = 0, predWithin30 = 0;
        for (TaskMaterialSummary sum : summaries) {
            Material material = ctx.materialMap().get(sum.getMaterialId());
            if (material == null) continue;
            String matCategory = material.getCategory();
            if (matCategory != null && (matCategory.contains("半成品") || matCategory.contains("淘汰"))) continue;
            String mid = sum.getMaterialId();
            PgSales pgSale = pgMap.getOrDefault(mid, new PgSales(BigDecimal.ZERO, 0));
            BigDecimal pg = pgSale.qty();
            BigDecimal orderActual = orderMap.getOrDefault(mid, BigDecimal.ZERO);
            BigDecimal loss = lossMap.getOrDefault(mid + "|" + store.getId(), BigDecimal.ZERO);
            BigDecimal transfer = transferMap.getOrDefault(mid + "|" + store.getId(), BigDecimal.ZERO);
            BigDecimal ret = returnMap.getOrDefault(mid + "|" + store.getId(), BigDecimal.ZERO);
            BigDecimal sp = selfPurchaseMap.getOrDefault(mid + "|" + store.getId(), BigDecimal.ZERO);
            // PG 失真期口径：actual 以窗口内已送达订货量为主（店长按需订货≈真实消耗，验证中位贴合 12.9%）；
            // 窗口内无订货（低频长周期物料恰未到货）才回退 PG 销量兜底
            BigDecimal actual = orderActual.compareTo(BigDecimal.ZERO) > 0
                    ? orderActual.add(loss).add(transfer).add(ret).subtract(sp)
                    : pg.add(loss).add(transfer).add(ret).subtract(sp);
            SmartOrderItem item = itemByMid.get(material.getId());
            BigDecimal suggest = item != null ? item.getSuggestQty() : BigDecimal.ZERO;
            Map<String, Object> trace = predictOut.get(mid);
            if (actual.compareTo(BigDecimal.ZERO) > 0) {
                compared++;
                if (trace != null && "stopped".equals(trace.get("useMode"))) {
                    stoppedMissed++; // 停售拦截但窗口内实际有消耗 → 停售误判（如停售两周后重启销售）
                } else {
                    BigDecimal base = trace != null && trace.get("base") != null
                            ? (BigDecimal) trace.get("base") : null;
                    if (base != null && base.compareTo(BigDecimal.ZERO) <= 0) {
                        correctSkip++; // 需求≤库存，正确不订（库存充足，不算漏订）
                    } else if (suggest.compareTo(BigDecimal.ZERO) <= 0) {
                        missed++;   // 需求>库存却未入单 → 真漏订
                    } else {
                        // 单位对齐：suggest 是库存单位（件/包），actual 是基础单位（ml/g/根），换算后再对比
                        BigDecimal factor = trace != null && trace.get("factor") != null
                                ? (BigDecimal) trace.get("factor") : null;
                        BigDecimal actualInUnit = factor != null && factor.compareTo(BigDecimal.ONE) > 0
                                ? actual.divide(factor, 6, RoundingMode.HALF_UP) : actual;
                        BigDecimal diff = suggest.subtract(actualInUnit).abs();
                        BigDecimal rate = diff.divide(actualInUnit, 4, RoundingMode.HALF_UP);
                        errors.add(rate);
                        if (rate.compareTo(BigDecimal.valueOf(0.3)) <= 0) within30++;
                    }
                }
            } else if (item != null) {
                conservative++; // 实际无消耗但预测入单 → 保守多备
            }
            if (trace != null && "stopped".equals(trace.get("useMode"))) stopped++;
            BigDecimal predictedDaily = trace != null ? (BigDecimal) trace.get("dailyUse") : null;
            BigDecimal demand = trace != null ? (BigDecimal) trace.get("demand") : null;
            BigDecimal base = trace != null ? (BigDecimal) trace.get("base") : null;
            if (predictedDaily != null && actual.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal actualDaily = actual.divide(BigDecimal.valueOf(windowDays), 6, RoundingMode.HALF_UP);
                BigDecimal pd = predictedDaily.compareTo(BigDecimal.ZERO) > 0 ? predictedDaily : BigDecimal.ZERO;
                BigDecimal prate = pd.subtract(actualDaily).abs().divide(actualDaily, 4, RoundingMode.HALF_UP);
                predErrors.add(prate);
                predCompared++;
                if (prate.compareTo(BigDecimal.valueOf(0.3)) <= 0) predWithin30++;
            }
            Map<String, Object> rec = new HashMap<>();
            rec.put("materialName", sum.getMaterialName());
            rec.put("mid", mid);
            rec.put("category", material.getCategory());
            rec.put("qmCode", material.getQmCode());
            rec.put("currentInventory", sum.getTotalQty() != null ? sum.getTotalQty() : BigDecimal.ZERO);
            rec.put("predictedDaily", predictedDaily);
            rec.put("demand", demand);
            rec.put("base", base);
            rec.put("factor", trace != null ? trace.get("factor") : null);
            rec.put("useMode", trace != null ? trace.get("useMode") : null);
            rec.put("suggest", suggest);
            rec.put("actual", actual);
            rec.put("pgSales", pg);
            rec.put("orderActual", orderActual); // 耗材需求代理：窗口内已送达订货量
            rec.put("loss", loss);
            rec.put("transfer", transfer);
            rec.put("return", ret);
            rec.put("selfPurchase", sp);
            rec.put("inTransit", item != null ? item.getInTransitQty() : null);
            rec.put("inOrder", item != null);
            rec.put("reason", trace != null && trace.get("reason") != null
                    ? String.valueOf(trace.get("reason")) : "未入单");
            records.add(rec);
        }
        BigDecimal avgDeviation = errors.isEmpty() ? null : errors.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(errors.size()), 4, RoundingMode.HALF_UP);
        BigDecimal avgPredDeviation = predErrors.isEmpty() ? null
                : predErrors.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(predErrors.size()), 4, RoundingMode.HALF_UP);

        result.put("taskId", taskId);
        result.put("storeId", store.getId());
        result.put("storeName", store.getMendianmingcheng());
        result.put("weekStart", weekStart.toString());
        result.put("actualWindow", "[" + weekStart + ", " + end + ")");
        result.put("suggestCount", items.size());
        Map<String, Object> stats = new HashMap<>();
        int effectiveCompared = Math.max(0, compared - correctSkip - stoppedMissed); // 可评估基数：排除正确不订与停售误判
        stats.put("compared", compared);
        stats.put("within30pct", within30);
        stats.put("within30pctRate", effectiveCompared == 0 ? null
                : BigDecimal.valueOf(within30).divide(BigDecimal.valueOf(effectiveCompared), 4, RoundingMode.HALF_UP));
        stats.put("missed", missed);
        stats.put("correctSkip", correctSkip);      // 库存充足正确不订（需求≤库存）
        stats.put("stoppedMissed", stoppedMissed);  // 停售拦截但窗口内实际有消耗 → 停售误判
        stats.put("conservative", conservative);
        stats.put("stopped", stopped);
        stats.put("avgDeviation", avgDeviation);
        stats.put("predCompared", predCompared);
        stats.put("predWithin30pct", predWithin30);
        stats.put("predWithin30pctRate", predCompared == 0 ? null
                : BigDecimal.valueOf(predWithin30).divide(BigDecimal.valueOf(predCompared), 4, RoundingMode.HALF_UP));
        stats.put("avgPredDeviation", avgPredDeviation);
        result.put("stats", stats);
        result.put("records", records);
        return result;
    }

    /**
     * 核心生成引擎：以周盘任务快照为物料池 + 当前库存，按预测公式生成某批建议单，返回是否新建。
     * 批1=首个订货日（周盘 deadline 当天）；批2=第二订货日（orderDay2 = orderDay1 + 4，当周推导）。
     */
    private boolean buildOrder(StoreInfo store, Task current, List<TaskMaterialSummary> curSummaries, int batchNo) {
        LocalDate today = LocalDate.now();
        // 周基准 = 来源周盘任务所在周（同周两批共用同一 week_start_date）；
        // '7,3' 型门店（周日盘）批2 周三已落下一个 ISO 周——按生成日取周一会把批2 错标到下一周，
        // 且与批1 的预测窗口（weekStart 锚定）不一致
        LocalDate weekStart = weekStartOfTask(current);
        int orderDay = batchNo == BATCH_2 ? secondOrderDayOf(store) : firstOrderDayOf(store);

        // 幂等：同店同周盘任务同订货日已生成过则跳过（UNIQUE KEY uk_store_week_batch 兜底并发）；
        // 批1 与批2 orderDay 不同 → 同周可生成两张单
        Long existing = orderMapper.selectCount(new LambdaQueryWrapper<SmartOrder>()
                .eq(SmartOrder::getStoreId, store.getId())
                .eq(SmartOrder::getTaskId, current.getId())
                .eq(SmartOrder::getOrderDay, orderDay));
        if (existing != null && existing > 0) {
            log.info("SMART_ORDER_GEN storeId={} taskId={} batch={} 该订货日已生成过，跳过", store.getId(), current.getId(), batchNo);
            return false;
        }

        List<SmartOrderItem> items = computeItems(store, current, curSummaries, weekStart, batchNo, null);
        if (items.isEmpty()) {
            log.info("SMART_ORDER_GEN storeId={} batch={} 无需要补货的物料，跳过生成", store.getId(), batchNo);
            return false;
        }
        // 截止日：批1 = 周盘 deadline 当天（订货日）；批2 = 生成当天（第二订货日）
        LocalDate deadlineDate = batchNo == BATCH_2 ? today
                : (current.getDeadline() != null ? current.getDeadline().toLocalDate() : weekStart);
        LocalDate countDate = current.getSubmittedAt() != null ? current.getSubmittedAt().toLocalDate() : null;
        return persistOrder(store, weekStart, items, current.getId().longValue(), deadlineDate,
                batchNo, orderDay, countDate);
    }

    /** 解析 order_days（1-7 逗号分隔，1=周一）为有序去重列表；无合法值抛业务异常 */
    private static List<Integer> orderDaysOf(StoreInfo store) {
        List<Integer> list = new ArrayList<>();
        if (StringUtils.hasText(store.getOrderDays())) {
            for (String s : store.getOrderDays().split(",")) {
                try {
                    int d = Integer.parseInt(s.trim());
                    if (d >= 1 && d <= 7 && !list.contains(d)) list.add(d);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (list.isEmpty()) throw new BusinessException("门店未配置订货周期（order_days），无法拆单");
        return list;
    }

    /** 首个订货日 = order_days 首值（拆单后仅首个订货日盘点、批1 下单） */
    private static int firstOrderDayOf(StoreInfo store) {
        return orderDaysOf(store).get(0);
    }

    /**
     * 第二订货日：order_days 有第二值取实际值（试点 '3,7' → 周日 7，按各店订货周期拆分）；
     * 单值店按 +4 推导 (d+3)%7+1（7 天周期拆 4+3 的默认假定，跨周回绕）。
     */
    private static int secondOrderDayOf(StoreInfo store) {
        List<Integer> days = orderDaysOf(store);
        if (days.size() >= 2) return days.get(1);
        return (days.get(0) + 3) % 7 + 1;
    }

    /**
     * 批次覆盖销售天数：按本店两个订货日的实际间隔切分（不写死 4/3）——
     * 批1 = 首→次订货日间隔 gap（如 '3,7' → 4 天：周三~周六）；批2 = 7 − gap（3 天：周日~下周二）。
     * 若某店间隔 3 天（如 '1,4'）→ 批1=3 天、批2=4 天。单值店 +4 推导 → gap=4（4+3）。
     */
    private static int coverageDaysOf(StoreInfo store, int batchNo) {
        int d1 = firstOrderDayOf(store);
        int d2 = secondOrderDayOf(store);
        int gap = (d2 - d1 + 7) % 7;
        if (gap == 0) gap = 7; // 防御：两订货日同天（配置异常）按整周处理
        return batchNo == BATCH_1 ? gap : 7 - gap;
    }

    /** 解析周标签 YYYY-Www → 该周周一（与 TaskServiceImpl.parseWeekStart 同口径，ISO 周制）；
     *  解析失败回退本周一（正常不会发生） */
    private static LocalDate weekStartOfTask(Task task) {
        if (task != null && StringUtils.hasText(task.getTaskWeek())) {
            try {
                DateTimeFormatter fmt = new DateTimeFormatterBuilder()
                        .appendPattern("YYYY-'W'ww")
                        .parseDefaulting(ChronoField.DAY_OF_WEEK, 1)
                        .toFormatter();
                return LocalDate.parse(task.getTaskWeek(), fmt);
            } catch (Exception ignored) {
            }
        }
        return LocalDate.now().with(DayOfWeek.MONDAY);
    }

    /**
     * 预测计算核心（不落库）：以任务快照为物料池 + 当前库存，按预测公式计算建议明细。
     * weekStart 决定全部预测窗口（损耗近4周 / PG 近4周·去年同周 / 在途），回测时传历史周模拟"当时生成"。
     * batchNo：0=回测/整周口径（cycleDays 走 sys_config）；1=批1（4 天量）；2=批2（3 天量，库存基准=估算值）。
     * predictOut 非空时（回测），把每个物料的预测上下文（dailyUse/useMode/demand/base 等，含未入单物料）写入，
     * 供回测对比预测日均 vs 实际日均。
     */
    private List<SmartOrderItem> computeItems(StoreInfo store, Task current, List<TaskMaterialSummary> curSummaries,
                                              LocalDate weekStart, int batchNo,
                                              Map<String, Map<String, Object>> predictOut) {
        // 库存差法降级所需：上一次已提交盘点任务（周盘或月度均可）。
        // 按「生成基准时刻」过滤：正式生成 = 当前任务提交时刻；回测 = 模拟生成日 weekStart。
        // 不加过滤时回测会被 weekStart 之后提交的任务污染（如 8/7 测试任务抢走 7/31 月盘的 prev 位）；
        // 回测传"已提交任务"作物料池时（如 8/31 月盘模拟 9/7 生成），prev 不能选到 current 自己——
        // 否则 daysBetween=0、库存差不可用，回测永远落 PG 链（2026-09 修复，验证三源融合的前提）
        LocalDateTime prevBefore = predictOut != null
                ? weekStart.atStartOfDay()
                : (current.getSubmittedAt() != null ? current.getSubmittedAt() : LocalDateTime.now());
        Task prev = taskMapper.selectOne(new LambdaQueryWrapper<Task>()
                .eq(Task::getStoreId, store.getId())
                .eq(Task::getStatus, "submitted")
                .ne(current.getId() != null, Task::getId, current.getId())
                .lt(Task::getSubmittedAt, prevBefore)
                .orderByDesc(Task::getSubmittedAt)
                .last("LIMIT 1"));
        Map<String, BigDecimal> prevQtyMap = new HashMap<>();
        if (prev != null) {
            for (TaskMaterialSummary s : summaryMapper.selectList(
                    new LambdaQueryWrapper<TaskMaterialSummary>().eq(TaskMaterialSummary::getTaskId, prev.getId()))) {
                if (s.getTotalQty() != null) prevQtyMap.put(s.getMaterialId(), s.getTotalQty());
            }
        }

        // 批量加载物料/规则/换算链（注意：summary.materialId 是 VARCHAR 外部编码，material.id 是 BIGINT 自增主键）
        MaterialCtx ctx = loadMaterialContext(curSummaries);
        Map<String, Material> materialMap = ctx.materialMap();
        Map<String, MaterialInventoryRule> ruleMap = ctx.ruleMap();
        Map<String, List<MaterialConversionRule>> convMap = ctx.convMap();
        Map<String, MaterialOrderConstraint> constraintMap = ctx.constraintMap();

        // P2B 预测数据：PG 销量（去年同周/近4周/去年同4周）+ 在途（累计订货-累计到货差值法）+ 损耗/调货/自购修正
        Map<String, PgSales> lastYearMap = pgSalesSum(store, weekStart.minusWeeks(52), weekStart.minusWeeks(51), materialMap, ruleMap, convMap);
        Map<String, PgSales> recent4wMap = pgSalesSum(store, weekStart.minusWeeks(4), weekStart, materialMap, ruleMap, convMap);
        Map<String, PgSales> lastYear4wMap = pgSalesSum(store, weekStart.minusWeeks(56), weekStart.minusWeeks(52), materialMap, ruleMap, convMap);
        // 停售检测：近7天 [weekStart-7, weekStart) PG 有销量记录的 qm_code 集合（有库存但不再动销 → 疑似停售）
        Set<String> recentSold = recentSoldQmCodes(store, weekStart, materialMap);
        Set<String> historicalSold = historicalSoldQmCodes(store, weekStart, materialMap); // 历史有销量 → 停售前提；从无销量=耗材，放行
        Map<String, BigDecimal> inTransitMap = pgInTransitMap(store, materialMap, ruleMap, convMap);
        LocalDate lossStart = weekStart.minusDays(28); // 近4周（前闭后开，不跨周）
        // 修正因子只统计本次任务物料池的（任务外的报损/调货/还货/自购不参与计算）
        List<String> midList = curSummaries.stream().map(TaskMaterialSummary::getMaterialId)
                .filter(Objects::nonNull).distinct().toList();
        Map<String, BigDecimal> lossMap = lossWeeklyMap(store.getId(), midList, lossStart, weekStart); // key mid|storeId → 周均
        Map<String, BigDecimal> transferMap = transferWeeklyMap(store.getId(), midList, lossStart, weekStart); // key mid|storeId → 周均净调出
        Map<String, BigDecimal> returnMap = returnWeeklyMap(store.getId(), midList, lossStart, weekStart, ruleMap, convMap); // key mid|storeId → 周均净还出(仅 goods)
        Map<String, BigDecimal> selfPurchaseMap = selfPurchaseWeeklyMap(store.getId(), midList, lossStart, weekStart, ruleMap, convMap); // key mid|storeId → 周均
        Map<String, BigDecimal> orderRhythmDaily = orderRhythmDailyMap(store, weekStart, materialMap, ruleMap, convMap); // 耗材订货节奏日均（key mid）

        // 库存差法（周盘优先、月盘降级）：期间企迈报货入库，惰性拉取，避免每日全店扫描拉取过重
        boolean hasInbound = prev != null && store.getQmaiStoreId() != null;
        Map<String, Map<String, BigDecimal>> inboundMap = null; // null = 尚未尝试
        boolean inboundFailed = false;
        BigDecimal daysBetween = null;
        if (prev != null && prev.getSubmittedAt() != null && current.getSubmittedAt() != null) {
            long days = ChronoUnit.DAYS.between(prev.getSubmittedAt().toLocalDate(), current.getSubmittedAt().toLocalDate());
            if (days > 0) daysBetween = BigDecimal.valueOf(days);
        }

        // 覆盖销售天数（拆单 v0.2 修订）：按本店两个订货日实际间隔切分（'3,7' → 批1=4 天 / 批2=3 天）；
        // 回测（batchNo=0）保持整周口径走 sys_config
        int cycleDays = parseInt(getConfig(CFG_CYCLE_DAYS, "7"), 7);
        if (batchNo == BATCH_1 || batchNo == BATCH_2) cycleDays = coverageDaysOf(store, batchNo);
        int safetyDays = parseInt(getConfig(CFG_SAFETY_DAYS, "1"), 1);
        BigDecimal defaultDailyUse = new BigDecimal(getConfig(CFG_DEFAULT_DAILY_USE, "0.5"));

        // 拆单批2 估算库存（无实盘）懒加载：盘点提交后已送达订货量（PG），首次用到才拉取
        Map<String, BigDecimal> deliveredAfterCountMap = null; // null = 尚未尝试
        boolean deliveredFailed = false;
        LocalDateTime nowTs = LocalDateTime.now();

        // 店长手动订货抑制源（2026-09-09 南姜案例）：近 N 天店长在企迈手动下过单的物料集合。
        // 一次性拉取（回测不拉企迈），失败=null → 不抑制（宽容降级）
        Set<String> recentManualDeclared = null;
        if (predictOut == null && store.getQmaiStoreId() != null) {
            int suppressDays = parseInt(getConfig(CFG_MANUAL_SUPPRESS_DAYS, "7"), 7);
            if (suppressDays > 0) {
                try {
                    recentManualDeclared = fetchRecentManualDeclaredCodes(
                            store.getQmaiStoreId(), nowTs, suppressDays);
                } catch (Exception e) {
                    log.warn("SMART_ORDER_GEN 手动订抑制源查询失败 storeId={}: {}", store.getId(), e.getMessage());
                }
            }
        }

        List<SmartOrderItem> items = new ArrayList<>();
        int sortNo = 0;
        for (TaskMaterialSummary sum : curSummaries) {
            Material material = materialMap.get(sum.getMaterialId());
            if (material == null) continue;
            // 半成品/淘汰类不下单（周盘模板已约束，此处兜底）
            String matCategory = material.getCategory();
            if (matCategory != null && (matCategory.contains("半成品") || matCategory.contains("淘汰"))) continue;
            MaterialInventoryRule rule = ruleMap.get(sum.getMaterialId());
            List<MaterialConversionRule> convs = convMap.getOrDefault(rule != null ? rule.getRuleId() : "", List.of());
            String baseUnit = StringUtils.hasText(sum.getBaseUnit()) ? sum.getBaseUnit() : "";
            // 下单单位：优先订货单位（order_unit=usageUnit），其次库存单位，兜底基础单位
            String orderUnit = orderUnitOf(rule, baseUnit);
            BigDecimal factor = ConversionFactorUtil.computeConversionFactor(orderUnit, baseUnit, convs);
            // 单价（2026-09-09 业务确认）：按本地「订货单价」下单——优先 rule.order_price
            //（xinfo standardCostPrice 同步，按订货单位整件口径，如 PP700细吸管 10 元/包）；
            // order_price 缺失（62/491 物料）时回退 unit_price(基础单位)×换算系数
            BigDecimal unitPrice = orderUnitPrice(rule, factor);

            BigDecimal currentQty = sum.getTotalQty() != null ? sum.getTotalQty() : BigDecimal.ZERO;
            String mid = sum.getMaterialId();

            // 疑似停售：历史有销量但近7天无销售记录（已下架/门店停售该品），不订货；库存为 0 不算（卖完需补货）。
            // 历史从无销量记录的耗材类（打包袋/清洁/周边等，消耗不体现在 POS 销售）不拦截 → 走订货节奏预测
            // 护栏（2026-09）：近90天该店仍有已送达订货的物料不判停售——PG 销量失真期会漏记，
            // 店长仍在补货说明未停售；只有既不卖也没人补货才判定停售
            String qm = material.getQmCode();
            if (StringUtils.hasText(qm) && currentQty.compareTo(BigDecimal.ZERO) > 0
                    && historicalSold.contains(qm) && !recentSold.contains(qm)
                    && !orderRhythmDaily.containsKey(mid)) {
                if (predictOut != null) {
                    Map<String, Object> trace = new HashMap<>();
                    trace.put("dailyUse", null);
                    trace.put("useMode", "stopped");
                    trace.put("demand", null);
                    trace.put("base", null);
                    trace.put("inOrder", false);
                    trace.put("reason", "疑似停售（近7天无销售记录）");
                    predictOut.put(mid, trace);
                }
                continue;
            }

            // ---- 店长手动订货抑制（2026-09-09 南姜案例）----
            // 店长近 N 天已在企迈手动下过单（source=1 且未取消）的物料，且系统对该物料无 PG 订货节奏
            //（= 低频手动管理料：南姜/香茅/鲜果等，店长自己按需高频小单补，不走系统节奏模型）→ 本次不重复建议，
            // 避免"9/6 刚订 300g、9/9 又让订 117g"。PG 有节奏的周度主料（厚椰乳/冰勃朗等）不受影响。
            if (recentManualDeclared != null && StringUtils.hasText(qm)
                    && recentManualDeclared.contains(qm) && !orderRhythmDaily.containsKey(mid)) {
                if (predictOut != null) {
                    Map<String, Object> trace = new HashMap<>();
                    trace.put("dailyUse", null);
                    trace.put("useMode", "manualDeclared");
                    trace.put("demand", null);
                    trace.put("base", null);
                    trace.put("inOrder", false);
                    trace.put("reason", "店长近7天已手动订过（手动管理料不重复建议）");
                    predictOut.put(mid, trace);
                }
                continue;
            }

            // ---- P2B 需求预测（降级链：去年同周 → 近4周均值 → 库存差法 → 默认兜底）----
            // 日均分母用「窗口内实际有数据的天数」而非固定 7/28 天——PG 数据不足 4 周时（新店/新接入），
            // 无数据日按 0 计入会把日均系统性摊薄低估（如只有 10 天数据 ÷28 = 低估 2.8 倍）
            PgSales lySale = lastYearMap.getOrDefault(mid, new PgSales(BigDecimal.ZERO, 0));
            PgSales r4Sale = recent4wMap.getOrDefault(mid, new PgSales(BigDecimal.ZERO, 0));
            PgSales ly4Sale = lastYear4wMap.getOrDefault(mid, new PgSales(BigDecimal.ZERO, 0));
            BigDecimal lastYearQty = lySale.qty();    // 去年同周（基础单位）
            BigDecimal recent4wQty = r4Sale.qty();    // 近4周
            BigDecimal ly4wQty = ly4Sale.qty();       // 去年同4周
            BigDecimal trendFactor = null;
            BigDecimal dailyUse;
            String useMode;

            // 库存差日均：(上次盘点 + 期间企迈入库 − 本次盘点) ÷ 间隔天数，= 上周真实消耗（含损耗/自购/调拨一切）
            BigDecimal inventoryDaily = null;
            boolean canInventory = prev != null && hasInbound && daysBetween != null && prevQtyMap.containsKey(mid);
            if (canInventory) {
                if (inboundMap == null && !inboundFailed) {
                    try {
                        inboundMap = fetchInbound(store.getQmaiStoreId(), prev.getSubmittedAt(), current.getSubmittedAt());
                    } catch (Exception e) {
                        log.warn("SMART_ORDER_GEN storeId={} 企迈报货入库拉取失败: {}", store.getId(), e.getMessage());
                        inboundFailed = true;
                        inboundMap = Map.of();
                    }
                }
                BigDecimal prevQty = prevQtyMap.get(mid);
                BigDecimal inbound = inboundMap == null || inboundMap.isEmpty() ? BigDecimal.ZERO
                        : inboundInBase(inboundMap, material.getQmCode(), baseUnit, convs);
                BigDecimal used = prevQty.add(inbound).subtract(currentQty);
                if (used.compareTo(BigDecimal.ZERO) < 0) used = BigDecimal.ZERO;
                inventoryDaily = used.divide(daysBetween, 6, RoundingMode.HALF_UP);
            }
            BigDecimal pgDaily = r4Sale.days() > 0
                    ? recent4wQty.divide(BigDecimal.valueOf(r4Sale.days()), 6, RoundingMode.HALF_UP) : null;

            // 理论消耗日均（costcard 域：销售×成本卡展开到 base_qty；开关 smart_order_use_consume）
            // 近 7 天该店物料 base_qty 有数据日均；数据日 <3 视为缺源（新店/卡未覆盖），null 不参与
            BigDecimal consumeDaily = null;
            if ("1".equals(getConfig(CFG_USE_CONSUME, "0")) && StringUtils.hasText(mid)) {
                try {
                    List<Map<String, Object>> cr = jdbcTemplate.queryForList(
                            "SELECT COALESCE(SUM(base_qty),0) AS q, COUNT(DISTINCT stat_date) AS d"
                                    + " FROM store_material_consume_daily"
                                    + " WHERE store_id = ? AND material_id = ? AND base_qty IS NOT NULL"
                                    + " AND stat_date >= (CURDATE() - INTERVAL 7 DAY) AND stat_date < CURDATE()",
                            store.getId(), mid);
                    if (!cr.isEmpty()) {
                        long days = ((Number) cr.get(0).get("d")).longValue();
                        if (days >= 3) {
                            BigDecimal q = new BigDecimal(String.valueOf(cr.get(0).get("q")));
                            consumeDaily = q.divide(BigDecimal.valueOf(days), 6, RoundingMode.HALF_UP);
                        }
                    }
                } catch (Exception e) {
                    log.warn("SMART_ORDER 理论消耗读取失败 mid={}: {}", mid, e.getMessage());
                }
            }

            // PG 销量预测源开关（sys_config smart_order_use_pg，默认 0=失真期关闭）。
            // 失真期：PG 销量系统性低估 30~40%（2026-09 全量验证），预测只用库存差/订货节奏；
            // 企迈修复验收达标后置 1：恢复 PG 参与互证（仍以库存差为准，不一致信盘点倒推而非 PG）。
            boolean usePg = "1".equals(getConfig(CFG_USE_PG, "0"));
            BigDecimal devThirty = BigDecimal.valueOf(0.3);

            // ===== 预测主链（2026-09 三源融合版：订货节奏 + 库存差 + PG 一起算）=====
            // 三个日均口径：
            //   ① inventoryDaily 库存差（上次盘点+期间企迈入库−本次盘点）÷天数 = 客观事实（含损耗/调货/自购）
            //   ② rhythmDaily  店长订货节奏（近90天已送达订货÷有货周数÷7）= 店长实际需求（贴合真实消耗，验证中位 12.9%）
            //   ③ pgDaily      PG 近4周日均（÷有数据天数）                    = 校验源（失真期 use_pg=0 不参与数值）
            // 融合规则：① 为主源 → 与②偏差 ≤30% 取均值（两个独立信源互证降噪，mode=inventoryRhythm）；
            //   不一致信 ①（店长感觉多订 ~13%、盘点倒推才是真实消耗）；
            //   ③ 仅在 use_pg=1（企迈修复验收后）且与当前日均偏差 ≤30% 时再取均值（三源一致才收窄）；
            //   不一致时 PG 不参与（低估方向不可信）
            BigDecimal rhythmDaily = orderRhythmDaily != null ? orderRhythmDaily.get(mid) : null;

            if (inventoryDaily != null) {
                // ① 主源 = 库存差；与 ② 店长订货节奏互证
                boolean rhythmAgree = rhythmDaily != null && rhythmDaily.compareTo(BigDecimal.ZERO) > 0;
                BigDecimal baseDaily = inventoryDaily;
                if (rhythmAgree) {
                    BigDecimal maxDaily = inventoryDaily.max(rhythmDaily);
                    BigDecimal dev = inventoryDaily.subtract(rhythmDaily).abs().divide(maxDaily, 4, RoundingMode.HALF_UP);
                    if (dev.compareTo(devThirty) <= 0) {
                        baseDaily = inventoryDaily.add(rhythmDaily).divide(BigDecimal.valueOf(2), 6, RoundingMode.HALF_UP);
                        useMode = "inventoryRhythm"; // 库存差与订货节奏互证一致 → 取均值
                    } else {
                        rhythmAgree = false;
                        baseDaily = inventoryDaily; // 不一致信库存差（店长感觉偏差大，needs_review 会提示）
                        useMode = "inventory";
                    }
                } else {
                    useMode = "inventory";
                }
                // ③ PG 三源互证（仅修复期 use_pg=1）：与当前日均一致 → 取均值收窄；不一致维持客观侧
                if (usePg && pgDaily != null && pgDaily.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal maxDaily = baseDaily.max(pgDaily);
                    BigDecimal dev = baseDaily.subtract(pgDaily).abs().divide(maxDaily, 4, RoundingMode.HALF_UP);
                    if (dev.compareTo(devThirty) <= 0) {
                        dailyUse = baseDaily.add(pgDaily).divide(BigDecimal.valueOf(2), 6, RoundingMode.HALF_UP);
                        useMode = "blend"; // 与 PG 一致：取均值（含 PG 信息，三源或两源+PG）
                    } else {
                        dailyUse = baseDaily; // PG 与客观口径偏差大 → 不参与数值
                    }
                } else {
                    dailyUse = baseDaily;
                }
                // 理论消耗互证（smart_order_use_consume=1）：与当前日均偏差 ≤30% 取均值（口径独立收窄）
                if (consumeDaily != null && consumeDaily.compareTo(BigDecimal.ZERO) > 0 && dailyUse != null) {
                    BigDecimal maxDaily = dailyUse.max(consumeDaily);
                    BigDecimal dev = dailyUse.subtract(consumeDaily).abs()
                            .divide(maxDaily, 4, RoundingMode.HALF_UP);
                    if (dev.compareTo(BigDecimal.valueOf(0.3)) <= 0) {
                        dailyUse = dailyUse.add(consumeDaily).divide(BigDecimal.valueOf(2), 6, RoundingMode.HALF_UP);
                        useMode = "consumeBlend";
                    }
                }
            } else if (usePg && lastYearQty.compareTo(BigDecimal.ZERO) > 0) {
                useMode = "lastYear";
                BigDecimal lyDailyBase = lastYearQty.divide(BigDecimal.valueOf(Math.max(lySale.days(), 1)), 6, RoundingMode.HALF_UP);
                if (ly4wQty.compareTo(BigDecimal.ZERO) > 0) {
                    // 趋势系数 = 近4周日均 ÷ 去年同4周日均（按有数据天数），clamp [0.5, 2.0]
                    BigDecimal r4Daily = r4Sale.days() > 0
                            ? recent4wQty.divide(BigDecimal.valueOf(r4Sale.days()), 6, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                    BigDecimal ly4Daily = ly4Sale.days() > 0
                            ? ly4wQty.divide(BigDecimal.valueOf(ly4Sale.days()), 6, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                    trendFactor = r4Daily.divide(ly4Daily, 3, RoundingMode.HALF_UP)
                            .max(BigDecimal.valueOf(0.5)).min(BigDecimal.valueOf(2.0));
                    dailyUse = lyDailyBase.multiply(trendFactor);
                } else {
                    dailyUse = lyDailyBase; // 无去年4周对比，趋势=1
                }
            } else if (usePg && recent4wQty.compareTo(BigDecimal.ZERO) > 0) {
                useMode = "recent4w";
                dailyUse = pgDaily;
            } else if ("1".equals(getConfig(CFG_USE_CONSUME, "0")) && consumeDaily != null
                    && consumeDaily.compareTo(BigDecimal.ZERO) > 0) {
                useMode = "consumeDaily"; // 无库存差：理论消耗（销售×成本卡）= 真实出货代理
                dailyUse = consumeDaily;
            } else if (rhythmDaily != null && rhythmDaily.compareTo(BigDecimal.ZERO) > 0) {
                useMode = "orderRhythm"; // 无库存差（新店/无盘点历史）：店长订货节奏=需求代理
                dailyUse = rhythmDaily;
            } else {
                useMode = "default";
                dailyUse = defaultDailyUse;
            }

            // 修正因子（基础单位）—— 按预测源口径拆分叠加比例，避免重复或漏算：
            //   inventory（纯库存差日均 = 真实消耗）          → 报损/调货/还货/自购全含在日均中，修正=0
            //   inventoryRhythm（库存差+订货节奏均值）        → 报损两源都吸收（店长补货含损耗）修正=0；
            //                                                  调货/还货/自购 库存差那半含、订货那半不含 → ×0.5
            //   blend（与 PG 一致取均值）                    → PG 那半不含 → 修正×0.5 补齐
            //   orderRhythm（订货节奏 = 已送达订货量）       → 报损被店长补货自然吸收（≈含），但调货/自购/还货
            //                                                 不走订货通道、节奏中不含 → 仅补这三项（×1）
            //   recent4w/lastYear/default（纯销量/拍脑袋）  → 全不含 → 修正×1
            // 所有叠加项 × (C+H)/7 周期放大：修正数据按周均口径（7 天），与 demand 的 (cycleDays+safetyDays)
            // 天周期对齐（此前只加了 1 周量，少算 ~30%）
            boolean pureConsumption = "inventory".equals(useMode);                     // 修正=0
            boolean rhythmHalfConsumption = "inventoryRhythm".equals(useMode);         // 损耗=0，调货/还货/自购×0.5
            boolean halfConsumption = "blend".equals(useMode);                         // 修正×0.5
            boolean rhythmConsumption = "orderRhythm".equals(useMode);                 // 仅补非报损项
            BigDecimal periodScale = BigDecimal.valueOf(cycleDays + safetyDays)
                    .divide(BigDecimal.valueOf(7), 4, RoundingMode.HALF_UP);
            BigDecimal halfScale = periodScale.multiply(BigDecimal.valueOf(0.5));
            String mk = mid + "|" + store.getId();
            boolean lossAbsorbed = pureConsumption || rhythmHalfConsumption || rhythmConsumption; // 报损已被吸收
            BigDecimal lossQty = lossAbsorbed ? BigDecimal.ZERO
                    : lossMap.getOrDefault(mk, BigDecimal.ZERO)
                            .multiply(halfConsumption ? halfScale : periodScale);       // 周期内损耗(+)
            boolean halfTransfers = rhythmHalfConsumption || halfConsumption;          // 调货/还货/自购 ×0.5
            BigDecimal transferQty = pureConsumption ? BigDecimal.ZERO
                    : transferMap.getOrDefault(mk, BigDecimal.ZERO)
                            .multiply(halfTransfers ? halfScale : periodScale);         // 周期内净调出(±)
            BigDecimal returnQty = pureConsumption ? BigDecimal.ZERO
                    : returnMap.getOrDefault(mk, BigDecimal.ZERO)
                            .multiply(halfTransfers ? halfScale : periodScale);         // 周期内净还出(±, 仅 goods, 还钱不计)
            BigDecimal inTransitQty = inTransitMap.getOrDefault(mid, BigDecimal.ZERO);  // 在途(−，存量不乘)
            BigDecimal selfPurchaseQty = pureConsumption ? BigDecimal.ZERO
                    : selfPurchaseMap.getOrDefault(mk, BigDecimal.ZERO)
                            .multiply(halfTransfers ? halfScale : periodScale);         // 周期内自购(−)

            // 拆单批2（无实盘）：库存基准 = 估算值 = 盘点数 + 盘点后已送达订货(真实) − 日均×已过天数(预估)
            // 误差方向自保护（plan v0.2 §3.4）：实际消耗快 → 估算偏低 → 订多 → 安全；慢 → 订少但架上货多，
            // 下周二实盘拉回。批1/回测 = 实盘数原样
            BigDecimal inventoryQty = currentQty;
            if (batchNo == BATCH_2) {
                if (deliveredAfterCountMap == null && !deliveredFailed) {
                    try {
                        deliveredAfterCountMap = deliveredAfterCount(store, current, nowTs, materialMap, ruleMap, convMap);
                    } catch (Exception e) {
                        log.warn("SMART_ORDER_GEN storeId={} 批2盘点后到货拉取失败，按无到货估算: {}", store.getId(), e.getMessage());
                        deliveredFailed = true;
                        deliveredAfterCountMap = Map.of();
                    }
                }
                BigDecimal arrived = deliveredAfterCountMap.getOrDefault(mid, BigDecimal.ZERO);
                BigDecimal elapsed = elapsedDaysOf(current.getSubmittedAt(), nowTs);
                BigDecimal estimate = currentQty.add(arrived).subtract(dailyUse.multiply(elapsed));
                if (estimate.compareTo(BigDecimal.ZERO) < 0) estimate = BigDecimal.ZERO; // 不因负估算把订单顶过整窗需求
                inventoryQty = estimate;
            }

            // ① 预计消耗（含安全库存）② 修正 ③ 基础建议量：max(0, 预测 + 损耗 ± 调货 ± 还货 − 库存(批2=估算) − 在途 − 自购)
            BigDecimal demand = dailyUse.multiply(BigDecimal.valueOf(cycleDays + safetyDays));
            BigDecimal base = demand.add(lossQty).add(transferQty).add(returnQty)
                    .subtract(inventoryQty).subtract(inTransitQty).subtract(selfPurchaseQty);
            Map<String, Object> trace = null;
            if (predictOut != null) {
                trace = new HashMap<>();
                trace.put("dailyUse", dailyUse);
                trace.put("useMode", useMode);
                trace.put("lastYearQty", lastYearQty);
                trace.put("recent4wQty", recent4wQty);
                trace.put("trendFactor", trendFactor);
                trace.put("demand", demand);
                trace.put("base", base);
                trace.put("factor", factor);
                trace.put("inOrder", false);
                predictOut.put(mid, trace);
            }
            if (base.compareTo(BigDecimal.ZERO) <= 0) continue; // 库存充足，不入单

            // ④ 单位修正：换算为订货单位，并统一向上取整为整数（仓库按整件发货，不允许小数）
            BigDecimal suggest = factor != null
                    ? base.divide(factor, 6, RoundingMode.HALF_UP).setScale(0, RoundingMode.CEILING)
                    : base.setScale(0, RoundingMode.CEILING);
            boolean unitConverted = factor != null && factor.compareTo(BigDecimal.ONE) != 0;

            // ④' 企迈订货约束取整（material_order_constraint，2026-09-10）：
            //     起订量：建议量不足则提升到起订量（酸角 12 瓶、南姜 300g、金桔柠檬 300g）；
            //     倍数：向上取整到倍数（香茅/南姜/金桔柠檬 100g、乍甸酸奶 5 份、杯套 20 捆）——
            //     否则下单页报"订货数量小于起订数量 / 不满足订货倍数"，店长必须手动改。
            //     限购（limitQty）仅作提示不强制：超过时仍需按需订（企迈侧会拦截，明细里标注）。
            boolean constraintApplied = false;
            String constraintNote = null;
            MaterialOrderConstraint oc = constraintMap.get(material.getQmCode());
            if (oc != null) {
                BigDecimal before = suggest;
                BigDecimal mult = oc.getOrderMultiple();
                if (mult != null && mult.compareTo(BigDecimal.ZERO) > 0) {
                    suggest = suggest.divide(mult, 0, RoundingMode.CEILING).multiply(mult);
                }
                BigDecimal minQ = oc.getMinOrderQty();
                if (minQ != null && minQ.compareTo(BigDecimal.ZERO) > 0
                        && suggest.compareTo(minQ) < 0) {
                    suggest = minQ;
                }
                if (suggest.compareTo(before) != 0) {
                    constraintApplied = true;
                    constraintNote = buildConstraintNote(oc, before, suggest);
                }
            }

            // ⑤ 无企迈编码无法下单，跳过
            String qmCode = material.getQmCode();
            if (!StringUtils.hasText(qmCode)) {
                log.info("SMART_ORDER_GEN storeId={} 物料 {} 无企迈编码，跳过", store.getId(), material.getMaterialName());
                continue;
            }

            BigDecimal supportDays = dailyUse.compareTo(BigDecimal.ZERO) > 0
                    ? inventoryQty.divide(dailyUse, 1, RoundingMode.HALF_UP) : null;

            StringBuilder reason = new StringBuilder();
            if ("lastYear".equals(useMode)) {
                reason.append("去年同期 ").append(trimNum(lastYearQty));
                if (trendFactor != null) reason.append("，趋势系数 ").append(trendFactor.toPlainString());
            } else if ("recent4w".equals(useMode)) {
                reason.append("近4周日均消耗估算");
            } else if ("inventory".equals(useMode)) {
                reason.append("库存差法估算日均消耗");
            } else if ("inventoryRhythm".equals(useMode)) {
                reason.append("库存差与您近期订货节奏互证（取均值）");
            } else if ("blend".equals(useMode)) {
                reason.append("库存差与PG销量互证（取均值）");
            } else if ("orderRhythm".equals(useMode)) {
                reason.append("按您近期订货节奏估算日均消耗");
            } else {
                reason.append("暂无历史数据，按默认日均消耗估算");
            }
            if (lossQty.compareTo(BigDecimal.ZERO) != 0) reason.append("，损耗 ").append(signed(lossQty));
            if (transferQty.compareTo(BigDecimal.ZERO) != 0) reason.append("，调货 ").append(signed(transferQty));
            if (returnQty.compareTo(BigDecimal.ZERO) != 0) reason.append("，还货 ").append(signed(returnQty));
            if (inTransitQty.compareTo(BigDecimal.ZERO) != 0) reason.append("，在途 −").append(trimNum(inTransitQty.abs()));
            if (selfPurchaseQty.compareTo(BigDecimal.ZERO) != 0) reason.append("，自购 −").append(trimNum(selfPurchaseQty));
            if (unitConverted) reason.append(" · 按订货单位换算并向上取整");
            if (constraintApplied) reason.append("；").append(constraintNote);
            // 批2 库存是估算值（非实盘），明细标注供店长知情（plan v0.2 决策#4/#F）
            if (batchNo == BATCH_2 && current.getSubmittedAt() != null) {
                LocalDate cd = current.getSubmittedAt().toLocalDate();
                reason.append("；库存为估算值（基于 ").append(cd.getMonthValue()).append("月")
                        .append(cd.getDayOfMonth()).append("日 盘点）");
            }

            // 店长意图对照（2026-09）：近90天订货节奏折算"单次订货量"（订货单位），与系统建议对比。
            // 偏差 >30% → needs_review=1：前端提示"系统建议 X，您近期每次约订 Y（差异较大请确认）"。
            // 交互原则：店长确认时以店长修改为准（感觉纠正系统）；未修改则按系统建议下单（系统兜住感觉）
            // （rhythmDaily 已在三源融合段声明，此处复用）
            BigDecimal orderRefQty = null;
            int needsReview = 0;
            if (rhythmDaily != null && rhythmDaily.compareTo(BigDecimal.ZERO) > 0
                    && factor != null && factor.compareTo(BigDecimal.ZERO) > 0) {
                // 订货节奏日均 × 订货周期 = 单次订货量（基础单位）→ ÷factor 转订货单位
                orderRefQty = rhythmDaily.multiply(BigDecimal.valueOf(cycleDays))
                        .divide(factor, 2, RoundingMode.HALF_UP);
                if (orderRefQty.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal dev = suggest.subtract(orderRefQty).abs()
                            .divide(orderRefQty, 4, RoundingMode.HALF_UP);
                    if (dev.compareTo(BigDecimal.valueOf(0.3)) > 0) needsReview = 1;
                }
            }
            if (orderRefQty != null && orderRefQty.compareTo(BigDecimal.ZERO) > 0) {
                reason.append(needsReview == 1
                        ? "；您近期每次约订 " + trimNum(orderRefQty) + " 件（差异较大，请核对确认）"
                        : "；您近期每次约订 " + trimNum(orderRefQty) + " 件");
            }

            SmartOrderItem item = new SmartOrderItem();
            item.setMaterialId(material.getId());
            item.setMaterialName(sum.getMaterialName());
            item.setSpec(sum.getSpec());
            item.setCategory(material.getCategory());
            item.setQmCode(qmCode);
            // stock_unit 列存订货单位（下单口径）；qmStockUnit 存库存单位（企迈库存口径，供校验/展示）
            item.setStockUnit(orderUnit);
            item.setQmStockUnit(rule != null && StringUtils.hasText(rule.getStockUnit()) ? rule.getStockUnit() : baseUnit);
            item.setBaseUnit(baseUnit);
            item.setUnitPrice(unitPrice);
            item.setCurrentInventory(inventoryQty); // 批1=实盘数；批2=估算值（估算日期见 reason 标注）
            item.setDailyUse(dailyUse);
            item.setLastYearQty(lastYearQty.compareTo(BigDecimal.ZERO) > 0 ? lastYearQty : null);
            item.setTrendFactor(trendFactor);
            item.setLossQty(lossQty);
            item.setTransferQty(transferQty);
            item.setReturnQty(returnQty);
            item.setInTransitQty(inTransitQty);
            item.setSelfPurchaseQty(selfPurchaseQty);
            item.setCycleDays(cycleDays);
            item.setSafetyDays(safetyDays);
            item.setSuggestQty(suggest);
            item.setSupportDays(supportDays);
            item.setOrderRefQty(orderRefQty);
            item.setNeedsReview(needsReview);
            item.setReason(reason.toString());
            item.setSortNo(++sortNo);
            items.add(item);
            if (trace != null) {
                trace.put("inOrder", true);
                trace.put("reason", reason.toString());
            }
        }

        return items;
    }

    /** 落库：插入建议单 + 明细（事务内），返回是否新建；拆单后标注批次与订货日 */
    private boolean persistOrder(StoreInfo store, LocalDate weekStart, List<SmartOrderItem> items,
                                 Long taskId, LocalDate deadlineDate, int batchNo, int orderDay,
                                 LocalDate countDate) {
        BigDecimal totalQty = items.stream().map(SmartOrderItem::getSuggestQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal amount = items.stream()
                .map(i -> i.getUnitPrice() == null ? BigDecimal.ZERO
                        : i.getSuggestQty().multiply(i.getUnitPrice()))
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);

        WeekFields wf = WeekFields.ISO;
        SmartOrder order = new SmartOrder();
        order.setBizCode(BizCodeUtil.of("SMO"));
        order.setStoreId(store.getId());
        order.setStoreName(store.getMendianmingcheng());
        order.setWeekStartDate(weekStart);
        order.setWeekLabel(weekStart.get(wf.weekBasedYear()) + "-W" + weekStart.get(wf.weekOfWeekBasedYear()));
        order.setOrderDay(orderDay);
        order.setBatchNo(batchNo);
        order.setTaskId(taskId);
        order.setStatus("pending");
        order.setItemCount(items.size());
        order.setTotalQty(totalQty);
        order.setSuggestAmount(amount);
        order.setDeadline(orderDeadline(deadlineDate));
        order.setGeneratedAt(LocalDateTime.now());
        order.setSyncAttempts(0);

        try {
            transactionTemplate.executeWithoutResult(ts -> {
                orderMapper.insert(order);
                for (SmartOrderItem item : items) {
                    item.setOrderId(order.getId());
                    itemMapper.insert(item);
                }
            });
        } catch (DuplicateKeyException e) {
            log.info("SMART_ORDER_GEN storeId={} taskId={} batch={} 建议单已存在（并发防重），跳过",
                    store.getId(), taskId, batchNo);
            return false;
        }
        log.info("SMART_ORDER_GEN storeId={} 生成建议单 第{}批 orderDay={} {} 品项={} 数量={} 金额={}",
                store.getId(), batchNo, orderDay, order.getBizCode(), items.size(), totalQty, amount);

        // 订阅消息：建议单生成（批1=3:00 job/周盘提交补触发，批2=第二订货日9:00 job）→ 通知该店店长确认
        String countLabel = countDate != null
                ? countDate.getMonthValue() + "月" + countDate.getDayOfMonth() + "日" : "";
        String title = batchNo == BATCH_2
                ? "【订货】本周第 2 批订货单已生成（库存为估算值）"
                : "【订货】本周第 1 批订货单已生成";
        String content = batchNo == BATCH_2
                ? "第 2 批库存按估算值计算（基于 " + countLabel + " 盘点），请核对后确认"
                : "请查看本周第 1 批建议订货单并确认";
        notificationService.enqueueToStoreManagers(order.getStoreId(), "ORDER_CONFIRM",
                title, content, String.valueOf(order.getId()), null);
        return true;
    }

    // ==================== P2B 预测数据查询 ====================

    /** PG 窗口销量：qty = 汇总数量（换算基础单位），days = 实际有数据的天数（用于日均计算，避免数据不足期被 28 天摊薄） */
    private record PgSales(BigDecimal qty, int days) {}

    /**
     * 近7天 [weekStart-7, weekStart) PG 有销量记录的 qm_code 集合（停售检测用，批量一次查询）。
     * 有库存但不在集合内的物料视为疑似停售（已下架/停售），不订货；无企迈编码/无仓的物料不参与检测。
     */
    private Set<String> recentSoldQmCodes(StoreInfo store, LocalDate weekStart, Map<String, Material> materialMap) {
        Set<String> sold = new HashSet<>();
        Map<String, String> qmToMid = buildQmToMid(materialMap);
        if (!StringUtils.hasText(store.getCangkuid()) || qmToMid.isEmpty()) return sold;
        try {
            String inItems = qmToMid.keySet().stream().map(c -> "'" + c + "'").collect(Collectors.joining(","));
            String sql = "SELECT DISTINCT item_code FROM dwd.store_item_sales " +
                    "WHERE warehouse_code = ? AND stat_date >= ?::date AND stat_date < ?::date AND item_code IN (" + inItems + ")";
            List<Map<String, Object>> rows = pgJdbc.queryForList(sql, store.getCangkuid(),
                    weekStart.minusDays(7).toString(), weekStart.toString());
            for (Map<String, Object> row : rows) sold.add((String) row.get("item_code"));
        } catch (Exception e) {
            log.warn("SMART_ORDER_GEN 停售检测查询失败 storeId={}: {}", store.getId(), e.getMessage());
        }
        return sold;
    }

    /**
     * 历史有销量记录的 qm_code 集合（[weekStart-120, weekStart)，停售检测的前提条件）。
     * 从未有销量记录的物料是耗材类/新增品（消耗不体现在 POS 销售，如打包袋/清洁/周边），不能判停售。
     */
    private Set<String> historicalSoldQmCodes(StoreInfo store, LocalDate weekStart, Map<String, Material> materialMap) {
        Set<String> sold = new HashSet<>();
        Map<String, String> qmToMid = buildQmToMid(materialMap);
        if (!StringUtils.hasText(store.getCangkuid()) || qmToMid.isEmpty()) return sold;
        try {
            String inItems = qmToMid.keySet().stream().map(c -> "'" + c + "'").collect(Collectors.joining(","));
            String sql = "SELECT DISTINCT item_code FROM dwd.store_item_sales " +
                    "WHERE warehouse_code = ? AND stat_date >= ?::date AND stat_date < ?::date AND item_code IN (" + inItems + ")";
            List<Map<String, Object>> rows = pgJdbc.queryForList(sql, store.getCangkuid(),
                    weekStart.minusDays(120).toString(), weekStart.toString());
            for (Map<String, Object> row : rows) sold.add((String) row.get("item_code"));
        } catch (Exception e) {
            log.warn("SMART_ORDER_GEN 历史销量查询失败 storeId={}: {}", store.getId(), e.getMessage());
        }
        return sold;
    }

    /**
     * PG 销量汇总（窗口 [start, end)，口径与差异模块一致：sales_quantity − return_quantity，换算基础单位）。
     * key = material.material_id 编码；days 按 (item_code, unit) 行去重统计，同物料多 unit 的天数可能重复（边缘场景可忽略）。
     */
    private Map<String, PgSales> pgSalesSum(StoreInfo store, LocalDate start, LocalDate end,
                                            Map<String, Material> materialMap,
                                            Map<String, MaterialInventoryRule> ruleMap,
                                            Map<String, List<MaterialConversionRule>> convMap) {
        Map<String, PgSales> map = new HashMap<>();
        Map<String, String> qmToMid = buildQmToMid(materialMap);
        if (!StringUtils.hasText(store.getCangkuid()) || qmToMid.isEmpty()) return map;
        try {
            String inItems = qmToMid.keySet().stream().map(c -> "'" + c + "'").collect(Collectors.joining(","));
            // 明细级按天取数：识别并剔除"月汇总行"——企迈数据形态为整月汇总写在月初单日
            // （2026-06-01 行 = 6 月整月、2026-07-01 行 = 7/1~7/23），若被当单日，日均会虚高 20~30 倍
            String sql = "SELECT item_code, stat_date, unit, " +
                    "COALESCE(SUM(sales_quantity - COALESCE(return_quantity,0)),0) AS day_qty " +
                    "FROM dwd.store_item_sales WHERE warehouse_code = ? " +
                    "AND stat_date >= ?::date AND stat_date < ?::date AND item_code IN (" + inItems + ") " +
                    "GROUP BY item_code, stat_date, unit";
            List<Map<String, Object>> rows = pgJdbc.queryForList(sql, store.getCangkuid(), start.toString(), end.toString());
            // 按 (item_code, unit) 收集逐日值，识别汇总行后聚合
            Map<String, List<BigDecimal>> dayQtyByKey = new LinkedHashMap<>();
            for (Map<String, Object> row : rows) {
                String key = row.get("item_code") + "|" + row.get("unit");
                dayQtyByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(toBigDecimal(row.get("day_qty")));
            }
            for (Map.Entry<String, List<BigDecimal>> e : dayQtyByKey.entrySet()) {
                String[] parts = e.getKey().split("\\|", -1);
                String itemCode = parts[0];
                String unit = parts.length > 1 ? parts[1] : null;
                List<BigDecimal> dayQtys = e.getValue();
                int days = dayQtys.size();
                BigDecimal total = dayQtys.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                // 汇总行判定：窗口内有 ≥4 个有数据日、单日 ≥ 其余日均×20（正常日销波动远达不到 20 倍）
                if (days >= 4) {
                    BigDecimal maxDay = dayQtys.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
                    BigDecimal restAvg = total.subtract(maxDay)
                            .divide(BigDecimal.valueOf(days - 1), 4, RoundingMode.HALF_UP);
                    if (maxDay.compareTo(restAvg.multiply(BigDecimal.valueOf(20))) > 0) {
                        total = total.subtract(maxDay);
                        days = days - 1;
                        log.warn("SMART_ORDER_GEN PG 汇总行剔除 item_code={} window=[{},{}): 单日 {} = 其余日均×{} 以上",
                                itemCode, start, end, maxDay,
                                restAvg.compareTo(BigDecimal.ZERO) == 0 ? "∞" : maxDay.divide(restAvg, 1, RoundingMode.HALF_UP));
                    }
                }
                String mid = qmToMid.get(itemCode);
                if (mid == null) continue;
                if (days <= 0) continue;
                BigDecimal qty = convertToBase(mid, unit, total, ruleMap, convMap);
                PgSales cur = map.get(mid);
                if (cur == null) map.put(mid, new PgSales(qty, days));
                else map.put(mid, new PgSales(cur.qty().add(qty), cur.days() + days));
            }
        } catch (Exception e) {
            log.warn("SMART_ORDER_GEN PG 销量查询失败 storeId={} window=[{},{}): {}", store.getId(), start, end, e.getMessage());
        }
        return map;
    }

    /** 在途量 = 未终态订单（order_status 非 '已送达'/'已完成'）的订货量。
     * 注意：当前 PG dwd.purchase_order 仅同步了终态行，未送达订单不在库中 → 结果恒为空（在途=0）。
     * 原实现用 dwd.purchase（采购退货表，非到货表）作到货量做差值法，会把「全历史订货量」误当在途，
     * 导致 base 被巨量虚扣、全部物料不入单——已修正；待企迈报货单状态接入后再启用真实在途。 */
    private Map<String, BigDecimal> pgInTransitMap(StoreInfo store,
                                                   Map<String, Material> materialMap,
                                                   Map<String, MaterialInventoryRule> ruleMap,
                                                   Map<String, List<MaterialConversionRule>> convMap) {
        Map<String, BigDecimal> orderMap = new HashMap<>();
        Map<String, String> qmToMid = buildQmToMid(materialMap);
        if (!StringUtils.hasText(store.getCangkuid()) || qmToMid.isEmpty()) return orderMap;
        try {
            String inItems = qmToMid.keySet().stream().map(c -> "'" + c + "'").collect(Collectors.joining(","));
            String orderSql = "SELECT item_code, COALESCE(SUM(order_quantity),0) AS total_qty, " +
                    "COALESCE(unit, MAX(unit) OVER (PARTITION BY item_code)) AS unit " +
                    "FROM dwd.purchase_order WHERE warehouse_code = ? AND order_status NOT IN ('已送达','已完成') " +
                    "AND item_code IN (" + inItems + ") " +
                    "GROUP BY item_code, unit";
            for (Map<String, Object> row : pgJdbc.queryForList(orderSql, store.getCangkuid())) {
                String mid = qmToMid.get((String) row.get("item_code"));
                if (mid != null) orderMap.merge(mid,
                        convertToBase(mid, (String) row.get("unit"), toBigDecimal(row.get("total_qty")), ruleMap, convMap), BigDecimal::add);
            }
        } catch (Exception e) {
            log.warn("SMART_ORDER_GEN PG 在途查询失败 storeId={}: {}", store.getId(), e.getMessage());
        }
        return orderMap;
    }

    /**
     * 耗材订货节奏日均：近12周已送达订货量 ÷ max(有订货的周数, 2) ÷ 7（key = mid，基础单位）。
     * 对无销售记录的耗材类（打包袋/清洁/周边等），门店按消耗节奏订货、库存长期平衡 → 订货量≈消耗量。
     * 窗口取 12 周：近4周可能恰好无到货（订货周期长），避免耗材落入 default 0.5 的严重低估；
     * 分母用「有订货的周数」且下限 2 周（一次性大单摊 2 周而非 1 周，防单次备货量被当周消耗）——
     * 周期性订货（每周/两周/月订）不被摊薄低估，低频大单不被高估过头。
     * 窗口内无终态到货（已送达/已完成）的物料不在 map 中，调用方继续降级；
     * 在途/未送达订单由 inTransit 扣减逻辑处理。
     */
    private Map<String, BigDecimal> orderRhythmDailyMap(StoreInfo store, LocalDate weekStart,
                                                        Map<String, Material> materialMap,
                                                        Map<String, MaterialInventoryRule> ruleMap,
                                                        Map<String, List<MaterialConversionRule>> convMap) {
        Map<String, BigDecimal> map = new HashMap<>();
        Map<String, String> qmToMid = buildQmToMid(materialMap);
        if (!StringUtils.hasText(store.getCangkuid()) || qmToMid.isEmpty()) return map;
        LocalDate start = weekStart.minusDays(90);
        try {
            String inItems = qmToMid.keySet().stream().map(c -> "'" + c + "'").collect(Collectors.joining(","));
            String sql = "SELECT item_code, COALESCE(SUM(shipping_quantity),0) AS total_qty, " +
                    "COUNT(DISTINCT DATE_TRUNC('week', order_time)) AS weeks, " +
                    "COALESCE(unit, MAX(unit) OVER (PARTITION BY item_code)) AS unit " +
                    "FROM dwd.purchase_order WHERE warehouse_code = ? AND order_status IN ('已送达','已完成') " +
                    "AND order_time >= ?::timestamp AND order_time < ?::timestamp AND item_code IN (" + inItems + ") " +
                    "GROUP BY item_code, unit";
            List<Map<String, Object>> rows = pgJdbc.queryForList(sql, store.getCangkuid(), start.toString(), weekStart.toString());
            for (Map<String, Object> row : rows) {
                String mid = qmToMid.get((String) row.get("item_code"));
                if (mid == null) continue;
                int weeks = row.get("weeks") != null ? ((Number) row.get("weeks")).intValue() : 0;
                if (weeks <= 0) continue;
                BigDecimal qty = convertToBase(mid, (String) row.get("unit"), toBigDecimal(row.get("total_qty")), ruleMap, convMap);
                if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;
                map.put(mid, qty.divide(BigDecimal.valueOf(Math.max(weeks, 2)), 6, RoundingMode.HALF_UP)
                        .divide(BigDecimal.valueOf(7), 6, RoundingMode.HALF_UP));
            }
        } catch (Exception e) {
            log.warn("SMART_ORDER_GEN 订货节奏查询失败 storeId={} window=[{},{}): {}", store.getId(), start, weekStart, e.getMessage());
        }
        return map;
    }

    /** 回测：窗口内已送达订货量合计（基础单位，key = mid）——耗材类物料（无销售记录）的实际需求代理 */
    private Map<String, BigDecimal> orderInWindow(StoreInfo store, LocalDate start, LocalDate end,
                                                  Map<String, Material> materialMap,
                                                  Map<String, MaterialInventoryRule> ruleMap,
                                                  Map<String, List<MaterialConversionRule>> convMap) {
        Map<String, BigDecimal> map = new HashMap<>();
        Map<String, String> qmToMid = buildQmToMid(materialMap);
        if (!StringUtils.hasText(store.getCangkuid()) || qmToMid.isEmpty()) return map;
        try {
            String inItems = qmToMid.keySet().stream().map(c -> "'" + c + "'").collect(Collectors.joining(","));
            String sql = "SELECT item_code, COALESCE(SUM(shipping_quantity),0) AS total_qty, " +
                    "COALESCE(unit, MAX(unit) OVER (PARTITION BY item_code)) AS unit " +
                    "FROM dwd.purchase_order WHERE warehouse_code = ? AND order_status IN ('已送达','已完成') " +
                    "AND order_time >= ?::timestamp AND order_time < ?::timestamp AND item_code IN (" + inItems + ") " +
                    "GROUP BY item_code, unit";
            List<Map<String, Object>> rows = pgJdbc.queryForList(sql, store.getCangkuid(), start.toString(), end.toString());
            for (Map<String, Object> row : rows) {
                String mid = qmToMid.get((String) row.get("item_code"));
                if (mid == null) continue;
                map.merge(mid, convertToBase(mid, (String) row.get("unit"), toBigDecimal(row.get("total_qty")), ruleMap, convMap), BigDecimal::add);
            }
        } catch (Exception e) {
            log.warn("SMART_ORDER_GEN 回测订货量查询失败 storeId={} window=[{},{}): {}", store.getId(), start, end, e.getMessage());
        }
        return map;
    }

    /**
     * 回测：窗口内报损合计（loss_report，base_qty 优先；key = mid|storeId，不 ÷4）。
     * 两种来源都计入——报损物料即库存消耗，需求必须补上：
     * 日常报损(daily) 不走补发，completed 即损耗定性；
     * 到货报损(arrival) 审核确认后全程计入（registered 起，含厂家确认补发 confirmed_resend、
     * 门店收货 received/not_received）——报损是持续发生的消耗（周均），补发到店的货已含在
     * 盘点库存中由 −inventory 覆盖，无需冲抵。pending / pending_approval 未审核单不计。
     * 数量：base_qty 优先（登记时已按换算链折算最小单位）；确认时被改过数量
     * （orig_qty 非空，如牛油果泥）的用确认后的 input_qty，base_qty 未随确认更新。
     */
    private Map<String, BigDecimal> lossInWindow(String storeId, Collection<String> mids,
                                                 LocalDate startDate, LocalDate endDate) {
        Map<String, BigDecimal> map = new HashMap<>();
        if (mids == null || mids.isEmpty()) return map;
        try {
            String inPh = mids.stream().map(m -> "?").collect(Collectors.joining(","));
            // 报损口径（2026-08-25 业务确认）：
            //   日常报损 daily（每天做多做扔的半成品）→ 计入损耗：真实消耗，每天都会产生
            //   到货报损 arrival → 第一期不计算：仓库会补发，报损量被补货抵消，净影响≈0
            //   例外：蔬菜水果类到货报损不补发 → 仍计入损耗（loss_report 无分类字段，
            //   按 material.category ∈ {水果蔬菜, 水果蔬菜类} 判断）
            String sql = "SELECT material_id, " +
                    "COALESCE(SUM(CASE WHEN orig_qty IS NOT NULL AND orig_qty > 0 THEN input_qty " +
                    "WHEN base_qty IS NOT NULL AND base_qty > 0 THEN base_qty ELSE input_qty END),0) AS qty " +
                    "FROM loss_report WHERE store_id = ? AND del_flag = 0 AND occurred_date >= ? AND occurred_date < ? " +
                    "AND material_id IN (" + inPh + ") " +
                    "AND ((loss_type = 'daily' AND status = 'completed') " +
                    "OR (loss_type = 'arrival' AND status IN ('registered','confirmed_resend','received','not_received','completed') " +
                    "AND material_id IN (SELECT material_id FROM material WHERE category IN ('水果蔬菜','水果蔬菜类') AND del_flag = 0))) " +
                    "GROUP BY material_id";
            List<Object> args = new ArrayList<>();
            args.add(storeId); args.add(startDate.toString()); args.add(endDate.toString()); args.addAll(mids);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args.toArray());
            for (Map<String, Object> row : rows) {
                String mid = (String) row.get("material_id");
                if (mid == null) continue;
                map.put(mid + "|" + storeId, toBigDecimal(row.get("qty")));
            }
        } catch (Exception e) {
            log.warn("SMART_ORDER_GEN 报损查询失败 storeId={}: {}", storeId, e.getMessage());
        }
        return map;
    }

    /** 近4周报损周均 = lossInWindow ÷ 4 */
    private Map<String, BigDecimal> lossWeeklyMap(String storeId, Collection<String> mids,
                                                  LocalDate startDate, LocalDate endDate) {
        Map<String, BigDecimal> map = lossInWindow(storeId, mids, startDate, endDate);
        divide4(map);
        return map;
    }

    /** 回测：窗口内调货净值（transfer_order 完成单，调出−调入，折算基础单位；key = mid|storeId，净调出为正，不 ÷4） */
    private Map<String, BigDecimal> transferInWindow(String storeId, Collection<String> mids,
                                                    LocalDate startDate, LocalDate endDate) {
        Map<String, BigDecimal> map = new HashMap<>();
        if (mids == null || mids.isEmpty()) return map;
        try {
            String inPh = mids.stream().map(m -> "?").collect(Collectors.joining(","));
            String sql = "SELECT oi.material_id, oi.transfer_qty, oi.unit, oi.base_qty, o.from_store_id, o.to_store_id " +
                    "FROM transfer_order_item oi JOIN transfer_order o ON oi.transfer_id = o.id " +
                    "WHERE (o.from_store_id = ? OR o.to_store_id = ?) " +
                    "AND o.status IN ('completed','returned') AND o.received_at >= ? AND o.received_at < ? " +
                    "AND oi.material_id IN (" + inPh + ") " +
                    "AND oi.del_flag = 0 AND o.del_flag = 0";
            List<Object> args = new ArrayList<>();
            args.add(storeId); args.add(storeId); args.add(startDate.toString()); args.add(endDate.toString()); args.addAll(mids);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args.toArray());
            for (Map<String, Object> row : rows) {
                String mid = (String) row.get("material_id");
                if (mid == null) continue;
                BigDecimal rawQty = toBigDecimal(row.get("transfer_qty"));
                if (rawQty.compareTo(BigDecimal.ZERO) == 0) continue;
                BigDecimal qty = toBigDecimal(row.get("base_qty"));
                if (qty.compareTo(BigDecimal.ZERO) <= 0) qty = rawQty; // base_qty 缺失时用原值（调用侧无法换算，近似）
                boolean fromThis = storeId.equals(row.get("from_store_id"));
                boolean toThis = storeId.equals(row.get("to_store_id"));
                if (fromThis) map.merge(mid + "|" + storeId, qty, BigDecimal::add);       // 调出 → 净调出增加（多订）
                if (toThis) map.merge(mid + "|" + storeId, qty.negate(), BigDecimal::add); // 调入 → 减少
            }
        } catch (Exception e) {
            log.warn("SMART_ORDER_GEN 调货查询失败 storeId={}: {}", storeId, e.getMessage());
        }
        return map;
    }

    /** 近4周调货净值周均 = transferInWindow ÷ 4 */
    private Map<String, BigDecimal> transferWeeklyMap(String storeId, Collection<String> mids,
                                                     LocalDate startDate, LocalDate endDate) {
        Map<String, BigDecimal> map = transferInWindow(storeId, mids, startDate, endDate);
        divide4(map);
        return map;
    }

    /**
     * 回测：窗口内还货净值（transfer_return_record，折算基础单位；key = mid|storeId，净还出为正，不 ÷4）。
     * 仅处理 return_type='goods'（还货品=实物回流，方向与调货相反：原调出店收回货 → 少订 −，原调入店还走货 → 多订 +）；
     * return_type='money'（还钱）实物未动，不影响订货，不计。
     */
    private Map<String, BigDecimal> returnInWindow(String storeId, Collection<String> mids,
                                                  LocalDate startDate, LocalDate endDate,
                                                  Map<String, MaterialInventoryRule> ruleMap,
                                                  Map<String, List<MaterialConversionRule>> convMap) {
        Map<String, BigDecimal> map = new HashMap<>();
        if (mids == null || mids.isEmpty()) return map;
        try {
            String inPh = mids.stream().map(m -> "?").collect(Collectors.joining(","));
            String sql = "SELECT oi.material_id, oi.unit, rr.return_qty, o.from_store_id, o.to_store_id " +
                    "FROM transfer_return_record rr " +
                    "JOIN transfer_order_item oi ON rr.item_id = oi.id AND oi.del_flag = 0 " +
                    "JOIN transfer_order o ON rr.transfer_id = o.id AND o.del_flag = 0 " +
                    "WHERE (o.from_store_id = ? OR o.to_store_id = ?) " +
                    "AND rr.return_type = 'goods' AND rr.created_at >= ? AND rr.created_at < ? " +
                    "AND oi.material_id IN (" + inPh + ")";
            List<Object> args = new ArrayList<>();
            args.add(storeId); args.add(storeId); args.add(startDate.toString()); args.add(endDate.toString()); args.addAll(mids);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args.toArray());
            for (Map<String, Object> row : rows) {
                String mid = (String) row.get("material_id");
                if (mid == null) continue;
                BigDecimal rawQty = toBigDecimal(row.get("return_qty"));
                if (rawQty.compareTo(BigDecimal.ZERO) == 0) continue;
                BigDecimal qty = convertToBase(mid, (String) row.get("unit"), rawQty, ruleMap, convMap);
                if (storeId.equals(row.get("from_store_id"))) map.merge(mid + "|" + storeId, qty.negate(), BigDecimal::add); // 收回货 → 少订
                if (storeId.equals(row.get("to_store_id"))) map.merge(mid + "|" + storeId, qty, BigDecimal::add);           // 还出货 → 多订
            }
        } catch (Exception e) {
            log.warn("SMART_ORDER_GEN 还货查询失败 storeId={}: {}", storeId, e.getMessage());
        }
        return map;
    }

    /** 近4周还货净值周均 = returnInWindow ÷ 4 */
    private Map<String, BigDecimal> returnWeeklyMap(String storeId, Collection<String> mids,
                                                   LocalDate startDate, LocalDate endDate,
                                                   Map<String, MaterialInventoryRule> ruleMap,
                                                   Map<String, List<MaterialConversionRule>> convMap) {
        Map<String, BigDecimal> map = returnInWindow(storeId, mids, startDate, endDate, ruleMap, convMap);
        divide4(map);
        return map;
    }

    /** 回测：窗口内自购合计（self_purchase_material，折算基础单位；key = mid|storeId，减项，不 ÷4） */
    private Map<String, BigDecimal> selfPurchaseInWindow(String storeId, Collection<String> mids,
                                                         LocalDate startDate, LocalDate endDate,
                                                         Map<String, MaterialInventoryRule> ruleMap,
                                                         Map<String, List<MaterialConversionRule>> convMap) {
        Map<String, BigDecimal> map = new HashMap<>();
        if (mids == null || mids.isEmpty()) return map;
        try {
            String inPh = mids.stream().map(m -> "?").collect(Collectors.joining(","));
            String sql = "SELECT i.material_id, i.unit, COALESCE(SUM(i.purchase_qty),0) AS qty " +
                    "FROM self_purchase_material_item i JOIN self_purchase_material h ON h.biz_code = i.biz_code " +
                    "WHERE h.store_id = ? AND h.del_flag = 0 AND h.purchase_date >= ? AND h.purchase_date < ? " +
                    "AND i.material_id IN (" + inPh + ") AND i.del_flag = 0 " +
                    "GROUP BY i.material_id, i.unit";
            List<Object> args = new ArrayList<>();
            args.add(storeId); args.add(startDate.toString()); args.add(endDate.toString()); args.addAll(mids);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args.toArray());
            for (Map<String, Object> row : rows) {
                String mid = (String) row.get("material_id");
                if (mid == null) continue;
                BigDecimal qty = convertToBase(mid, (String) row.get("unit"), toBigDecimal(row.get("qty")), ruleMap, convMap);
                map.merge(mid + "|" + storeId, qty, BigDecimal::add);
            }
        } catch (Exception e) {
            log.warn("SMART_ORDER_GEN 自购查询失败 storeId={}: {}", storeId, e.getMessage());
        }
        return map;
    }

    /** 近4周自购周均 = selfPurchaseInWindow ÷ 4 */
    private Map<String, BigDecimal> selfPurchaseWeeklyMap(String storeId, Collection<String> mids,
                                                          LocalDate startDate, LocalDate endDate,
                                                          Map<String, MaterialInventoryRule> ruleMap,
                                                          Map<String, List<MaterialConversionRule>> convMap) {
        Map<String, BigDecimal> map = selfPurchaseInWindow(storeId, mids, startDate, endDate, ruleMap, convMap);
        divide4(map);
        return map;
    }

    /** 修正因子周均换算：窗口总值 ÷ 4 */
    private static void divide4(Map<String, BigDecimal> map) {
        for (Map.Entry<String, BigDecimal> e : map.entrySet()) {
            e.setValue(e.getValue().divide(BigDecimal.valueOf(4), 4, RoundingMode.HALF_UP));
        }
    }

    /**
     * 物料/规则/换算链上下文（summary.materialId 是 VARCHAR 外部编码，material.id 是 BIGINT 自增主键）。
     */
    private record MaterialCtx(Map<String, Material> materialMap,
                               Map<String, MaterialInventoryRule> ruleMap,
                               Map<String, List<MaterialConversionRule>> convMap,
                               Map<String, MaterialOrderConstraint> constraintMap) {}

    private MaterialCtx loadMaterialContext(List<TaskMaterialSummary> curSummaries) {
        List<String> matIds = curSummaries.stream()
                .map(TaskMaterialSummary::getMaterialId).filter(Objects::nonNull).distinct().toList();
        Map<String, Material> materialMap = matIds.isEmpty() ? Map.of()
                : materialMapper.selectList(new LambdaQueryWrapper<Material>()
                        .in(Material::getMaterialId, matIds))
                .stream().collect(Collectors.toMap(Material::getMaterialId, m -> m, (a, b) -> a));
        Map<String, MaterialInventoryRule> ruleMap = matIds.isEmpty() ? Map.of()
                : ruleMapper.selectList(new LambdaQueryWrapper<MaterialInventoryRule>()
                        .in(MaterialInventoryRule::getMaterialId, matIds))
                .stream().collect(Collectors.toMap(MaterialInventoryRule::getMaterialId, r -> r, (a, b) -> a));
        List<String> ruleIds = ruleMap.values().stream()
                .map(MaterialInventoryRule::getRuleId).filter(Objects::nonNull).distinct().toList();
        Map<String, List<MaterialConversionRule>> convMap = ruleIds.isEmpty() ? Map.of()
                : conversionMapper.selectList(new LambdaQueryWrapper<MaterialConversionRule>()
                        .in(MaterialConversionRule::getRuleId, ruleIds))
                .stream().collect(Collectors.groupingBy(MaterialConversionRule::getRuleId));
        // 企迈订货约束（起订量/倍数/限购，key = qm_code）：建议量取整用（酸角起订 12 瓶等）。
        // 表未建/查询异常时降级为空（不阻断生成）
        List<String> qmCodes = materialMap.values().stream()
                .map(Material::getQmCode).filter(StringUtils::hasText).distinct().toList();
        Map<String, MaterialOrderConstraint> constraintMap = Map.of();
        if (!qmCodes.isEmpty()) {
            try {
                constraintMap = orderConstraintMapper.selectList(new LambdaQueryWrapper<MaterialOrderConstraint>()
                                .in(MaterialOrderConstraint::getQmCode, qmCodes))
                        .stream().collect(Collectors.toMap(MaterialOrderConstraint::getQmCode, c -> c, (a, b) -> a));
            } catch (Exception e) {
                log.warn("SMART_ORDER_GEN 订货约束读取失败（表未建？），跳过起订量取整: {}", e.getMessage());
            }
        }
        return new MaterialCtx(materialMap, ruleMap, convMap, constraintMap);
    }

    /** qm_code → material.material_id 映射（PG item_code ↔ 自有物料编码） */
    private Map<String, String> buildQmToMid(Map<String, Material> materialMap) {
        Map<String, String> map = new HashMap<>();
        for (Material m : materialMap.values()) {
            if (m.getQmCode() != null && !m.getQmCode().isEmpty()) map.put(m.getQmCode(), m.getMaterialId());
        }
        return map;
    }

    /** 按换算链将 PG 单位数量折算到基础单位；无换算规则时原值近似（与差异模块口径一致） */
    private BigDecimal convertToBase(String mid, String unit, BigDecimal qty,
                                     Map<String, MaterialInventoryRule> ruleMap,
                                     Map<String, List<MaterialConversionRule>> convMap) {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        if (unit == null || unit.isBlank()) return qty;
        MaterialInventoryRule rule = ruleMap.get(mid);
        String baseUnit = rule != null && StringUtils.hasText(rule.getBaseUnit()) ? rule.getBaseUnit() : "";
        if (baseUnit.isEmpty() || baseUnit.equals(unit)) return qty;
        List<MaterialConversionRule> convs = convMap.getOrDefault(rule != null ? rule.getRuleId() : "", List.of());
        BigDecimal factor = ConversionFactorUtil.computeConversionFactor(unit, baseUnit, convs);
        return factor != null ? qty.multiply(factor) : qty;
    }

    private static BigDecimal toBigDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        try {
            return new BigDecimal(v.toString());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private static String trimNum(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }

    /** 订货约束取整说明（供 reason 展示）：给出调整前后与约束依据 */
    private static String buildConstraintNote(MaterialOrderConstraint oc, BigDecimal before, BigDecimal after) {
        StringBuilder sb = new StringBuilder("按企迈起订规则从 ").append(trimNum(before))
                .append(" 调整为 ").append(trimNum(after));
        String unit = StringUtils.hasText(oc.getOrderUnit()) ? oc.getOrderUnit() : "";
        sb.append(unit);
        if (oc.getOrderMultiple() != null && oc.getOrderMultiple().compareTo(BigDecimal.ZERO) > 0) {
            sb.append("（订货倍数 ").append(trimNum(oc.getOrderMultiple())).append(unit).append("）");
        } else if (oc.getMinOrderQty() != null && oc.getMinOrderQty().compareTo(BigDecimal.ZERO) > 0) {
            sb.append("（起订量 ").append(trimNum(oc.getMinOrderQty())).append(unit).append("）");
        }
        return sb.toString();
    }

    /** 带符号数量文本：正数前加 +（如 +8、−5） */
    private static String signed(BigDecimal v) {
        String s = v.stripTrailingZeros().toPlainString();
        return v.compareTo(BigDecimal.ZERO) >= 0 ? "+" + s : s;
    }

    /** 拉取窗口期内企迈报货单商品（productCode → 按单位分组的数量合计） */
    private Map<String, Map<String, BigDecimal>> fetchInbound(long qmaiStoreId,
                                                              LocalDateTime start, LocalDateTime end) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        var listResult = qmaiClient.getDeclareOrderList(qmaiStoreId, start.format(fmt), end.format(fmt), 1, 50);
        Map<String, Map<String, BigDecimal>> sum = new HashMap<>();
        int detailCount = 0;
        for (var rec : listResult.getRecords()) {
            if (detailCount >= 20) break;
            if (!StringUtils.hasText(rec.getDeclareNo())) continue;
            try {
                var detail = qmaiClient.getDeclareOrderDetail(rec.getDeclareNo());
                detailCount++;
                for (var p : detail.getProducts()) {
                    if (!StringUtils.hasText(p.getProductCode())) continue;
                    sum.computeIfAbsent(p.getProductCode(), k -> new HashMap<>())
                            .merge(p.getProductUnit() == null ? "" : p.getProductUnit(),
                                    BigDecimal.valueOf(p.getProductNum()), BigDecimal::add);
                }
            } catch (Exception e) {
                log.warn("SMART_ORDER_GEN 报货单详情拉取失败 declareNo={}: {}", rec.getDeclareNo(), e.getMessage());
            }
        }
        return sum;
    }

    /**
     * 拉取近 N 天店长在企迈端手动创建的报货单物料集合（source=1 且未取消/未驳回）。
     * 用于低频手动管理料抑制（2026-09-09 南姜案例）：店长刚在企迈手动订过（如 9/6 订 300g），
     * 系统又建议 117g → "刚买过还让我买"。source=2（智能订货 API 提交）不算手动；
     * 取消/驳回的单不算已订。上限 30 单（每单一次 detail 调用），失败降级返回空集。
     */
    private Set<String> fetchRecentManualDeclaredCodes(long qmaiStoreId, LocalDateTime nowTs, int windowDays) {
        Set<String> codes = new HashSet<>();
        if (qmaiStoreId <= 0 || windowDays <= 0) return codes;
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime start = nowTs.minusDays(windowDays);
        try {
            var listResult = qmaiClient.getDeclareOrderListAll(qmaiStoreId, start.format(fmt), nowTs.format(fmt), 1, 50);
            int detailCount = 0;
            for (var rec : listResult.getRecords()) {
                if (detailCount >= 30) break;
                if (!StringUtils.hasText(rec.getDeclareNo())) continue;
                if (rec.getSource() != 1) continue;              // 只认店长手动（source=1），系统 API 单不算
                if (rec.getOrderStatus() == 5 || rec.getOrderStatus() == 6) continue; // 取消/驳回不算已订
                try {
                    var detail = qmaiClient.getDeclareOrderDetail(rec.getDeclareNo());
                    detailCount++;
                    for (var p : detail.getProducts()) {
                        if (StringUtils.hasText(p.getProductCode())) codes.add(p.getProductCode());
                    }
                } catch (Exception e) {
                    log.warn("SMART_ORDER_GEN 手动订详情拉取失败 declareNo={}: {}", rec.getDeclareNo(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("SMART_ORDER_GEN 近{}天手动订查询失败 qmaiStoreId={}: {}", windowDays, qmaiStoreId, e.getMessage());
        }
        return codes;
    }

    /** 某物料窗口入库量折算到基础单位；单位无法换算时按基础单位近似（一期） */
    private BigDecimal inboundInBase(Map<String, Map<String, BigDecimal>> inboundMap,
                                     String qmCode, String baseUnit,
                                     List<MaterialConversionRule> convs) {
        if (!StringUtils.hasText(qmCode)) return BigDecimal.ZERO;
        Map<String, BigDecimal> byUnit = inboundMap.get(qmCode);
        if (byUnit == null) return BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> e : byUnit.entrySet()) {
            BigDecimal factor = ConversionFactorUtil.computeConversionFactor(e.getKey(), baseUnit, convs);
            if (factor == null) factor = BigDecimal.ONE;
            total = total.add(e.getValue().multiply(factor));
        }
        return total;
    }

    /**
     * 批2 估算库存用：盘点提交后已送达的订货量（PG dwd.purchase_order 已送达/已完成，
     * order_time ∈ (盘点提交时刻, 现在]，key = mid → 基础单位）。
     * 当前 PG 仅同步终态行 → 未送达订单不在结果中（=在途，由在途扣减处理）；
     * 典型场景：批1 周三订货 → 周五送达 → 周日批2 估算「盘点数 + 期间到货 − 预估消耗」时把批1 加回。
     */
    private Map<String, BigDecimal> deliveredAfterCount(StoreInfo store, Task current, LocalDateTime endTs,
                                                        Map<String, Material> materialMap,
                                                        Map<String, MaterialInventoryRule> ruleMap,
                                                        Map<String, List<MaterialConversionRule>> convMap) {
        Map<String, BigDecimal> map = new HashMap<>();
        LocalDateTime fromTs = current.getSubmittedAt();
        Map<String, String> qmToMid = buildQmToMid(materialMap);
        if (fromTs == null || !StringUtils.hasText(store.getCangkuid()) || qmToMid.isEmpty()) return map;
        try {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String inItems = qmToMid.keySet().stream().map(c -> "'" + c + "'").collect(Collectors.joining(","));
            String sql = "SELECT item_code, COALESCE(SUM(shipping_quantity),0) AS total_qty, " +
                    "COALESCE(unit, MAX(unit) OVER (PARTITION BY item_code)) AS unit " +
                    "FROM dwd.purchase_order WHERE warehouse_code = ? AND order_status IN ('已送达','已完成') " +
                    "AND order_time > ?::timestamp AND order_time <= ?::timestamp AND item_code IN (" + inItems + ") " +
                    "GROUP BY item_code, unit";
            List<Map<String, Object>> rows = pgJdbc.queryForList(sql, store.getCangkuid(),
                    fromTs.format(fmt), endTs.format(fmt));
            for (Map<String, Object> row : rows) {
                String mid = qmToMid.get((String) row.get("item_code"));
                if (mid == null) continue;
                BigDecimal qty = convertToBase(mid, (String) row.get("unit"),
                        toBigDecimal(row.get("total_qty")), ruleMap, convMap);
                if (qty.compareTo(BigDecimal.ZERO) > 0) map.merge(mid, qty, BigDecimal::add);
            }
        } catch (Exception e) {
            log.warn("SMART_ORDER_GEN 批2盘点后到货查询失败 storeId={}: {}", store.getId(), e.getMessage());
        }
        return map;
    }

    /** 盘点提交到现在的天数（小数，批2 预估消耗 = 日均 × 已过天数）；提交时间缺失/异常按 1 天兜底 */
    private static BigDecimal elapsedDaysOf(LocalDateTime from, LocalDateTime now) {
        if (from == null) return BigDecimal.ONE;
        long minutes = ChronoUnit.MINUTES.between(from, now);
        if (minutes <= 0) return BigDecimal.ONE;
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(1440), 1, RoundingMode.HALF_UP)
                .max(BigDecimal.valueOf(0.5)).min(BigDecimal.valueOf(7));
    }

    /** "2026-09-09 13:28:13" 或 "2026-09-09T…" → 取前 10 位解析日期；解析失败返回 null */
    private static LocalDate safeParseDate(String s) {
        if (s == null || s.length() < 10) return null;
        try {
            return LocalDate.parse(s.substring(0, 10));
        } catch (Exception e) {
            return null;
        }
    }

    /** CG 采购单号日期（CG+yyyyMMdd+序号）须在报货单日 ±1 天内才算同期采购；不可解析返回 false（不参与匹配） */
    private static boolean inCgWindow(String bizNo, LocalDate declareDate) {
        if (!StringUtils.hasText(bizNo) || !bizNo.startsWith("CG") || bizNo.length() < 10) return false;
        try {
            LocalDate d = LocalDate.parse(bizNo.substring(2, 10), DateTimeFormatter.ofPattern("yyyyMMdd"));
            return !d.isBefore(declareDate.minusDays(1)) && !d.isAfter(declareDate.plusDays(1));
        } catch (Exception e) {
            return false;
        }
    }

    /** 截止时间：订货日当天 sys_config 配置时刻（仅展示不强制）。
     * 批1 day = 周盘 deadline 日期（= 首个订货日）；批2 day = 第二订货日（生成当天） */
    private LocalDateTime orderDeadline(LocalDate day) {
        String time = getConfig(CFG_DEADLINE_TIME, "18:00:00");
        try {
            return LocalDateTime.of(day, LocalTime.parse(time));
        } catch (Exception e) {
            return LocalDateTime.of(day, LocalTime.of(18, 0));
        }
    }

    // ==================== 查询 ====================

    @Override
    public Page<SmartOrder> pageByStore(String storeId, int pageNum, int pageSize) {
        return orderMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<SmartOrder>()
                        .eq(SmartOrder::getStoreId, storeId)
                        .orderByDesc(SmartOrder::getWeekStartDate));
    }

    @Override
    public Page<SmartOrder> pageByStores(String openid, int pageNum, int pageSize) {
        List<String> storeIds = storeIdsOfUser(openid);
        if (storeIds.isEmpty()) return new Page<>(pageNum, pageSize);
        Page<SmartOrder> page = orderMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<SmartOrder>()
                        .in(SmartOrder::getStoreId, storeIds)
                        .in(SmartOrder::getStatus, "pending", "submit_failed", "success")
                        .orderByDesc(SmartOrder::getWeekStartDate));
        enrichQmPayStatus(page.getRecords());
        return page;
    }

    /**
     * 对已生成报货单的订单实时查询企迈支付状态（不落库）。
     * 按门店一次拉取近 90 天报货单列表建 declareNo 索引，再逐单填充；
     * 调用次数=门店数而非订单数；查询失败降级为 null（前端兜底显示"已订货"）。
     */
    private void enrichQmPayStatus(List<SmartOrder> records) {
        if (records == null || records.isEmpty()) return;
        Map<String, List<SmartOrder>> byStore = records.stream()
                .filter(o -> "success".equals(o.getStatus()) && StringUtils.hasText(o.getQmaiDeclareNo()))
                .collect(Collectors.groupingBy(SmartOrder::getStoreId));
        if (byStore.isEmpty()) return;
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();
        String start = now.minusDays(90).format(fmt);
        String end = now.format(fmt);
        for (Map.Entry<String, List<SmartOrder>> e : byStore.entrySet()) {
            String sid = e.getKey();
            Long qmaiStoreId = null;
            try {
                StoreInfo s = storeService.getStoreById(sid);
                if (s != null) qmaiStoreId = s.getQmaiStoreId();
            } catch (Exception ignored) {
            }
            if (qmaiStoreId == null) continue;
            try {
                // 全状态查询（含待支付），翻页凑齐
                List<QmaiClient.DeclareOrderSummary> all = new ArrayList<>();
                for (int page = 1; page <= 5; page++) {
                    var listResult = qmaiClient.getDeclareOrderListAll(qmaiStoreId, start, end, page, 100);
                    List<QmaiClient.DeclareOrderSummary> recs = listResult.getRecords();
                    if (recs == null || recs.isEmpty()) break;
                    all.addAll(recs);
                    if (all.size() >= listResult.getTotal()) break;
                }
                Map<String, QmaiClient.DeclareOrderSummary> byNo = new HashMap<>();
                for (QmaiClient.DeclareOrderSummary rec : all) {
                    if (StringUtils.hasText(rec.getDeclareNo())) byNo.put(rec.getDeclareNo(), rec);
                }
                for (SmartOrder o : e.getValue()) {
                    QmaiClient.DeclareOrderSummary rec = byNo.get(o.getQmaiDeclareNo());
                    if (rec != null) {
                        o.setQmOrderStatus(rec.getOrderStatus());
                        o.setQmPayStatus(rec.getPayStatus());
                    }
                }
            } catch (Exception ex) {
                log.warn("SMART_ORDER 列表支付状态查询失败 storeId={}: {}", sid, ex.getMessage());
            }
        }
    }

    @Override
    public Map<String, Object> detail(Long id) {
        SmartOrder order = orderMapper.selectById(id);
        if (order == null) throw new BusinessException("订货单不存在");
        var user = UserContextHolder.get();
        if (user != null && !isUserStore(user.getOpenid(), order.getStoreId())) {
            throw new BusinessException(403, "无权查看该门店的订货单");
        }
        List<SmartOrderItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<SmartOrderItem>()
                        .eq(SmartOrderItem::getOrderId, id)
                        .orderByAsc(SmartOrderItem::getSortNo));

        // 待确认/提交失败：实时查询企迈总仓可用库存，下单前比对（失败静默降级 qmStock=null）
        if ("pending".equals(order.getStatus()) || "submit_failed".equals(order.getStatus())) {
            enrichCentralStock(items);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("order", order);
        result.put("items", items);
        // 已生成企迈报货单：实时查询企迈支付状态（不落库，失败静默降级为 null）
        if ("success".equals(order.getStatus()) && StringUtils.hasText(order.getQmaiDeclareNo())) {
            QmaiClient.DeclareOrderDetail qmDetail = null;
            try {
                qmDetail = qmaiClient.getDeclareOrderDetail(order.getQmaiDeclareNo());
                result.put("qmOrderStatus", qmDetail.getOrderStatus());
                result.put("qmPayStatus", qmDetail.getPayStatus());
            } catch (Exception e) {
                log.warn("SMART_ORDER 支付状态查询失败 id={} declareNo={}: {}",
                        id, order.getQmaiDeclareNo(), e.getMessage());
            }
            // 报货单拆单：订货单（按配送中心）+ 采购单，各自状态独立（失败降级返回空列表）
            result.put("related", fetchRelatedOrders(order.getQmaiDeclareNo(), qmDetail));
        }
        return result;
    }

    /**
     * 查询报货单拆单关联（订货单 DH* / 采购申请 CGSQ* / 采购单 CG*）。
     * 报货单按配送中心拆单：每个仓一个订货单，单号与状态各自独立。
     * 每个单附加物料明细（items）：
     * ① 本地已有对应入库单（bizNo=单号）→ 用入库单明细（实际到货：物料/应收/已收/单价）；
     * ② 无入库单 → 用报货单明细兜底（declare/order/detail products，物料与数量一致，未按仓拆分）。
     * 任一企迈调用失败 → 降级返回空列表，不阻塞详情页。
     */
    private Map<String, Object> fetchRelatedOrders(String declareNo, QmaiClient.DeclareOrderDetail qmDetail) {
        Map<String, Object> related = new LinkedHashMap<>();
        List<Map<String, Object>> requireOrders = new ArrayList<>();
        List<Map<String, Object>> purchases = new ArrayList<>();
        related.put("requireOrders", requireOrders);
        related.put("purchases", purchases);
        if (!StringUtils.hasText(declareNo)) return related;
        // 报货单已取消(5)/已驳回(6)：拆单关联与收货入口无意义 → 返回空（顶部状态卡已说明原因）。
        // 2026-09-09 修复：此前取消单下方仍会因物料重叠误挂历史批次采购单的"去收货"（见下方 CG 匹配）
        if (qmDetail != null && qmDetail.getOrderStatus() >= 5) {
            return related;
        }

        // 报货单明细（物料名/数量/单位/单价/业绩归属 performanceCode=配送中心或供应商）
        List<Map<String, Object>> declareItems = new ArrayList<>();
        if (qmDetail != null && qmDetail.getProducts() != null) {
            for (QmaiClient.DeclareProduct p : qmDetail.getProducts()) {
                Map<String, Object> it = new LinkedHashMap<>();
                it.put("productName", p.getProductName());
                it.put("productNum", p.getProductNum());
                it.put("productUnit", p.getProductUnit());
                it.put("productSpec", p.getProductSpec());
                it.put("price", p.getPrice());
                it.put("productCode", p.getProductCode());
                it.put("performanceCode", p.getPerformanceCode());
                declareItems.add(it);
            }
        }

        try {
            var rel = qmaiClient.getDeclareOrderByNo(declareNo);
            List<String> requireNos = rel.getRequireNoList() == null ? List.of() : rel.getRequireNoList();
            List<String> applyNos = rel.getPurchaseApplyNoList() == null ? List.of() : rel.getPurchaseApplyNoList();
            List<String> purchaseNos = rel.getPurchaseNoList() == null ? List.of() : rel.getPurchaseNoList();

            // 先查询全部订货单，收集配送中心编码集合（物料 performanceCode 匹配 warehouseNo 即归属该单）
            Set<String> requireWarehouseNos = new HashSet<>();
            List<QmaiClient.RequireOrderSummary> requireOrderList = new ArrayList<>();
            for (String rn : requireNos) {
                try {
                    var ro = qmaiClient.getRequireOrderByNo(rn);
                    requireOrderList.add(ro);
                    if (StringUtils.hasText(ro.getWarehouseNo())) requireWarehouseNos.add(ro.getWarehouseNo());
                } catch (Exception e) {
                    log.warn("SMART_ORDER 订货单查询失败 requireNo={}: {}", rn, e.getMessage());
                }
            }
            for (var ro : requireOrderList) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("requireNo", ro.getRequireNo());
                m.put("warehouseNo", ro.getWarehouseNo());
                m.put("warehouseName", ro.getWarehouseName());
                m.put("orderStatus", ro.getOrderStatus());
                m.put("amount", ro.getAmount());
                m.put("orderAt", ro.getOrderAt());
                // 本地入库单关联（bizNo = 订货单号）：收货入口直达；同号多单（分批发货）合并为一条
                List<InboundOrder> inbounds = findInboundOrdersByNo(ro.getRequireNo());
                if (inbounds.size() > 1) {
                    // 分批发货：明细按物料合并展示；收货入口取未收货（pending）的入库单优先
                    m.put("items", mergeInboundItems(inbounds));
                    InboundOrder inbound = inbounds.stream()
                            .filter(o -> "pending".equals(o.getLocalStatus()))
                            .findFirst().orElse(inbounds.get(0));
                    m.put("inboundId", inbound.getId());
                    m.put("inboundNo", inbound.getInboundNo());
                    m.put("inboundStatus", inbound.getLocalStatus());
                } else {
                    // 无入库单时按配送中心精确拆分（performanceCode==warehouseNo）
                    m.put("items", attachOrderItems(ro.getRequireNo(), declareItems, ro.getWarehouseNo()));
                    if (!inbounds.isEmpty()) {
                        InboundOrder inbound = inbounds.get(0);
                        m.put("inboundId", inbound.getId());
                        m.put("inboundNo", inbound.getInboundNo());
                        m.put("inboundStatus", inbound.getLocalStatus());
                    }
                }
                requireOrders.add(m);
            }
            // 采购物料 = 不归属任何订货单配送中心的报货单物料（供应商渠道/无业绩归属）
            List<Map<String, Object>> purchaseItems = collectPurchaseItems(declareItems, requireWarehouseNos);

            // 采购单：企迈拆单接口不返回 CG 采购单（CG 是采购申请 CGSQ 的后继单，不挂在报货单下）。
            // 用报货单采购物料 productCode 匹配本地采购入库单（inboundType=3）明细，命中则以本地采购单展示（收货入口直达）。
            Set<String> purchaseCodes = new HashSet<>();
            for (Map<String, Object> it : purchaseItems) {
                if (StringUtils.hasText((String) it.get("productCode"))) {
                    purchaseCodes.add((String) it.get("productCode"));
                }
            }
            Set<String> cgCoveredCodes = new HashSet<>();
            String curStoreId = UserContextHolder.get().getStoreId();
            if (!purchaseCodes.isEmpty() && StringUtils.hasText(curStoreId)) {
                List<InboundOrder> localPurchaseOrders = inboundOrderMapper.selectList(
                        new LambdaQueryWrapper<InboundOrder>()
                                .eq(InboundOrder::getStoreId, curStoreId)
                                .eq(InboundOrder::getInboundType, 3)
                                .orderByDesc(InboundOrder::getId));
                // 同一企迈采购单号（bizNo）可能对应多个入库单（分批发货）→ 按 bizNo 合并为一条，
                // 明细合并展示，未收货的入库单优先作为收货入口
                Map<String, List<InboundOrder>> byBizNo = new LinkedHashMap<>();
                List<InboundOrder> noBiz = new ArrayList<>();
                // 2026-09-09 修复：CG 采购单仅按 productCode 重叠匹配会误关联历史批次——
                // 例：9/7 采购单(柠檬/芒果)与 9/9 报货单物料重叠 → 9/9 取消单下方挂出 9/7 的"去收货"。
                // 采购单生成日 ≈ 报货单日（±1 天），按 CG 单号日期收紧；日期不可解析的行不参与匹配（宁缺勿错）
                LocalDate declareDate = qmDetail != null && StringUtils.hasText(qmDetail.getCreatedAt())
                        ? safeParseDate(qmDetail.getCreatedAt()) : null;
                for (InboundOrder cg : localPurchaseOrders) {
                    if (declareDate != null && !inCgWindow(cg.getBizNo(), declareDate)) continue;
                    List<InboundOrderItem> cgItems = inboundOrderItemMapper.selectList(
                            new LambdaQueryWrapper<InboundOrderItem>()
                                    .eq(InboundOrderItem::getInboundOrderId, cg.getId()));
                    boolean hit = cgItems.stream().anyMatch(i -> purchaseCodes.contains(i.getProductCode()));
                    if (!hit) continue;
                    cgItems.forEach(i -> cgCoveredCodes.add(i.getProductCode()));
                    if (StringUtils.hasText(cg.getBizNo())) {
                        byBizNo.computeIfAbsent(cg.getBizNo(), k -> new ArrayList<>()).add(cg);
                    } else {
                        noBiz.add(cg);
                    }
                }
                for (List<InboundOrder> group : byBizNo.values()) {
                    purchases.add(buildPurchaseFromInbounds(group));
                }
                for (InboundOrder cg : noBiz) {
                    purchases.add(buildPurchaseFromInbounds(List.of(cg)));
                }
            }
            // 企迈拆单的采购申请/采购单：物料排除已被本地 CG 覆盖的部分，全部覆盖则不再显示（已转采购单）
            for (String pn : applyNos) {
                List<Map<String, Object>> items = attachOrderItems(pn, purchaseItems, null).stream()
                        .filter(it -> !cgCoveredCodes.contains(it.get("productCode"))).toList();
                if (items.isEmpty()) continue;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("purchaseNo", pn);
                m.put("type", "apply");
                m.put("items", items);
                purchases.add(m);
            }
            for (String pn : purchaseNos) {
                List<Map<String, Object>> items = attachOrderItems(pn, purchaseItems, null).stream()
                        .filter(it -> !cgCoveredCodes.contains(it.get("productCode"))).toList();
                if (items.isEmpty()) continue;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("purchaseNo", pn);
                m.put("type", "purchase");
                m.put("items", items);
                // 采购单接单即发货/待入库：本地采购入库单（inboundType=3，bizNo=CG*）关联收货
                InboundOrder inbound = findInboundByNo(pn);
                if (inbound != null) {
                    m.put("inboundId", inbound.getId());
                    m.put("inboundNo", inbound.getInboundNo());
                    m.put("inboundStatus", inbound.getLocalStatus());
                }
                purchases.add(m);
            }
        } catch (Exception e) {
            log.warn("SMART_ORDER 拆单关联查询失败 declareNo={}: {}", declareNo, e.getMessage());
        }
        return related;
    }

    /** 按单号查本地入库单（bizNo 或 sourceRequireNo），同号多单（分批发货）全部返回 */
    private List<InboundOrder> findInboundOrdersByNo(String bizNo) {
        if (!StringUtils.hasText(bizNo)) return List.of();
        return inboundOrderMapper.selectList(new LambdaQueryWrapper<InboundOrder>()
                .eq(InboundOrder::getBizNo, bizNo)
                .or().eq(InboundOrder::getSourceRequireNo, bizNo)
                .orderByDesc(InboundOrder::getId));
    }

    /** 按单号查单个本地入库单（bizNo 或 sourceRequireNo），仅取一条 */
    private InboundOrder findInboundByNo(String bizNo) {
        List<InboundOrder> list = findInboundOrdersByNo(bizNo);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 同一采购单号（bizNo）的多个入库单 → 前端采购单条目：
     * 明细全部合并展示；收货入口取未收货（pending）的入库单优先。
     */
    private Map<String, Object> buildPurchaseFromInbounds(List<InboundOrder> inbounds) {
        Map<String, Object> m = new LinkedHashMap<>();
        InboundOrder primary = inbounds.stream()
                .filter(o -> "pending".equals(o.getLocalStatus()))
                .findFirst().orElse(inbounds.get(0));
        m.put("purchaseNo", primary.getBizNo());
        m.put("type", "purchase");
        m.put("inboundId", primary.getId());
        m.put("inboundNo", primary.getInboundNo());
        m.put("inboundStatus", primary.getLocalStatus());
        m.put("items", mergeInboundItems(inbounds));
        return m;
    }

    /** 同一单号的多个入库单明细 → 按物料合并为一行（应收/实收相加），分批发货完整展示 */
    private List<Map<String, Object>> mergeInboundItems(List<InboundOrder> inbounds) {
        Map<String, Map<String, Object>> byCode = new LinkedHashMap<>();
        for (InboundOrder ib : inbounds) {
            List<InboundOrderItem> ibs = inboundOrderItemMapper.selectList(
                    new LambdaQueryWrapper<InboundOrderItem>()
                            .eq(InboundOrderItem::getInboundOrderId, ib.getId()));
            for (InboundOrderItem it : ibs) {
                String key = StringUtils.hasText(it.getProductCode())
                        ? it.getProductCode()
                        : (StringUtils.hasText(it.getProductName()) ? it.getProductName() : "#" + it.getId());
                Map<String, Object> acc = byCode.computeIfAbsent(key, k -> {
                    Map<String, Object> mm = new LinkedHashMap<>();
                    mm.put("productName", it.getProductName());
                    mm.put("productUnit", it.getProductUnit());
                    mm.put("productSpec", it.getProductSpec());
                    mm.put("productNum", BigDecimal.ZERO);
                    mm.put("receivedQty", BigDecimal.ZERO);
                    return mm;
                });
                acc.put("productNum", ((BigDecimal) acc.get("productNum"))
                        .add(it.getProductNum() != null ? it.getProductNum() : BigDecimal.ZERO));
                acc.put("receivedQty", ((BigDecimal) acc.get("receivedQty"))
                        .add(it.getReceivedQty() != null ? it.getReceivedQty() : BigDecimal.ZERO));
            }
        }
        List<Map<String, Object>> merged = new ArrayList<>();
        for (Map<String, Object> acc : byCode.values()) {
            BigDecimal rq = (BigDecimal) acc.get("receivedQty");
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("productName", acc.get("productName"));
            out.put("productNum", ((BigDecimal) acc.get("productNum")).doubleValue());
            out.put("productUnit", acc.get("productUnit"));
            out.put("productSpec", acc.get("productSpec"));
            out.put("receivedQty", rq.doubleValue());
            out.put("received", rq.compareTo(BigDecimal.ZERO) > 0 ? 1 : 0);
            merged.add(out);
        }
        return merged;
    }

    /**
     * 单号对应物料明细：
     * 1. 有入库单 → 本地入库单明细优先（物料/应收/已收/单价，精确）；
     * 2. 无入库单 → warehouseNo 非空时按配送中心过滤报货单物料（performanceCode==warehouseNo），
     *    为空（采购单/查询失败）返回传入的 candidateItems 全量。
     */
    private List<Map<String, Object>> attachOrderItems(String bizNo, List<Map<String, Object>> candidateItems,
                                                       String warehouseNo) {
        InboundOrder inbound = findInboundByNo(bizNo);
        if (inbound != null) {
            List<InboundOrderItem> inboundItems = inboundOrderItemMapper.selectList(
                    new LambdaQueryWrapper<InboundOrderItem>()
                            .eq(InboundOrderItem::getInboundOrderId, inbound.getId()));
            if (!inboundItems.isEmpty()) {
                return toInboundItemMaps(inboundItems);
            }
        }
        if (candidateItems == null || candidateItems.isEmpty() || !StringUtils.hasText(warehouseNo)) {
            return candidateItems == null ? new ArrayList<>() : candidateItems;
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> it : candidateItems) {
            String perf = (String) it.get("performanceCode");
            if (warehouseNo.equals(perf)) list.add(it);
        }
        return list;
    }

    /** 本地入库单明细转前端 map（物料/应收/已收标记） */
    private List<Map<String, Object>> toInboundItemMaps(List<InboundOrderItem> inboundItems) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (InboundOrderItem it : inboundItems) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("productName", it.getProductName());
            m.put("productNum", it.getProductNum() != null ? it.getProductNum().doubleValue() : 0);
            m.put("productUnit", it.getProductUnit());
            m.put("productSpec", it.getProductSpec());
            m.put("price", it.getPrice() != null ? it.getPrice().doubleValue() : null);
            m.put("receivedQty", it.getReceivedQty() != null ? it.getReceivedQty().doubleValue() : 0);
            m.put("received", it.getReceived());
            list.add(m);
        }
        return list;
    }

    /**
     * 采购物料集合 = 不归属任何订货单配送中心的报货单物料（供应商渠道或无业绩归属）。
     */
    private List<Map<String, Object>> collectPurchaseItems(List<Map<String, Object>> declareItems, Set<String> requireWarehouseNos) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (declareItems == null) return list;
        for (Map<String, Object> it : declareItems) {
            String perf = (String) it.get("performanceCode");
            if (!StringUtils.hasText(perf) || !requireWarehouseNos.contains(perf)) list.add(it);
        }
        return list;
    }

    /**
     * 查询企迈总仓实时库存并附加到明细（qmStock=可用库存 availableQuantity，库存单位口径）。
     * qmStockUnit = 企迈返回的实际单位，缺失时回退行内 qmStockUnit（生成时存的规则 stockUnit），
     * 注意与下单单位（stockUnit=orderUnit）区分——库存展示/校验统一走 qmStockUnit。
     * 总仓编码未配置（sys_config smart_order_central_warehouse_no）或接口失败时静默降级：
     * qmStock 保持 null，前端不展示库存标注、不拦截下单。
     */
    private void enrichCentralStock(List<SmartOrderItem> items) {
        if (items == null || items.isEmpty()) return;
        List<String> warehouseNos = parseWarehouseNos(getConfig(CFG_CENTRAL_WAREHOUSE_NO, ""));
        if (warehouseNos.isEmpty()) return;
        List<String> codes = items.stream().map(SmartOrderItem::getQmCode)
                .filter(StringUtils::hasText).distinct().toList();
        if (codes.isEmpty()) return;
        try {
            // 分批查询并合并：warehouseNoList 单次最多 5 个仓，仓库数超过时按 5 个一批
            Map<String, Double> availMap = new HashMap<>();
            Map<String, String> unitMap = new HashMap<>();
            for (List<String> batch : partition(warehouseNos, 5)) {
                for (QmaiClient.WarehouseProductStock s : qmaiClient.getWarehouseProductStock(batch, codes)) {
                    if (!StringUtils.hasText(s.getProductCode())) continue;
                    availMap.merge(s.getProductCode(), s.getAvailableQuantity(), Double::sum);
                    if (StringUtils.hasText(s.getStockUnit()) && !unitMap.containsKey(s.getProductCode())) {
                        unitMap.put(s.getProductCode(), s.getStockUnit());
                    }
                }
            }
            for (SmartOrderItem it : items) {
                // 水果蔬菜类不显示总仓库存（生鲜不记库存，恒 0，显示无意义）
                if (isNoStockCheck(it.getCategory())) continue;
                Double avail = availMap.get(it.getQmCode());
                if (avail != null) {
                    it.setQmStock(BigDecimal.valueOf(avail));
                    it.setQmStockUnit(unitMap.getOrDefault(it.getQmCode(), it.getQmStockUnit()));
                }
            }
            log.info("SMART_ORDER 总仓库存查询成功 orderId={} 仓库数={} 品项={} 命中={}",
                    items.get(0).getOrderId(), warehouseNos.size(), codes.size(), availMap.size());
        } catch (Exception e) {
            log.warn("SMART_ORDER 总仓库存查询失败 orderId={}: {}", items.get(0).getOrderId(), e.getMessage());
        }
    }

    /** 解析总仓编码配置：逗号分隔（中英文逗号均可），支持超过 5 个（调用方按批查询） */
    private List<String> parseWarehouseNos(String cfg) {
        if (!StringUtils.hasText(cfg)) return List.of();
        return Arrays.stream(cfg.split("[,，]"))
                .map(String::trim).filter(StringUtils::hasText).toList();
    }

    /** 按 size 分批，接口参数（如 warehouseNoList）超过上限时拆分调用 */
    private static <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            batches.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return batches;
    }

    // ==================== 新增物料 ====================

    @Override
    public List<String> materialCategories() {
        return materialMapper.selectList(new LambdaQueryWrapper<Material>()
                        .select(Material::getCategory)
                        .isNotNull(Material::getCategory)
                        .ne(Material::getCategory, "")
                        .isNotNull(Material::getQmCode)
                        .ne(Material::getQmCode, "")
                        .notLike(Material::getCategory, "半成品")
                        .notLike(Material::getCategory, "淘汰")
                        .groupBy(Material::getCategory)
                        .orderByAsc(Material::getCategory))
                .stream().map(Material::getCategory).filter(Objects::nonNull).toList();
    }

    @Override
    public List<Map<String, Object>> searchMaterials(String keyword, String category) {
        LambdaQueryWrapper<Material> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(Material::getMaterialName, keyword)
                    .or().like(Material::getQmCode, keyword));
        }
        if (StringUtils.hasText(category)) {
            qw.eq(Material::getCategory, category);
        }
        // 半成品类、淘汰品类不下单
        qw.notLike(Material::getCategory, "半成品").notLike(Material::getCategory, "淘汰")
                .orderByAsc(Material::getMaterialName).last("LIMIT 200");
        List<Material> materials = materialMapper.selectList(qw);
        if (materials.isEmpty()) return List.of();

        List<String> matIds = materials.stream().map(Material::getMaterialId)
                .filter(Objects::nonNull).distinct().toList();
        Map<String, MaterialInventoryRule> ruleMap = matIds.isEmpty() ? Map.of()
                : ruleMapper.selectList(new LambdaQueryWrapper<MaterialInventoryRule>()
                        .in(MaterialInventoryRule::getMaterialId, matIds))
                .stream().collect(Collectors.toMap(MaterialInventoryRule::getMaterialId, r -> r, (a, b) -> a));
        List<String> ruleIds = ruleMap.values().stream()
                .map(MaterialInventoryRule::getRuleId).filter(Objects::nonNull).distinct().toList();
        Map<String, List<MaterialConversionRule>> convMap = ruleIds.isEmpty() ? Map.of()
                : conversionMapper.selectList(new LambdaQueryWrapper<MaterialConversionRule>()
                        .in(MaterialConversionRule::getRuleId, ruleIds))
                .stream().collect(Collectors.groupingBy(MaterialConversionRule::getRuleId));

        // 批量附加总仓可用库存（availableQuantity 跨仓累加；水果蔬菜类不显示；查询失败降级不附加）
        List<String> codes = materials.stream().map(Material::getQmCode)
                .filter(StringUtils::hasText).distinct().toList();
        Map<String, Double> stockMap = Map.of();
        if (!codes.isEmpty()) {
            try {
                stockMap = fetchCentralStockMap(codes);
            } catch (Exception e) {
                log.warn("searchMaterials 总仓库存查询失败，降级不附加: err={}", e.getMessage());
            }
        }

        List<Map<String, Object>> list = new ArrayList<>();
        for (Material m : materials) {
            if (!StringUtils.hasText(m.getQmCode())) continue; // 无企迈编码无法下单
            MaterialInventoryRule rule = ruleMap.get(m.getMaterialId());
            List<MaterialConversionRule> convs = convMap.getOrDefault(rule != null ? rule.getRuleId() : "", List.of());
            String baseUnit = rule != null && StringUtils.hasText(rule.getBaseUnit()) ? rule.getBaseUnit() : "";
            // 下单单位：优先订货单位，其次库存单位，兜底基础单位
            String orderUnit = orderUnitOf(rule, baseUnit);
            // 企迈库存单位（qmStock 数值配套）：保持规则表库存单位口径，不随下单单位切换
            String qmUnit = rule != null && StringUtils.hasText(rule.getStockUnit()) ? rule.getStockUnit() : baseUnit;
            BigDecimal factor = ConversionFactorUtil.computeConversionFactor(orderUnit, baseUnit, convs);
            // 展示/下单单价 = 订货单价优先（order_price），缺失回退 unit_price×换算系数
            BigDecimal unitPrice = orderUnitPrice(rule, factor);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", m.getId());
            item.put("materialName", m.getMaterialName());
            item.put("spec", m.getSpec() != null ? m.getSpec() : "");
            item.put("category", m.getCategory() != null ? m.getCategory() : "");
            item.put("qmCode", m.getQmCode());
            item.put("stockUnit", orderUnit);
            item.put("baseUnit", baseUnit);
            item.put("unitPrice", unitPrice);
            if (!isNoStockCheck(m.getCategory())) {
                Double avail = stockMap.get(m.getQmCode());
                if (avail != null) {
                    item.put("qmStock", avail);
                    item.put("qmStockUnit", qmUnit);
                }
            }
            list.add(item);
        }
        return list;
    }

    @Override
    public SmartOrderItem addItem(Long id, SmartOrderAddItemReq req) {
        var user = UserContextHolder.get();
        if (user == null) throw new BusinessException(401, "未登录");

        SmartOrder order = orderMapper.selectById(id);
        if (order == null) throw new BusinessException("订货单不存在");
        if (!order.isActionable()) throw new BusinessException("仅待确认或提交失败的订货单可新增物料");
        if (!isUserStore(user.getOpenid(), order.getStoreId())) {
            throw new BusinessException(403, "无权操作该门店的订货单");
        }

        BigDecimal qty = req.getQty();
        if (qty.compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException("数量必须大于 0");
        if (qty.stripTrailingZeros().scale() > 0) throw new BusinessException("数量必须为整数（仓库按整件发货）");

        Material material = materialMapper.selectById(req.getMaterialId());
        if (material == null) throw new BusinessException("物料不存在");
        if (!StringUtils.hasText(material.getQmCode())) throw new BusinessException("该物料无企迈编码，无法订货");

        // 已在单中的物料 = 更新建议数量（upsert，支持详情页删掉后重新加入）
        SmartOrderItem existing = itemMapper.selectOne(new LambdaQueryWrapper<SmartOrderItem>()
                .eq(SmartOrderItem::getOrderId, id)
                .eq(SmartOrderItem::getMaterialId, req.getMaterialId())
                .last("LIMIT 1"));
        if (existing != null) {
            // 库存校验：qty 是订货单位口径，企迈总仓库存是库存单位口径，需要换算链
            MaterialInventoryRule exRule = ruleMapper.selectOne(new LambdaQueryWrapper<MaterialInventoryRule>()
                    .eq(MaterialInventoryRule::getMaterialId, existing.getMaterialId()));
            List<MaterialConversionRule> exConvs = exRule != null
                    ? conversionMapper.selectList(new LambdaQueryWrapper<MaterialConversionRule>()
                            .eq(MaterialConversionRule::getRuleId, exRule.getRuleId()))
                    : List.of();
            validateItemStock(existing.getQmCode(), existing.getCategory(),
                    existing.getStockUnit(), existing.getQmStockUnit(), existing.getBaseUnit(),
                    exConvs, existing.getMaterialName(), qty);
            BigDecimal oldQty = existing.getSuggestQty() != null ? existing.getSuggestQty() : BigDecimal.ZERO;
            BigDecimal delta = qty.subtract(oldQty);
            BigDecimal deltaAmount = existing.getUnitPrice() != null
                    ? delta.multiply(existing.getUnitPrice()).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            existing.setSuggestQty(qty);
            existing.setReason("手动调整");
            transactionTemplate.executeWithoutResult(ts -> {
                itemMapper.updateById(existing);
                if (delta.compareTo(BigDecimal.ZERO) != 0) {
                    orderMapper.update(null, new LambdaUpdateWrapper<SmartOrder>()
                            .eq(SmartOrder::getId, id)
                            .setSql("total_qty = total_qty + " + delta.toPlainString())
                            .setSql("suggest_amount = suggest_amount + " + deltaAmount.toPlainString()));
                }
            });
            return existing;
        }

        // 单价/单位：与生成逻辑同口径（rule.unit_price 为基础单位单价，换算为每订货单位单价）
        MaterialInventoryRule rule = ruleMapper.selectOne(new LambdaQueryWrapper<MaterialInventoryRule>()
                .eq(MaterialInventoryRule::getMaterialId, material.getMaterialId()));
        List<MaterialConversionRule> convs = rule != null
                ? conversionMapper.selectList(new LambdaQueryWrapper<MaterialConversionRule>()
                        .eq(MaterialConversionRule::getRuleId, rule.getRuleId()))
                : List.of();
        String baseUnit = rule != null && StringUtils.hasText(rule.getBaseUnit()) ? rule.getBaseUnit() : "";
        String orderUnit = orderUnitOf(rule, baseUnit);
        String qmUnit = rule != null && StringUtils.hasText(rule.getStockUnit()) ? rule.getStockUnit() : baseUnit;
        BigDecimal factor = ConversionFactorUtil.computeConversionFactor(orderUnit, baseUnit, convs);
        // 单价 = 订货单价优先（order_price），缺失回退 unit_price×换算系数
        BigDecimal unitPrice = orderUnitPrice(rule, factor);

        SmartOrderItem last = itemMapper.selectOne(new LambdaQueryWrapper<SmartOrderItem>()
                .eq(SmartOrderItem::getOrderId, id)
                .orderByDesc(SmartOrderItem::getSortNo)
                .last("LIMIT 1"));
        int sortNo = last != null && last.getSortNo() != null ? last.getSortNo() + 1 : 1;

        SmartOrderItem item = new SmartOrderItem();
        item.setOrderId(id);
        item.setMaterialId(material.getId());
        item.setMaterialName(material.getMaterialName());
        item.setSpec(material.getSpec());
        item.setCategory(material.getCategory());
        item.setQmCode(material.getQmCode());
        // stock_unit 列存订货单位（下单口径）；qmStockUnit 存库存单位（企迈库存口径，供校验/展示）
        item.setStockUnit(orderUnit);
        item.setQmStockUnit(qmUnit);
        item.setBaseUnit(baseUnit);
        item.setUnitPrice(unitPrice);
        item.setSuggestQty(qty);
        item.setReason("手动添加");
        item.setSortNo(sortNo);

        validateItemStock(item.getQmCode(), item.getCategory(),
                item.getStockUnit(), item.getQmStockUnit(), item.getBaseUnit(),
                convs, item.getMaterialName(), qty);

        BigDecimal addAmount = unitPrice != null
                ? qty.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        transactionTemplate.executeWithoutResult(ts -> {
            itemMapper.insert(item);
            orderMapper.update(null, new LambdaUpdateWrapper<SmartOrder>()
                    .eq(SmartOrder::getId, id)
                    .setSql("item_count = item_count + 1")
                    .setSql("total_qty = total_qty + " + qty.toPlainString())
                    .setSql("suggest_amount = suggest_amount + " + addAmount.toPlainString()));
        });
        return item;
    }

    @Override
    public void deleteItem(Long id, Long itemId) {
        var user = UserContextHolder.get();
        if (user == null) throw new BusinessException(401, "未登录");

        SmartOrder order = orderMapper.selectById(id);
        if (order == null) throw new BusinessException("订货单不存在");
        if (!order.isActionable()) throw new BusinessException("仅待确认或提交失败的订货单可删除物料");
        if (!isUserStore(user.getOpenid(), order.getStoreId())) {
            throw new BusinessException(403, "无权操作该门店的订货单");
        }

        SmartOrderItem item = itemMapper.selectById(itemId);
        if (item == null || !id.equals(item.getOrderId())) throw new BusinessException("明细不存在");

        BigDecimal qty = item.getSuggestQty() != null ? item.getSuggestQty() : BigDecimal.ZERO;
        BigDecimal amount = item.getUnitPrice() != null
                ? qty.multiply(item.getUnitPrice()).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        transactionTemplate.executeWithoutResult(ts -> {
            itemMapper.deleteById(itemId); // 软删除，重新添加会走新增分支
            orderMapper.update(null, new LambdaUpdateWrapper<SmartOrder>()
                    .eq(SmartOrder::getId, id)
                    .setSql("item_count = GREATEST(item_count - 1, 0)")
                    .setSql("total_qty = GREATEST(total_qty - " + qty.toPlainString() + ", 0)")
                    .setSql("suggest_amount = GREATEST(suggest_amount - " + amount.toPlainString() + ", 0)"));
        });
    }

    @Override
    public Map<String, Long> overview(String storeId) {
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("pending", countByStatus(storeId, "pending", null));
        result.put("submitFailed", countByStatus(storeId, "submit_failed", null));
        result.put("syncing", countByStatus(storeId, "syncing", null));
        result.put("success", countByStatus(storeId, "success", null));
        return result;
    }

    @Override
    public List<Map<String, Object>> overviewByStores(String openid) {
        List<Map<String, Object>> stores = staffService.findStoresByOpenid(openid);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> s : stores) {
            String sid = (String) s.get("storeId");
            Long pending = countByStatus(sid, "pending", null);
            Long failed = countByStatus(sid, "submit_failed", null);
            long total = (pending != null ? pending : 0) + (failed != null ? failed : 0);
            if (total > 0) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("storeId", sid);
                item.put("storeName", s.get("storeName"));
                item.put("pending", total);
                result.add(item);
            }
        }
        return result;
    }

    @Override
    public Map<String, Long> overviewTotal(String openid) {
        List<String> storeIds = storeIdsOfUser(openid);
        Long pending;
        if (storeIds.isEmpty()) {
            pending = 0L;
        } else {
            pending = orderMapper.selectCount(new LambdaQueryWrapper<SmartOrder>()
                    .in(SmartOrder::getStoreId, storeIds)
                    .in(SmartOrder::getStatus, "pending", "submit_failed"));
        }
        return Map.of("pending", pending != null ? pending : 0L);
    }

    private Long countByStatus(String storeId, String status, String status2) {
        return orderMapper.selectCount(new LambdaQueryWrapper<SmartOrder>()
                .eq(SmartOrder::getStoreId, storeId)
                .and(status2 != null, w -> w.eq(SmartOrder::getStatus, status).or().eq(SmartOrder::getStatus, status2))
                .eq(status2 == null, SmartOrder::getStatus, status));
    }

    // ==================== 确认 ====================

    /**
     * 下单硬校验：确认数量（>0 的明细）不得超过企迈总仓可用库存。
     * 总仓编码未配置（sys_config smart_order_central_warehouse_no）或接口查询失败时跳过校验。
     */
    /** 水果蔬菜类不参与总仓库存拦截（企迈不记录生鲜库存，显示照旧、下单不拦） */
    private static boolean isNoStockCheck(String category) {
        return category != null && category.contains("水果蔬菜");
    }

    /** 批量查询总仓可用库存（availableQuantity 跨仓累加）；查询异常向上抛，由调用方决定降级 */
    private Map<String, Double> fetchCentralStockMap(List<String> codes) {
        if (codes == null || codes.isEmpty()) return Map.of();
        List<String> warehouseNos = parseWarehouseNos(getConfig(CFG_CENTRAL_WAREHOUSE_NO, ""));
        if (warehouseNos.isEmpty()) return Map.of();
        Map<String, Double> stockMap = new HashMap<>();
        for (List<String> batch : partition(warehouseNos, 5)) {
            for (QmaiClient.WarehouseProductStock s : qmaiClient.getWarehouseProductStock(batch, codes)) {
                if (StringUtils.hasText(s.getProductCode())) {
                    stockMap.merge(s.getProductCode(), s.getAvailableQuantity(), Double::sum);
                }
            }
        }
        return stockMap;
    }

    /** 下单单位：优先订货单位（order_unit=xinfo usageUnit），其次库存单位（stock_unit），兜底基础单位 */
    private String orderUnitOf(MaterialInventoryRule rule, String baseUnit) {
        if (rule != null && StringUtils.hasText(rule.getOrderUnit())) return rule.getOrderUnit();
        if (rule != null && StringUtils.hasText(rule.getStockUnit())) return rule.getStockUnit();
        return baseUnit;
    }

    /**
     * 下单单价（订货单位口径）：
     * ① 优先 rule.order_price —— 订货单价（xinfo standardCostPrice 同步，按订货单位整件口径，
     *    业务确认以此价下单，2026-09-09；如 PP700细吸管 = 10 元/包）；
     * ② order_price 缺失（约 62/491 物料）→ 回退 unit_price（基础单位单价）× 换算系数折算到订货单位。
     */
    private static BigDecimal orderUnitPrice(MaterialInventoryRule rule, BigDecimal factor) {
        if (rule != null && rule.getOrderPrice() != null
                && rule.getOrderPrice().compareTo(BigDecimal.ZERO) > 0) {
            return rule.getOrderPrice().setScale(2, RoundingMode.HALF_UP);
        }
        if (rule == null || rule.getUnitPrice() == null) return null;
        BigDecimal p = rule.getUnitPrice();
        if (factor != null) p = p.multiply(factor).setScale(2, RoundingMode.HALF_UP);
        return p;
    }

    /**
     * 把 qty（订货单位口径）换算成库存单位口径，用于与企迈总仓库存（库存单位口径）比较。
     * 换算链缺失或单位相同 → 原值返回（老行/无规则物料保持历史行为）。
     */
    private BigDecimal toStockUnitQty(BigDecimal qty, String orderUnit, String stockUnit,
                                      String baseUnit, List<MaterialConversionRule> convs) {
        if (qty == null || !StringUtils.hasText(orderUnit) || !StringUtils.hasText(stockUnit)
                || orderUnit.equals(stockUnit)) {
            return qty;
        }
        BigDecimal fOrder = ConversionFactorUtil.computeConversionFactor(orderUnit, baseUnit, convs);
        BigDecimal fStock = ConversionFactorUtil.computeConversionFactor(stockUnit, baseUnit, convs);
        if (fOrder == null || fStock == null) return qty;
        return qty.multiply(fOrder).divide(fStock, 6, RoundingMode.HALF_UP);
    }

    /**
     * 添加物料校验：qty 是订货单位（orderUnit）口径，企迈总仓可用库存是库存单位（qmStockUnit）口径，
     * 先经基础单位换算链换算后再比较（水果蔬菜类豁免；查询失败降级放行）。
     */
    private void validateItemStock(String qmCode, String category, String orderUnit, String qmStockUnit,
                                   String baseUnit, List<MaterialConversionRule> convs,
                                   String materialName, BigDecimal qty) {
        if (!StringUtils.hasText(qmCode) || isNoStockCheck(category)) return;
        try {
            Double avail = fetchCentralStockMap(List.of(qmCode)).get(qmCode);
            if (avail == null) {
                throw new BusinessException("物料「" + materialName + "」总仓无可用库存，请调整后再添加");
            }
            BigDecimal qtyStock = toStockUnitQty(qty, orderUnit, qmStockUnit, baseUnit, convs);
            if (qtyStock.doubleValue() > avail) {
                throw new BusinessException("物料「" + materialName + "」添加数量超过总仓可用库存（仅 "
                        + trimNum(avail) + (StringUtils.hasText(qmStockUnit) ? " " + qmStockUnit : "")
                        + "），请调整后再添加");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("addItem 总仓库存校验失败，降级放行: qmCode={} err={}", qmCode, e.getMessage());
        }
    }

    /**
     * 下单前库存校验：qty（订货单位口径）与企迈总仓可用库存（库存单位口径）换算后比较。
     * 提示单位统一用 qmStockUnit（库存单位），老行（qmStockUnit 为空）回退 stockUnit。
     */
    private void validateCentralStock(List<SmartOrderItem> items, Map<Long, BigDecimal> qtyMap) {
        if (items == null || items.isEmpty()) return;
        List<String> warehouseNos = parseWarehouseNos(getConfig(CFG_CENTRAL_WAREHOUSE_NO, ""));
        if (warehouseNos.isEmpty()) return;
        List<SmartOrderItem> ordered = items.stream()
                .filter(it -> qtyMap.getOrDefault(it.getId(), BigDecimal.ZERO).compareTo(BigDecimal.ZERO) > 0)
                .filter(it -> !isNoStockCheck(it.getCategory()))
                .toList();
        if (ordered.isEmpty()) return;
        List<String> codes = ordered.stream().map(SmartOrderItem::getQmCode)
                .filter(StringUtils::hasText).distinct().toList();
        if (codes.isEmpty()) return;
        try {
            // 批量查规则 + 换算链：qty 按订货单位，企迈库存按库存单位，需经基础单位换算
            // smart_order_item.material_id 是 Long（material.id BIGINT 体系），转 String 匹配规则表
            List<String> matIds = ordered.stream().map(SmartOrderItem::getMaterialId)
                    .filter(Objects::nonNull).map(String::valueOf).distinct().toList();
            Map<String, MaterialInventoryRule> ruleMap = matIds.isEmpty() ? Map.of()
                    : ruleMapper.selectList(new LambdaQueryWrapper<MaterialInventoryRule>()
                            .in(MaterialInventoryRule::getMaterialId, matIds))
                    .stream().collect(Collectors.toMap(MaterialInventoryRule::getMaterialId, r -> r, (a, b) -> a));
            List<String> ruleIds = ruleMap.values().stream().map(MaterialInventoryRule::getRuleId)
                    .filter(StringUtils::hasText).distinct().toList();
            Map<String, List<MaterialConversionRule>> convMap = ruleIds.isEmpty() ? Map.of()
                    : conversionMapper.selectList(new LambdaQueryWrapper<MaterialConversionRule>()
                            .in(MaterialConversionRule::getRuleId, ruleIds))
                    .stream().collect(Collectors.groupingBy(MaterialConversionRule::getRuleId));

            Map<String, Double> stockMap = fetchCentralStockMap(codes);
            for (SmartOrderItem it : ordered) {
                BigDecimal qty = qtyMap.get(it.getId());
                Double avail = stockMap.get(it.getQmCode());
                if (avail == null) {
                    // 仓库无该品项库存记录 → 视为 0，超量
                    if (qty.compareTo(BigDecimal.ZERO) > 0) {
                        throw new BusinessException("物料「" + it.getMaterialName() + "」总仓无可用库存，请调整后再提交");
                    }
                } else {
                    MaterialInventoryRule rule = ruleMap.get(String.valueOf(it.getMaterialId()));
                    String convKey = rule != null ? rule.getRuleId() : "";
                    BigDecimal qtyStock = toStockUnitQty(qty, it.getStockUnit(), it.getQmStockUnit(),
                            it.getBaseUnit(), convMap.getOrDefault(convKey, List.of()));
                    String showUnit = StringUtils.hasText(it.getQmStockUnit()) ? it.getQmStockUnit()
                            : StringUtils.hasText(it.getStockUnit()) ? it.getStockUnit() : "";
                    if (qtyStock.doubleValue() > avail) {
                        throw new BusinessException("物料「" + it.getMaterialName() + "」下单数量超过总仓可用库存（仅 "
                                + trimNum(avail) + " " + showUnit
                                + "），请调整后再提交");
                    }
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("SMART_ORDER 下单库存校验失败，跳过校验 orderId={}: {}", items.get(0).getOrderId(), e.getMessage());
        }
    }

    private static String trimNum(double v) {
        return v % 1 == 0 ? String.valueOf((long) v) : String.valueOf(v);
    }

    @Override
    public SmartOrder confirm(Long id, SmartOrderConfirmReq req) {
        var user = UserContextHolder.get();
        if (user == null) throw new BusinessException(401, "未登录");

        SmartOrder order = orderMapper.selectById(id);
        if (order == null) throw new BusinessException("订货单不存在");

        // 卡死恢复：syncing 超 5 分钟视为上次提交中断，重置为 submit_failed 允许重试
        if ("syncing".equals(order.getStatus()) && order.getUpdatedAt() != null
                && order.getUpdatedAt().isBefore(LocalDateTime.now().minusMinutes(5))) {
            order.setStatus("submit_failed");
            order.setSubmitError("上次同步中断，可重新提交");
            orderMapper.updateById(order);
        }

        order.assertCanTransition("confirm");

        // 访问校验：确认时使用单据所属门店（跨店列表进入时不切换上下文）
        if (!isUserStore(user.getOpenid(), order.getStoreId())) {
            throw new BusinessException(403, "无权操作该门店的订货单");
        }

        // 明细校验：itemId 必须是本单明细，数量 ≥ 0（0 = 本次不订）
        List<SmartOrderItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<SmartOrderItem>()
                        .eq(SmartOrderItem::getOrderId, id)
                        .orderByAsc(SmartOrderItem::getSortNo));
        Set<Long> itemIds = items.stream().map(SmartOrderItem::getId).collect(Collectors.toSet());
        for (SmartOrderConfirmReq.ConfirmItemReq r : req.getItems()) {
            if (!itemIds.contains(r.getItemId())) throw new BusinessException("明细不存在: " + r.getItemId());
            if (r.getQty() == null || r.getQty().compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException("确认数量不能为负数");
            }
            if (r.getQty().stripTrailingZeros().scale() > 0) {
                throw new BusinessException("确认数量必须为整数（仓库按整件发货）");
            }
        }
        Map<Long, BigDecimal> qtyMap = req.getItems().stream()
                .collect(Collectors.toMap(SmartOrderConfirmReq.ConfirmItemReq::getItemId,
                        SmartOrderConfirmReq.ConfirmItemReq::getQty));

        // 总仓库存校验：确认数量不得超过企迈总仓可用库存（配置缺失/查询失败时跳过，不阻塞下单）
        validateCentralStock(items, qtyMap);

        // 阶段A（短事务）：抢占状态 + 写入确认数量，提交后释放（远程调用绝不持事务）
        transactionTemplate.executeWithoutResult(ts -> {
            int rows = orderMapper.update(null, new LambdaUpdateWrapper<SmartOrder>()
                    .eq(SmartOrder::getId, id)
                    .in(SmartOrder::getStatus, "pending", "submit_failed")
                    .set(SmartOrder::getStatus, "syncing")
                    .set(SmartOrder::getConfirmedBy, user.getOpenid())
                    .set(SmartOrder::getConfirmedAt, LocalDateTime.now())
                    .setSql("sync_attempts = sync_attempts + 1"));
            if (rows != 1) throw new BusinessException("该订货单已被确认或状态已变化");
            for (SmartOrderItem it : items) {
                BigDecimal qty = qtyMap.get(it.getId());
                if (qty == null) continue; // 未提交的明细视为 0，不订
                itemMapper.update(null, new LambdaUpdateWrapper<SmartOrderItem>()
                        .eq(SmartOrderItem::getId, it.getId())
                        .set(SmartOrderItem::getConfirmedQty, qty.setScale(0)));
            }
        });

        // 阶段B（无事务）：构建商品清单并真实提交企迈
        boolean success = false;
        String declareNo = null;
        String errorMsg = null;
        BigDecimal realAmount = null; // 订单金额（本地订货价口径，下单成功后备写 suggest_amount）
        List<SmartOrderItem> ordered = items.stream()
                .filter(it -> qtyMap.getOrDefault(it.getId(), BigDecimal.ZERO).compareTo(BigDecimal.ZERO) > 0)
                .toList();
        try {
            if (ordered.isEmpty()) {
                success = true; // 全部数量为 0：本次不订货，直接完成
            } else {
                // 单价 = 明细快照价（本地订货价：material_inventory_rule.unit_price × 换算系数，生成时已折算到订货单位）。
                // 2026-09-09 修订：此前用企迈总仓 costPrice（成本价）覆盖，实测企迈成本价与订货价不符
                // （如 PP700细吸管 本地 0.05/根 = 10/包，企迈成本 2.6435/包）→ 订单金额按成本价严重偏差；
                // 订货按本地订货价下单，明细单价即下单单价。
                String warehouseNo = resolveWarehouseNo(order.getStoreId());
                List<QmaiClient.DeclareCreateProduct> products = ordered.stream().map(it -> {
                    QmaiClient.DeclareCreateProduct p = new QmaiClient.DeclareCreateProduct();
                    p.setProductCode(it.getQmCode());
                    p.setProductNum(qtyMap.get(it.getId()).doubleValue());
                    p.setPrice(it.getUnitPrice());
                    return p;
                }).toList();
                // 订单金额 = Σ 订货价×数量（本地口径，下单成功后备写 suggest_amount 保持一致）
                realAmount = products.stream()
                        .map(p -> p.getPrice() != null
                                ? p.getPrice().multiply(BigDecimal.valueOf(p.getProductNum())) : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                // 企迈创建报货单：onlinePay=1（线上支付）；orderAttribute=1
                // （实测 orderAttribute=0 会报 160098「单据属性值错误」，=1 成功——该商户单据属性枚举无 0）
                QmaiClient.DeclareCreateResult result = qmaiClient.createDeclareOrder(
                        warehouseNo, 1, 1, user.getEmployeeName(), products);
                if (result.getDeclareNoList() != null && !result.getDeclareNoList().isEmpty()
                        && (result.getErrorList() == null || result.getErrorList().isEmpty())) {
                    success = true;
                    declareNo = result.getDeclareNoList().get(0);
                } else {
                    errorMsg = "企迈未返回报货单号: " + result.getErrorList();
                }
            }
        } catch (Exception e) {
            errorMsg = e.getMessage();
        }

        // 阶段C（短事务）：落终态（lambda 需 final，先拷贝）
        final boolean syncSuccess = success;
        final String syncDeclareNo = declareNo;
        final String syncError = errorMsg;
        final BigDecimal syncRealAmount = realAmount;
        transactionTemplate.executeWithoutResult(ts -> {
            SmartOrder update = new SmartOrder();
            update.setId(id);
            if (syncSuccess) {
                update.setStatus("success");
                update.setQmaiDeclareNo(syncDeclareNo);
                update.setSubmitError(null);
                // 金额按本地订货价口径回写（与报货单一致）；明细单价即快照价，无需改动
                if (syncRealAmount != null) update.setSuggestAmount(syncRealAmount);
            } else {
                update.setStatus("submit_failed");
                update.setSubmitError(truncate(syncError, 1000));
            }
            orderMapper.updateById(update);
        });

        SmartOrder fresh = orderMapper.selectById(id);
        if (!syncSuccess) throw new BusinessException("企迈提交失败: " + truncate(syncError, 200));
        return fresh;
    }

    /** 解析企迈仓库编码：先查 cangkuid（外部API同步的仓库ID），再按最近 60 天报货单自愈回写，仍无则报错 */
    private String resolveWarehouseNo(String storeId) {
        String wn = null;
        try {
            wn = jdbcTemplate.queryForObject(
                    "SELECT cangkuid FROM store_info WHERE store_id=? AND del_flag=0", String.class, storeId);
        } catch (Exception ignored) {
        }
        if (StringUtils.hasText(wn)) return wn;

        StoreInfo s = storeService.getStoreById(storeId);
        if (s != null && s.getQmaiStoreId() != null) {
            try {
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                LocalDateTime now = LocalDateTime.now();
                var list = qmaiClient.getDeclareOrderList(s.getQmaiStoreId(),
                        now.minusDays(60).format(fmt), now.format(fmt), 1, 5);
                for (var rec : list.getRecords()) {
                    if (StringUtils.hasText(rec.getStoreWarehouseNo())) {
                        jdbcTemplate.update(
                                "UPDATE store_info SET cangkuid=?, updated_at=NOW() WHERE store_id=?",
                                rec.getStoreWarehouseNo(), storeId);
                        log.info("SMART_ORDER 自愈回写 warehouseNo storeId={} warehouseNo={}",
                                storeId, rec.getStoreWarehouseNo());
                        return rec.getStoreWarehouseNo();
                    }
                }
            } catch (Exception e) {
                log.warn("SMART_ORDER 自愈查询报货单失败 storeId={}: {}", storeId, e.getMessage());
            }
        }
        throw new BusinessException("该门店尚未配置企迈仓库编码，请联系总部");
    }

    // ==================== 工具方法 ====================

    private List<String> storeIdsOfUser(String openid) {
        return staffService.findStoresByOpenid(openid).stream()
                .map(s -> (String) s.get("storeId")).filter(Objects::nonNull).toList();
    }

    private boolean isUserStore(String openid, String storeId) {
        return staffService.findStoresByOpenid(openid).stream()
                .anyMatch(s -> storeId.equals(s.get("storeId")));
    }

    /** 读 sys_config，失败/为空返回默认值 */
    private String getConfig(String key, String defaultValue) {
        try {
            String val = jdbcTemplate.queryForObject(
                    "SELECT config_value FROM sys_config WHERE config_key=? LIMIT 1", String.class, key);
            return StringUtils.hasText(val) ? val : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static int parseInt(String val, int defaultValue) {
        try {
            return Integer.parseInt(val.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
