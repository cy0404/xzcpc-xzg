package com.xzcpc.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xzcpc.task.entity.TaskZone;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TaskZoneMapper extends BaseMapper<TaskZone> { // 任务分区快照表 Mapper
}
