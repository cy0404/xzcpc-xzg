package com.xzcpc.template.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xzcpc.template.entity.TemplateZoneMaterial;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Mapper
public interface TemplateZoneMaterialMapper extends BaseMapper<TemplateZoneMaterial> {

    @Select("<script>"
            + "SELECT tz.template_id, COUNT(*) AS cnt "
            + "FROM template_zone_material tzm "
            + "JOIN template_zone tz ON tzm.zone_id = tz.id "
            + "WHERE tz.template_id IN "
            + "<foreach collection='templateIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "GROUP BY tz.template_id"
            + "</script>")
    List<Map<String, Object>> selectZoneMaterialCounts(Set<Integer> templateIds);
}
