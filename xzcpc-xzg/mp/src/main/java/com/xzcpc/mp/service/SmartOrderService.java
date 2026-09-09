package com.xzcpc.mp.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.mp.dto.SmartOrderAddItemReq;
import com.xzcpc.mp.dto.SmartOrderConfirmReq;
import com.xzcpc.mp.entity.SmartOrder;
import com.xzcpc.mp.entity.SmartOrderItem;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** P1+P2B: 智能订货服务 */
public interface SmartOrderService {

    /** 为全部门店生成本周第 1 批建议订货单（每日 3:00 定时扫描：仅本周周盘已提交的门店生成），返回 {generated, skipped, failedStores} */
    Map<String, Object> generateAll();

    /** 为全部门店生成第 2 批建议订货单（拆单：第二订货日 9:00 job 调用，仅当今天是本店第二订货日时生成），返回 {generated, skipped, failedStores} */
    Map<String, Object> generateSecondBatchAll();

    /** 周盘任务提交后补触发：以该已提交任务为物料池生成建议单（幂等），返回是否新建 */
    boolean generateByWeeklyTask(Integer taskId);

    /** P2B 回测：以指定历史周为基准模拟生成（不落库），与窗口内实际消耗对比评估预测准确性 */
    Map<String, Object> backtest(Integer taskId, LocalDate weekStart);

    /** 单门店订货单分页（按周倒序，含全部状态） */
    Page<SmartOrder> pageByStore(String storeId, int pageNum, int pageSize);

    /** 跨门店可操作单据分页（pending/submit_failed，按周倒序） */
    Page<SmartOrder> pageByStores(String openid, int pageNum, int pageSize);

    /** 单据详情 {order, items}，带所属门店访问校验 */
    Map<String, Object> detail(Long id);

    /** 二级分类列表（仅含可下单物料：有企迈编码且分类非空） */
    List<String> materialCategories();

    /** 搜索可添加物料（仅返回有企迈编码的），[{id, materialName, spec, category, qmCode, stockUnit, baseUnit, unitPrice}] */
    List<Map<String, Object>> searchMaterials(String keyword, String category);

    /** 新增/调整物料明细（仅 pending/submit_failed 可操作；已在单中的物料更新其建议数量，服务端按规则换算单价/单位），返回明细 */
    SmartOrderItem addItem(Long id, SmartOrderAddItemReq req);

    /** 删除物料明细（软删除；仅 pending/submit_failed 可操作），同步扣减单据 item_count/total_qty/suggest_amount */
    void deleteItem(Long id, Long itemId);

    /** 确认订货：两阶段状态机（短事务抢占 → 无事务调企迈 → 短事务落终态），失败抛业务异常 */
    SmartOrder confirm(Long id, SmartOrderConfirmReq req);

    /** 按门店统计待确认单据数，仅返回 pending>0 的门店 [{storeId, storeName, pending}] */
    List<Map<String, Object>> overviewByStores(String openid);

    /** 单门店各状态数量 {pending, syncing, success, submitFailed} */
    Map<String, Long> overview(String storeId);

    /** 该用户全部门店的待确认单据总数 {pending} */
    Map<String, Long> overviewTotal(String openid);
}
