package com.xzcpc.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xzcpc.task.entity.InventoryDifference;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface InventoryDifferenceMapper extends BaseMapper<InventoryDifference> {

    @Delete("DELETE FROM inventory_difference WHERE task_id = #{taskId}")
    int physicalDeleteByTaskId(@Param("taskId") Integer taskId);

    @Insert("<script>" +
        "INSERT INTO inventory_difference (task_id,material_id,material_name,spec,unit,last_month_qty,transfer_net_qty,return_qty,loss_qty,self_purchase_qty,purchase_qty,order_qty,consumption_qty,theoretical_qty,actual_qty,diff_qty,diff_rate,is_large,original_is_large,status,created_at,updated_at) VALUES " +
        "<foreach collection='list' item='d' separator=','>" +
        "(#{d.taskId},#{d.materialId},#{d.materialName},#{d.spec},#{d.unit},#{d.lastMonthQty},#{d.transferNetQty},#{d.returnQty},#{d.lossQty},#{d.selfPurchaseQty},#{d.purchaseQty},#{d.orderQty},#{d.consumptionQty},#{d.theoreticalQty},#{d.actualQty},#{d.diffQty},#{d.diffRate},#{d.isLarge},#{d.originalIsLarge},#{d.status},#{d.createdAt},#{d.updatedAt})" +
        "</foreach>" +
        "</script>")
    int insertBatch(@Param("list") List<InventoryDifference> list);
}
