package com.xzcpc.template.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xzcpc.template.entity.Material;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MaterialMapper extends BaseMapper<Material> {

    /**
     * 按业务 materialId 查任意状态记录（含 del_flag=1），供同步"复活"使用。
     * 不走 MyBatis-Plus 逻辑删除过滤。
     */
    @Select("SELECT * FROM material WHERE material_id = #{materialId} LIMIT 1")
    Material selectAnyByMaterialId(String materialId);

    /**
     * 同步专用更新：覆盖基本信息并显式写 del_flag（复活 DISABLED→ENABLED）。
     * 自定义 SQL 不受 MyBatis-Plus 逻辑删除对 updateById 的 WHERE 拼接影响。
     */
    @Update("UPDATE material SET qm_code = #{qmCode}, parent_category = #{parentCategory}, "
            + "category = #{category}, material_name = #{materialName}, spec = #{spec}, "
            + "del_flag = #{delFlag}, updated_at = NOW() "
            + "WHERE material_id = #{materialId}")
    int upsertSyncFields(Material material);

    /**
     * 同步专用更新（存量物料）：仅刷新一级分类（成本科目映射用），其他字段不动。
     * 显式 AND del_flag = 0，防止把已停用物料意外刷新。
     */
    @Update("UPDATE material SET parent_category = #{parentCategory}, updated_at = NOW() "
            + "WHERE material_id = #{materialId} AND del_flag = 0")
    int updateParentCategoryFields(Material material);

    /**
     * 同步专用更新（存量物料）：仅刷新二级分类，其他字段不动。
     * 显式 AND del_flag = 0，防止把已停用物料意外刷新。
     */
    @Update("UPDATE material SET category = #{category}, updated_at = NOW() "
            + "WHERE material_id = #{materialId} AND del_flag = 0")
    int updateCategoryFields(Material material);

    /**
     * 同步专用更新（存量物料）：仅刷新规格（空时补齐接口原值用），其他字段不动。
     * 显式 AND del_flag = 0，防止把已停用物料意外刷新。
     */
    @Update("UPDATE material SET spec = #{spec}, updated_at = NOW() "
            + "WHERE material_id = #{materialId} AND del_flag = 0")
    int updateSpecFields(Material material);

    /**
     * 按 qm_code 查存量启用物料（del_flag=0），供同步按编码匹配存量补录采购字段。
     * 存量物料 material_id 是旧 id 体系（如 WP0917），但 qm_code 与接口 code 同编码，可对上。
     */
    @Select("SELECT * FROM material WHERE qm_code = #{qmCode} AND del_flag = 0 LIMIT 1")
    Material selectAnyByQmCode(String qmCode);

    /**
     * 按 qm_code 查任意状态存量物料（含 del_flag=1/2），供"全部同步"模式下
     * 存量/淘汰物料统一按接口覆盖归位。绕过 MyBatis-Plus 逻辑删除过滤。
     */
    @Select("SELECT * FROM material WHERE qm_code = #{qmCode} LIMIT 1")
    Material selectAnyByQmCodeAny(String qmCode);

    /**
     * 全量同步收尾：残留 del_flag=2（接口已无此 code 的淘汰物料）统一归 1。
     * 接口仍存在的 del_flag=2 物料已在同步循环中复活为 0，到这里的只剩接口没有的。
     */
    @Update("UPDATE material SET del_flag = 1, updated_at = NOW() WHERE del_flag = 2")
    int markEliminatedDeleted();

    /**
     * 仅按接口状态归位 del_flag（半成品同步关闭时用）：不改任何其他字段，
     * 避免误删的接口 ENABLED 半成品无法恢复、接口 DISABLED 的保持停用。
     */
    @Update("UPDATE material SET del_flag = #{delFlag}, updated_at = NOW() WHERE material_id = #{materialId}")
    int updateDelFlagOnly(@Param("materialId") String materialId, @Param("delFlag") int delFlag);
}
