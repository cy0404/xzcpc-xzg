package com.xzcpc.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xzcpc.task.entity.Task;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TaskMapper extends BaseMapper<Task> { // 任务表 Mapper
}
