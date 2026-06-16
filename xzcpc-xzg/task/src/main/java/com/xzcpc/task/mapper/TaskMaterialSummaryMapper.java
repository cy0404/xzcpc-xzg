package com.xzcpc.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xzcpc.task.entity.TaskMaterialSummary;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TaskMaterialSummaryMapper extends BaseMapper<TaskMaterialSummary> { // 物料汇总表 Mapper
}
