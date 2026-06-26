package com.xzcpc.people.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.common.annotation.OpLog;
import com.xzcpc.common.response.R;
import com.xzcpc.people.dto.OwnerRegistrationSaveReq;
import com.xzcpc.people.entity.OwnerRegistration;
import com.xzcpc.people.service.OwnerRegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/owner-registrations")
@RequiredArgsConstructor
public class OwnerRegistrationController {

    private final OwnerRegistrationService registrationService;

    @OpLog(module = "人员", operation = "查询门店绑定记录")
    @GetMapping
    public R<Page<OwnerRegistration>> list(@RequestParam(defaultValue = "") String storeId,
                                           @RequestParam(defaultValue = "") String status,
                                           @RequestParam(defaultValue = "") String name,
                                           @RequestParam(defaultValue = "") String phone,
                                           @RequestParam(defaultValue = "1") int pageNum,
                                           @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(registrationService.page(storeId, status, name, phone, pageNum, pageSize));
    }

    @OpLog(module = "人员", operation = "查看门店绑定记录")
    @GetMapping("/{id}")
    public R<OwnerRegistration> detail(@PathVariable Long id) {
        return R.ok(registrationService.detail(id));
    }

    @OpLog(module = "人员", operation = "手动绑定门店")
    @PostMapping("/{id}/bind")
    public R<OwnerRegistration> bind(@PathVariable Long id, @Valid @RequestBody OwnerRegistrationSaveReq req) {
        return R.ok(registrationService.bind(id, req));
    }

    @OpLog(module = "人员", operation = "更新门店绑定记录")
    @PutMapping("/{id}")
    public R<OwnerRegistration> update(@PathVariable Long id, @Valid @RequestBody OwnerRegistrationSaveReq req) {
        return R.ok(registrationService.update(id, req));
    }
}
