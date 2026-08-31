package com.xzcpc.template.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xzcpc.template.entity.SemiFormulaItem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/**
 * 半成品配方行 Mapper。
 * 同步按版本物理删除后重插（版本内容以接口为准，全量替换）。
 */
public interface SemiFormulaItemMapper extends BaseMapper<SemiFormulaItem> {

    @Delete("DELETE FROM semi_formula_item WHERE version_id = #{versionId}")
    int deleteByVersionId(@Param("versionId") Long versionId);
}
