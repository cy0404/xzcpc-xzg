package com.xzcpc.people.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.common.annotation.OpLog;
import com.xzcpc.common.response.R;
import com.xzcpc.people.dto.PeopleDashboardResp;
import com.xzcpc.people.entity.Employee;
import com.xzcpc.people.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    @OpLog(module = "人员", operation = "查询列表")
    @GetMapping
    public R<Page<Employee>> list(@RequestParam(defaultValue = "") String storeId,
                                  @RequestParam(defaultValue = "") String role,
                                  @RequestParam(defaultValue = "") String status,
                                  @RequestParam(defaultValue = "") String name,
                                  @RequestParam(defaultValue = "1") int pageNum,
                                  @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(employeeService.page(storeId, role, status, name, pageNum, pageSize));
    }

    @OpLog(module = "人员", operation = "查看统计")
    @GetMapping("/dashboard")
    public R<PeopleDashboardResp> dashboard(@RequestParam(defaultValue = "month") String range) {
        return R.ok(employeeService.dashboard(range));
    }

    @OpLog(module = "人员", operation = "查询详情")
    @GetMapping("/{employeeId}")
    public R<Map<String, Object>> detail(@PathVariable String employeeId) {
        return R.ok(employeeService.detail(employeeId));
    }
}
