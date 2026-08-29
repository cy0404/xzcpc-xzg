package com.xzcpc.mp.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.mp.dto.DailyLossCreateReq;
import com.xzcpc.mp.entity.LossReport;

import java.util.List;
import java.util.Map;

public interface LossReportService {

    /** 门店新建报损（到货验收/旧日常单物料） */
    LossReport create(LossReport report, String openid);

    /** 门店新建日常多物料报损 */
    LossReport createDaily(DailyLossCreateReq req);

    /** 门店报损列表 */
    Page<LossReport> pageByStore(String storeId, String lossType, int pageNum, int pageSize);

    /** 报损详情 */
    LossReport detail(Long id, String storeId);

    /** 总部全门店列表 */
    Page<LossReport> pageAll(String storeId, String supervisorName, String lossType, String status, String startDate, String endDate, int pageNum, int pageSize);

    /** 获取容器列表 */
    List<Map<String, Object>> listContainers(String storeId);

    /** 名下所有门店报损待处理数（非 closed） */
    List<Map<String, Object>> overviewByStores(String openid);

    /** 按ID查询（通用） */
    LossReport getById(Long id);

    /** 按ID更新（通用） */
    void updateById(LossReport report);

    /** 待审批列表 */
    Page<LossReport> pageApprovalByStore(String storeId, int pageNum, int pageSize);

    /** 审批通过：店员提交 → 下一步 */
    LossReport approve(Long id, String storeId);

    /** 审批拒绝：店员提交 → rejected */
    void rejectApproval(Long id, String storeId);

    /** 批量审批：action=approve|reject，返回 {success, skipped}（删除/非本店/已处理/并发冲突均计入 skipped） */
    Map<String, Object> batchApprove(List<Long> ids, String action, String storeId);

    /** 已收货 */
    void receive(Long id, String storeId, String operator, String remark);

    /** 未收到货 */
    void notReceive(Long id, String storeId, String operator, String remark);

    /** 操作日志 */
    List<Map<String, Object>> getLogs(Long reportId);

    /** 更新日常多物料报损 */
    LossReport updateDaily(Long id, DailyLossCreateReq req);

    /** 删除日常报损（软删除） */
    void deleteDaily(Long id, String storeId, String operator);
}
