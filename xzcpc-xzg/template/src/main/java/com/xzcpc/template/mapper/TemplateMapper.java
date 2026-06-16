package com.xzcpc.template.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xzcpc.template.entity.Template;
import org.apache.ibatis.annotations.Mapper;

// 模板 Mapper，MyBatis-Plus 基础 CRUD
@Mapper
public interface TemplateMapper extends BaseMapper<Template> {
}
