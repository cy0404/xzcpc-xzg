package com.xzcpc.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xzcpc.task.entity.TaskMaterialSummary;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TaskMaterialSummaryMapper extends BaseMapper<TaskMaterialSummary> {

    /** 真正批量插入——单条 INSERT ... VALUES (...),(...),(...) */
    @Insert("<script>" +
        "INSERT INTO task_material_summary (task_id, material_id, material_name, spec, base_unit, total_qty, zone_count, unit_breakdown) VALUES " +
        "<foreach collection='list' item='item' separator=','>" +
        "(#{item.taskId}, #{item.materialId}, #{item.materialName}, #{item.spec}, #{item.baseUnit}, #{item.totalQty}, #{item.zoneCount}, #{item.unitBreakdown})" +
        "</foreach>" +
        "</script>")
    int insertBatch(@Param("list") List<TaskMaterialSummary> list);
}
