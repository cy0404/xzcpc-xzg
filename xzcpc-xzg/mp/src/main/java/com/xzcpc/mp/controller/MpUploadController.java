package com.xzcpc.mp.controller;

import com.xzcpc.common.annotation.OpLog;
import com.xzcpc.common.response.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/mp")
@RequiredArgsConstructor
public class MpUploadController {

    @Value("${app.upload.path:./upload}")
    private String uploadPath;

    @Value("${app.public-url:}")
    private String publicUrl;

    /**
     * 上传支出凭证图片。
     * 文件保存到 {uploadPath}/voucher/ 目录，返回完整可访问 URL。
     */
    @OpLog(module = "小程序-上传", operation = "上传凭证图片")
    @PostMapping("/upload/voucher")
    public R<Map<String, String>> uploadVoucher(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return R.fail(400, "请选择文件");
        }

        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.lastIndexOf('.') > 0) {
            ext = originalName.substring(originalName.lastIndexOf('.')).toLowerCase();
        }
        // 只允许图片格式
        if (!ext.matches("\\.(jpg|jpeg|png|gif|bmp|webp)")) {
            return R.fail(400, "仅支持 jpg/png/gif/bmp/webp 格式图片");
        }

        String filename = UUID.randomUUID().toString().replace("-", "") + ext;
        Path voucherDir = Paths.get(uploadPath).toAbsolutePath().normalize().resolve("voucher");
        try {
            Files.createDirectories(voucherDir);
        } catch (IOException e) {
            log.error("创建上传目录失败: {}", voucherDir, e);
            return R.fail(500, "上传目录创建失败");
        }

        File dest = voucherDir.resolve(filename).toFile();
        try {
            file.transferTo(dest);
        } catch (IOException e) {
            log.error("保存文件失败: {}", dest, e);
            return R.fail(500, "文件保存失败");
        }

        // 使用配置的公开 URL 前缀，而非 request.getServerName()（Nginx 代理下会拿到内网地址）
        String baseUrl = publicUrl.isBlank()
                ? "http://localhost:" + 8081 + "/storeInventory"
                : publicUrl;
        String fullUrl = baseUrl + "/upload/voucher/" + filename;

        Map<String, String> result = new HashMap<>();
        result.put("url", fullUrl);
        result.put("filename", filename);
        return R.ok(result);
    }
}
