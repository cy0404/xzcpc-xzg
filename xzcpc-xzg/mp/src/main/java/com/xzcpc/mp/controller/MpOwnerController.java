package com.xzcpc.mp.controller;

import com.xzcpc.common.response.R;
import com.xzcpc.mp.context.LoginUser;
import com.xzcpc.mp.context.UserContextHolder;
import com.xzcpc.mp.dto.OwnerDashboardResp;
import com.xzcpc.mp.service.MpOwnerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mp/owner")
@RequiredArgsConstructor
public class MpOwnerController {

    private final MpOwnerService ownerService;

    @GetMapping("/dashboard")
    public R<OwnerDashboardResp> dashboard() {
        LoginUser user = UserContextHolder.get();
        return R.ok(ownerService.dashboard(user.getOpenid()));
    }
}
