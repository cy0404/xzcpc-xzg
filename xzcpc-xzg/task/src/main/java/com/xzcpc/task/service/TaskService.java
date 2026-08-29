package com.xzcpc.task.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.task.dto.MaterialUpdateReq;
import com.xzcpc.task.dto.TaskCreateRequest;
import com.xzcpc.task.dto.TaskUpdateRequest;
import com.xzcpc.task.entity.Task;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface TaskService {

    String getLatestMonth(); // 月盘任务服务接口

    Page<Task> page(String storeId, String supervisorName, String status, String keyword, String templateName,
                    String taskMonth, String taskType, int pageNum, int pageSize);

    void create(Task task); // 创建月盘任务（单门店）

    int batchCreate(TaskCreateRequest request); // 批量创建月盘任务（多门店）

    /**
     * 自动生成周盘任务（P2-A）：按门店配置的周盘点日，为盘点日在今天/明天的门店生成本周任务。
     * 幂等：同店同周未提交任务已存在则跳过；无启用的 weekly 模板时全部跳过；暂停门店跳过。
     */
    Map<String, Object> autoGenerateWeekly();

    Task detail(Integer id); // 获取任务详情

    Map<String, Object> getResult(Integer id); // 获取任务盘点结果（分区视图+物料汇总）

    void delete(Integer id); // 删除任务（仅未开始状态可删）

    void update(Integer id, TaskUpdateRequest req); // 更新任务字段（仅未开始/进行中可改）

    void updateMaterials(Integer taskId, List<MaterialUpdateReq> materials); // 批量更新物料数量（已提交任务）

    void deleteMaterial(Integer taskId, Integer materialId); // 删除物料（已提交任务）

    void setMaterialTotal(Integer taskId, String materialId, BigDecimal totalQty); // 物料总量汇总编辑
}
