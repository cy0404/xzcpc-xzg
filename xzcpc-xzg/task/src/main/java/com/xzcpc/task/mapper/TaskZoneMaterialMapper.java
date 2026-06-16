package com.xzcpc.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xzcpc.task.entity.TaskZoneMaterial;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TaskZoneMaterialMapper extends BaseMapper<TaskZoneMaterial> { // 任务分区物料快照表 Mapper

    /**
     * 查询唯一键对应的记录（包含已逻辑删除的），绕过 @TableLogic 自动过滤。
     * 用于 addMaterial 时判断是否需要复活而非新增。
     */
    @Select("SELECT * FROM task_zone_material WHERE task_id = #{taskId} AND task_zone_id = #{taskZoneId} AND material_id = #{materialId}")
    TaskZoneMaterial selectAnyByKey(@Param("taskId") Integer taskId, @Param("taskZoneId") Integer taskZoneId, @Param("materialId") String materialId);

    /**
     * 复活已逻辑删除的记录：将 del_flag 置为 0 并刷新物料快照字段。
     * 绕过 @TableLogic，使 WHERE id = #{id} 不受 del_flag 过滤影响。
     */
    @Update("UPDATE task_zone_material SET del_flag = 0, material_name = #{materialName}, spec = #{spec}, unit = #{unit}, inventory_unit = #{inventoryUnit}, sort_no = #{sortNo}, input_qty = NULL, input_status = 'not_entered', remark = NULL, version = version + 1 WHERE id = #{id}")
    int reactivateByKey(@Param("id") Integer id,
                        @Param("materialName") String materialName,
                        @Param("spec") String spec,
                        @Param("unit") String unit,
                        @Param("inventoryUnit") String inventoryUnit,
                        @Param("sortNo") Integer sortNo);
}
