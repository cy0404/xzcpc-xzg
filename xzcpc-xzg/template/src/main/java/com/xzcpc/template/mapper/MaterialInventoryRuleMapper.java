package com.xzcpc.template.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xzcpc.template.entity.MaterialInventoryRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MaterialInventoryRuleMapper extends BaseMapper<MaterialInventoryRule> {

    /**
     * 同步专用更新（存量物料的规则）：补采购单价/采购单位 + 订货单价/订货单位 + 库存单位。
     * 每个字段非 null 才写入（null 不覆盖已有值，保护人工维护/接口缺失场景）。
     * 显式 AND del_flag = 0，防止把已停用物料的规则意外刷新。
     */
    @Update("<script>UPDATE material_inventory_rule SET "
            + "<if test='purchasePrice != null'>purchase_price = #{purchasePrice},</if>"
            + "<if test='purchaseUnit != null'>purchase_unit = #{purchaseUnit},</if>"
            + "<if test='orderPrice != null'>order_price = #{orderPrice},</if>"
            + "<if test='orderUnit != null'>order_unit = #{orderUnit},</if>"
            + "<if test='stockUnit != null'>stock_unit = #{stockUnit},</if>"
            + "updated_at = NOW() WHERE material_id = #{materialId} AND del_flag = 0</script>")
    int updatePurchaseFields(MaterialInventoryRule rule);
}
