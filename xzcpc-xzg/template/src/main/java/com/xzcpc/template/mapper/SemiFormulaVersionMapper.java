package com.xzcpc.template.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xzcpc.template.entity.SemiFormulaVersion;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 半成品配方版本 Mapper。
 * 同步语义：先按 semi_id 全量逻辑删，再对接口仍存在的 version_id 恢复 + upsert——
 * 防止接口配方被删/清空后旧 ACTIVE 版本残留被爆炸误用。
 */
public interface SemiFormulaVersionMapper extends BaseMapper<SemiFormulaVersion> {

    @Update("UPDATE semi_formula_version SET del_flag = 1 WHERE semi_id = #{semiId} AND del_flag = 0")
    int logicDeleteBySemiId(@Param("semiId") String semiId);

    @Update("UPDATE semi_formula_version SET del_flag = 0 WHERE version_id = #{versionId} AND del_flag = 1")
    int restoreByVersionId(@Param("versionId") Long versionId);
}
