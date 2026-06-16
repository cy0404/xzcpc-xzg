package com.xzcpc.people.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xzcpc.people.entity.Employee;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EmployeeMapper extends BaseMapper<Employee> {
}
