package com.xzcpc.template.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xzcpc.template.entity.SemiProduct;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 半成品成本卡主表 Mapper。
 * selectAnyBySemiId 绕过逻辑删除（同步 upsert 需命中 del_flag=0/1）。
 */
public interface SemiProductMapper extends BaseMapper<SemiProduct> {

    @Select("SELECT * FROM semi_product WHERE semi_id = #{semiId} LIMIT 1")
    SemiProduct selectAnyBySemiId(@Param("semiId") String semiId);

    @Update("UPDATE semi_product SET del_flag = 0 WHERE semi_id = #{semiId}")
    int restoreBySemiId(@Param("semiId") String semiId);
}
