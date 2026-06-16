package com.xzcpc.mp.controller;

import com.xzcpc.common.response.R;
import com.xzcpc.mp.context.LoginUser;
import com.xzcpc.mp.context.UserContextHolder;
import com.xzcpc.mp.dto.BindStoreReq;
import com.xzcpc.mp.dto.LoginResp;
import com.xzcpc.mp.dto.WxLoginReq;
import com.xzcpc.mp.service.MpAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mp/auth")
@RequiredArgsConstructor
public class MpAuthController {

    private final MpAuthService authService;

    @PostMapping("/wx/login")
    public R<LoginResp> wxLogin(@Valid @RequestBody WxLoginReq req) {
        return R.ok(authService.wxLogin(req.getCode(), req.getWxNickname()));
    }

    @PostMapping("/bind-store")
    public R<LoginResp> bindStore(@Valid @RequestBody BindStoreReq req) {
        LoginUser user = UserContextHolder.get();
        return R.ok(authService.bindStore(user.getSessionId(), req));
    }

    @PostMapping("/switch-store")
    public R<LoginResp> switchStore(@Valid @RequestBody BindStoreReq req) {
        LoginUser user = UserContextHolder.get();
        return R.ok(authService.switchStore(user.getSessionId(), req));
    }

    @PostMapping("/logout")
    public R<Void> logout() {
        LoginUser user = UserContextHolder.get();
        authService.logout(user.getSessionId());
        return R.ok();
    }

    @GetMapping("/stores")
    public R<List<Map<String, Object>>> myStores() {
        LoginUser user = UserContextHolder.get();
        return R.ok(authService.myStores(user.getSessionId()));
    }

    @GetMapping("/me")
    public R<LoginResp> me() {
        LoginUser user = UserContextHolder.get();
        return R.ok(authService.me(user.getSessionId()));
    }
}
