package com.xzcpc.people.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.people.dto.PeopleDashboardResp;
import com.xzcpc.people.entity.Employee;

import java.util.Map;

public interface EmployeeService {

    Page<Employee> page(String storeId, String role, String status, String name, int pageNum, int pageSize);

    Map<String, Object> detail(String employeeId);

    PeopleDashboardResp dashboard(String range);
}
