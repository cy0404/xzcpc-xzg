package com.xzcpc.mp.controller;

import com.xzcpc.common.annotation.OpLog;
import com.xzcpc.common.response.R;
import com.xzcpc.mp.context.LoginUser;
import com.xzcpc.mp.context.UserContextHolder;
import com.xzcpc.mp.dto.WorkHoursSaveReq;
import com.xzcpc.mp.entity.StoreWorkHours;
import com.xzcpc.mp.service.MpWorkHoursService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/mp/work-hours")
@RequiredArgsConstructor
public class MpWorkHoursController {

    private final MpWorkHoursService workHoursService;

    @OpLog(module = "小程序-工时", operation = "查询列表")
    @GetMapping
    public R<List<StoreWorkHours>> list() {
        LoginUser user = UserContextHolder.get();
        return R.ok(workHoursService.list(user.getStoreId()));
    }

    @OpLog(module = "小程序-工时", operation = "新增")
    @PostMapping
    public R<StoreWorkHours> create(@Valid @RequestBody WorkHoursSaveReq req) {
        LoginUser user = UserContextHolder.get();
        return R.ok(workHoursService.create(user.getStoreId(), user.getStoreName(), user.getOpenid(), req));
    }

    @OpLog(module = "小程序-工时", operation = "修改")
    @PutMapping("/{recordId}")
    public R<StoreWorkHours> update(@PathVariable String recordId, @Valid @RequestBody WorkHoursSaveReq req) {
        LoginUser user = UserContextHolder.get();
        return R.ok(workHoursService.update(user.getStoreId(), user.getOpenid(), recordId, req));
    }

    @OpLog(module = "小程序-工时", operation = "删除")
    @DeleteMapping("/{recordId}")
    public R<Void> delete(@PathVariable String recordId) {
        LoginUser user = UserContextHolder.get();
        workHoursService.delete(user.getStoreId(), user.getOpenid(), recordId);
        return R.ok();
    }
}
