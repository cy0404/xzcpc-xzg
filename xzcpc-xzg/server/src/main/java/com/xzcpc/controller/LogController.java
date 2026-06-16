package com.xzcpc.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.common.entity.LoginLog;
import com.xzcpc.common.entity.OperationLog;
import com.xzcpc.common.response.R;
import com.xzcpc.common.service.LoginLogService;
import com.xzcpc.common.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 日志查询接口（总部端）
 */
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogController {

    private final OperationLogService operationLogService;
    private final LoginLogService loginLogService;

    /** 分页查询操作日志 */
    @GetMapping("/operation")
    public R<IPage<OperationLog>> operationPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String operation) {
        return R.ok(operationLogService.page(new Page<>(page, size), username, module, operation));
    }

    /** 分页查询登录日志 */
    @GetMapping("/login")
    public R<IPage<LoginLog>> loginPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String loginType) {
        return R.ok(loginLogService.page(new Page<>(page, size), username, loginType));
    }
}
