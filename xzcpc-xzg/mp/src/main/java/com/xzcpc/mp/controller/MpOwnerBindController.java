package com.xzcpc.mp.controller;

import com.xzcpc.common.response.R;
import com.xzcpc.mp.context.UserContextHolder;
import com.xzcpc.mp.dto.OwnerBindReq;
import com.xzcpc.mp.dto.OwnerBindStatusResp;
import com.xzcpc.mp.dto.OwnerMyStatusResp;
import com.xzcpc.mp.service.MpOwnerBindService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.io.OutputStream;
import java.util.Base64;

@RestController
@RequestMapping("/api/mp/auth/owner")
@RequiredArgsConstructor
public class MpOwnerBindController {

    private final MpOwnerBindService bindService;

    @PostMapping("/bind")
    public R<OwnerBindStatusResp> bind(@Valid @RequestBody OwnerBindReq req) {
        return R.ok(bindService.submitBind(req));
    }

    @GetMapping("/status")
    public R<OwnerBindStatusResp> status(@RequestParam("bindCode") String bindCode) {
        return R.ok(bindService.getBindStatus(bindCode));
    }

    /**
     * 生成绑定用小程序码（base64 png）。
     * GET /api/mp/auth/owner/qrcode?bindCode=test123
     */
    @GetMapping("/qrcode")
    public R<String> qrcode(@RequestParam("bindCode") String bindCode) {
        byte[] bytes = bindService.generateQrCode(bindCode);
        String base64 = "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
        return R.ok(base64);
    }

    /**
     * 直接返回小程序码图片（PNG），浏览器打开即可看到二维码。
     * GET /api/mp/auth/owner/qrcode-img?bindCode=boss001
     */
    /**
     * 登录时查询当前微信的绑定状态。
     * GET /api/mp/auth/owner/my-status?wxCode=xxx
     */
    @GetMapping("/my-status")
    public R<OwnerMyStatusResp> myStatus(@RequestParam("wxCode") String wxCode) {
        return R.ok(bindService.getMyStatus(wxCode));
    }

    /**
     * 已登录用户查询最新绑定申请（首页 banner）。
     * GET /api/mp/auth/owner/latest-application
     */
    @GetMapping("/latest-application")
    public R<OwnerBindStatusResp> latestApplication() {
        String openid = UserContextHolder.get().getOpenid();
        return R.ok(bindService.getLatestApplicationByOpenid(openid));
    }

    @GetMapping("/qrcode-img")
    public void qrcodeImg(@RequestParam("bindCode") String bindCode, HttpServletResponse response) {
        byte[] bytes = bindService.generateQrCode(bindCode);
        response.setContentType(MediaType.IMAGE_PNG_VALUE);
        response.setHeader("Content-Disposition", "inline; filename=qrcode.png");
        try (OutputStream os = response.getOutputStream()) {
            os.write(bytes);
            os.flush();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
