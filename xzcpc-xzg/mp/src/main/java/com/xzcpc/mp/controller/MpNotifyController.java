package com.xzcpc.mp.controller;

import com.xzcpc.common.response.R;
import com.xzcpc.mp.context.LoginUser;
import com.xzcpc.mp.context.UserContextHolder;
import com.xzcpc.mp.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订阅消息授权额度上报：前端在 wx.requestSubscribeMessage 返回 accept 后调用 +1。
 * 无参：openid 取登录态。
 */
@RestController
@RequestMapping("/api/mp/notify")
@RequiredArgsConstructor
public class MpNotifyController {

    private final NotificationService notificationService;

    @PostMapping("/quota/plus")
    public R<Void> plusQuota() {
        LoginUser user = UserContextHolder.get();
        if (user != null && StringUtils.hasText(user.getOpenid())) {
            notificationService.plusQuota(user.getOpenid());
        }
        return R.ok();
    }
}
