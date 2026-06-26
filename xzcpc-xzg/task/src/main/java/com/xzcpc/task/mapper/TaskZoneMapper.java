package com.xzcpc.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xzcpc.task.entity.TaskZone;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TaskZoneMapper extends BaseMapper<TaskZone> {
    int insertBatch(List<TaskZone> list);
}
