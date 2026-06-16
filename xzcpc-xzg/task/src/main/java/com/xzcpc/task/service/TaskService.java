package com.xzcpc.task.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.task.dto.TaskCreateRequest;
import com.xzcpc.task.entity.Task;

import java.util.Map;

public interface TaskService { // 月盘任务服务接口

    Page<Task> page(String storeId, String status, String keyword, String templateName,
                    String taskMonth, int pageNum, int pageSize);

    void create(Task task); // 创建月盘任务（单门店）

    int batchCreate(TaskCreateRequest request); // 批量创建月盘任务（多门店）

    Task detail(Integer id); // 获取任务详情

    Map<String, Object> getResult(Integer id); // 获取任务盘点结果（分区视图+物料汇总）

    void delete(Integer id); // 删除任务（仅未开始状态可删）
}
