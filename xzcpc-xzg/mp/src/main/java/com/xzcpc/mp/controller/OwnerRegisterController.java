package com.xzcpc.mp.controller;

import com.xzcpc.common.response.R;
import com.xzcpc.mp.service.OwnerRegisterService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mp/auth/owner")
@RequiredArgsConstructor
public class OwnerRegisterController {

    private final OwnerRegisterService ownerRegisterService;

    /** 获取门店列表（公开，供老板扫码页选择） */
    @GetMapping("/stores")
    public R<List<Map<String, Object>>> listStores() {
        return R.ok(ownerRegisterService.listStores());
    }

    /** 提交管理员登记（公开） */
    @PostMapping("/register")
    public R<List<Map<String, Object>>> register(@RequestBody Map<String, Object> body) {
        String code = (String) body.get("code");
        String name = (String) body.get("name");
        String phone = (String) body.get("phone");
        String role = (String) body.get("role");
        String bindCode = (String) body.get("bindCode");
        @SuppressWarnings("unchecked")
        List<String> storeIds = (List<String>) body.get("storeIds");
        return R.ok(ownerRegisterService.submit(code, name, phone, role, bindCode, storeIds));
    }
}
