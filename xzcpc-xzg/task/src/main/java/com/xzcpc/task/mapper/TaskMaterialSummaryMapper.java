package com.xzcpc.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xzcpc.task.entity.TaskMaterialSummary;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import org.apache.ibatis.annotations.Delete;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface TaskMaterialSummaryMapper extends BaseMapper<TaskMaterialSummary> {

    /** 真正批量插入——单条 INSERT ... VALUES (...),(...),(...) */
    @Insert("<script>" +
        "INSERT INTO task_material_summary (task_id, material_id, material_name, spec, image_url, base_unit, total_qty, original_qty, adjusted_qty, zone_count, unit_breakdown) VALUES " +
        "<foreach collection='list' item='item' separator=','>" +
        "(#{item.taskId}, #{item.materialId}, #{item.materialName}, #{item.spec}, #{item.imageUrl}, #{item.baseUnit}, #{item.totalQty}, #{item.originalQty}, #{item.adjustedQty}, #{item.zoneCount}, #{item.unitBreakdown})" +
        "</foreach>" +
        "</script>")
    int insertBatch(@Param("list") List<TaskMaterialSummary> list);

    /** 硬删除（绕过 @TableLogic，物理删除） */
    @Delete("DELETE FROM task_material_summary WHERE task_id = #{taskId}")
    int physicalDeleteByTaskId(@Param("taskId") Integer taskId);

    /** 更新调整后的数量（差异处理修改） */
    @Update("UPDATE task_material_summary SET adjusted_qty = #{adjustedQty} WHERE task_id = #{taskId} AND material_id = #{materialId}")
    int updateAdjustedQty(@Param("taskId") Integer taskId, @Param("materialId") String materialId, @Param("adjustedQty") BigDecimal adjustedQty);
}
