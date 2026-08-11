package com.xzcpc.mp.controller;

import com.xzcpc.common.annotation.OpLog;
import com.xzcpc.common.response.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/mp")
public class MpUploadController {

    private static final Logger log = LoggerFactory.getLogger(MpUploadController.class);

    @Value("${app.upload.path:./upload}")
    private String uploadPath;

    @Value("${app.public-url:}")
    private String publicUrl;

    private static final long MAX_FILE_SIZE = 200L * 1024 * 1024; // 200MB

    @OpLog(module = "小程序-上传", operation = "上传凭证文件")
    @PostMapping("/upload/voucher")
    public R<Map<String, String>> uploadVoucher(@RequestParam("file") MultipartFile file,
                                                 @RequestParam(value = "storeName", required = false) String storeName) {
        if (file.isEmpty()) {
            return R.fail(400, "请选择文件");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            return R.fail(400, "文件大小不能超过200MB");
        }

        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.lastIndexOf('.') > 0) {
            ext = originalName.substring(originalName.lastIndexOf('.')).toLowerCase();
        }
        if (!ext.matches("\\.(jpg|jpeg|png|gif|bmp|webp|mp4|mov|avi|mkv|webm)")) {
            return R.fail(400, "仅支持 jpg/png/gif/bmp/webp/mp4/mov/avi/mkv/webm 格式");
        }

        String filename = UUID.randomUUID().toString().replace("-", "") + ext;
        String month = YearMonth.now().toString();
        String safeStore = storeName != null && !storeName.isEmpty() ? storeName.replaceAll("[\\\\/:*?\"<>|]", "_") : "unknown";
        Path voucherDir = Paths.get(uploadPath).toAbsolutePath().normalize().resolve("voucher").resolve(safeStore).resolve(month);
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

        boolean isVideo = ext.matches("\\.(mp4|mov|avi|mkv|webm)");

        String baseUrl = publicUrl.isBlank()
                ? "http://localhost:" + 8081 + "/storeInventory"
                : publicUrl;
        String encodedStore = safeStore;
        try { encodedStore = URLEncoder.encode(safeStore, "UTF-8").replace("+", "%20"); } catch (Exception ignored) {}
        String fullUrl = baseUrl + "/upload/voucher/" + encodedStore + "/" + month + "/" + filename;

        Map<String, String> result = new HashMap<>();
        result.put("url", fullUrl);
        result.put("filename", filename);
        if (isVideo) {
            String thumbPath = generateThumbnail(dest.getAbsolutePath());
            if (thumbPath != null) result.put("thumb", thumbUrl(fullUrl));
        }
        return R.ok(result);
    }

    @OpLog(module = "小程序-上传", operation = "上传验收标准图片")
    @PostMapping("/upload/loss-standard")
    public R<Map<String, String>> uploadLossStandardImage(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return R.fail(400, "请选择文件");
        }
        if (file.getSize() > 20L * 1024 * 1024) {
            return R.fail(400, "文件大小不能超过20MB");
        }

        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.lastIndexOf('.') > 0) {
            ext = originalName.substring(originalName.lastIndexOf('.')).toLowerCase();
        }
        if (!ext.matches("\\.(jpg|jpeg|png|gif|bmp|webp)")) {
            return R.fail(400, "仅支持 jpg/png/gif/bmp/webp 格式");
        }

        String filename = UUID.randomUUID().toString().replace("-", "") + ext;
        String month = YearMonth.now().toString();
        Path dir = Paths.get(uploadPath).toAbsolutePath().normalize().resolve("loss-standard").resolve(month);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.error("创建目录失败: {}", dir, e);
            return R.fail(500, "目录创建失败");
        }

        File dest = dir.resolve(filename).toFile();
        try {
            file.transferTo(dest);
        } catch (IOException e) {
            log.error("保存文件失败: {}", dest, e);
            return R.fail(500, "文件保存失败");
        }

        boolean isVideo = ext.matches("\\.(mp4|mov|avi|mkv|webm)");

        String baseUrl = publicUrl.isBlank()
                ? "http://localhost:" + 8081 + "/storeInventory"
                : publicUrl;
        String fullUrl = baseUrl + "/upload/loss-standard/" + month + "/" + filename;

        Map<String, String> result = new HashMap<>();
        result.put("url", fullUrl);
        result.put("filename", filename);
        return R.ok(result);
    }

    private void transcodeToH264(Path f) { transcodeToH264(f.toFile()); }

    private void transcodeToH264(File f) {
        new Thread(() -> {
            try {
                String src = f.getAbsolutePath();
                ProcessBuilder probePb = new ProcessBuilder("ffprobe", "-v", "error",
                    "-select_streams", "v:0", "-show_entries", "stream=codec_name",
                    "-of", "default=noprint_wrappers=1:nokey=1", src);
                Process probe = probePb.start();
                String codec = new String(probe.getInputStream().readAllBytes()).trim();
                probe.waitFor();
                if (!"hevc".equalsIgnoreCase(codec) && !"h265".equalsIgnoreCase(codec)) return;
                String tmp = src + ".transcoded.mp4";
                ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-y", "-i", src,
                    "-c:v", "libx264", "-preset", "ultrafast", "-crf", "24",
                    "-c:a", "aac", "-movflags", "+faststart", tmp);
                pb.redirectErrorStream(true);
                Process p = pb.start();
                p.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
                if (p.waitFor() == 0 && new File(tmp).length() > 0) {
                    Files.delete(f.toPath());
                    Files.move(Paths.get(tmp), f.toPath());
                    log.info("转码完成: {}", f.getAbsolutePath());
                }
            } catch (Exception e) {
                log.warn("转码失败（保留原文件）: {}", f.getAbsolutePath());
            }
        }).start();
    }

    private String generateThumbnail(String videoPath) {
        String thumbPath = videoPath.substring(0, videoPath.lastIndexOf('.')) + "_thumb.jpg";
        try {
            ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-y", "-i", videoPath,
                    "-ss", "1", "-vframes", "1", "-q:v", "5", "-vf", "scale=320:-2", thumbPath);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream())).lines().forEach(line -> {});
            process.waitFor();
            if (new File(thumbPath).exists()) return thumbPath;
        } catch (Exception e) {
            log.warn("生成缩略图失败: {}", videoPath, e);
        }
        return null;
    }

    private String thumbUrl(String videoFullUrl) {
        int dot = videoFullUrl.lastIndexOf('.');
        return videoFullUrl.substring(0, dot) + "_thumb.jpg";
    }

    @OpLog(module = "小程序-上传", operation = "初始化分片上传")
    @PostMapping("/upload/chunk/init")
    public R<Map<String, String>> initChunkUpload(@RequestBody Map<String, Object> body) {
        String uploadId = UUID.randomUUID().toString().replace("-", "");
        Path chunkDir = Paths.get(uploadPath).toAbsolutePath().normalize().resolve("chunks").resolve(uploadId);
        try {
            Files.createDirectories(chunkDir);
        } catch (IOException e) {
            log.error("创建分片目录失败: {}", chunkDir, e);
            return R.fail(500, "创建分片目录失败");
        }
        Map<String, String> result = new HashMap<>();
        result.put("uploadId", uploadId);
        return R.ok(result);
    }

    @OpLog(module = "小程序-上传", operation = "上传分片")
    @PostMapping("/upload/chunk/{uploadId}/{chunkIndex}")
    public R<Void> uploadChunk(@PathVariable String uploadId, @PathVariable int chunkIndex,
                               @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) return R.fail(400, "分片为空");
        Path chunkDir = Paths.get(uploadPath).toAbsolutePath().normalize().resolve("chunks").resolve(uploadId);
        if (!Files.exists(chunkDir)) return R.fail(404, "上传会话不存在");
        Path chunkFile = chunkDir.resolve(String.valueOf(chunkIndex));
        try {
            file.transferTo(chunkFile.toFile());
        } catch (IOException e) {
            log.error("保存分片失败: {}", chunkFile, e);
            return R.fail(500, "分片保存失败");
        }
        return R.ok();
    }

    @OpLog(module = "小程序-上传", operation = "合并分片")
    @PostMapping("/upload/chunk/{uploadId}/complete")
    public R<Map<String, String>> completeChunkUpload(@PathVariable String uploadId,
                                                       @RequestBody Map<String, Object> body) {
        String fileName = (String) body.getOrDefault("fileName", "video.mp4");
        int totalChunks = body.containsKey("totalChunks") ? ((Number) body.get("totalChunks")).intValue() : 0;
        Path chunkDir = Paths.get(uploadPath).toAbsolutePath().normalize().resolve("chunks").resolve(uploadId);
        if (!Files.exists(chunkDir)) return R.fail(404, "上传会话不存在");

        try {
            long actualChunks = Files.list(chunkDir).count();
            if (actualChunks != totalChunks)
                return R.fail(400, "分片数量不匹配: 期望" + totalChunks + " 实际" + actualChunks);
        } catch (IOException e) {
            return R.fail(500, "检查分片失败");
        }

        String ext = "";
        if (fileName.lastIndexOf('.') > 0) ext = fileName.substring(fileName.lastIndexOf('.')).toLowerCase();
        String storeName2 = (String) body.getOrDefault("storeName", "");
        String safeStore2 = storeName2 != null && !storeName2.isEmpty() ? storeName2.replaceAll("[\\\\/:*?\"<>|]", "_") : "unknown";
        String month2 = YearMonth.now().toString();
        String destFilename = UUID.randomUUID().toString().replace("-", "") + ext;
        Path voucherDir = Paths.get(uploadPath).toAbsolutePath().normalize().resolve("voucher").resolve(safeStore2).resolve(month2);
        Path destFile;
        try {
            Files.createDirectories(voucherDir);
            destFile = voucherDir.resolve(destFilename);
        } catch (IOException e) {
            log.error("创建目标目录失败", e);
            return R.fail(500, "创建目标目录失败");
        }

        try (java.io.OutputStream out = Files.newOutputStream(destFile)) {
            for (int i = 0; i < totalChunks; i++) {
                Path chunkFile = chunkDir.resolve(String.valueOf(i));
                if (!Files.exists(chunkFile)) {
                    Files.deleteIfExists(destFile);
                    return R.fail(400, "缺少分片: " + i);
                }
                Files.copy(chunkFile, out);
            }
        } catch (IOException e) {
            log.error("合并分片失败", e);
            try { Files.deleteIfExists(destFile); } catch (IOException ignored) {}
            return R.fail(500, "合并文件失败");
        }

        try {
            for (int i = 0; i < totalChunks; i++) Files.deleteIfExists(chunkDir.resolve(String.valueOf(i)));
            Files.deleteIfExists(chunkDir);
        } catch (IOException ignored) {}

        boolean isVideo = ext.matches("\\.(mp4|mov|avi|mkv|webm)");
        // 转码已关闭

        String baseUrl = publicUrl.isBlank()
                ? "http://localhost:" + 8081 + "/storeInventory"
                : publicUrl;
        String encodedStore2 = safeStore2;
        try { encodedStore2 = URLEncoder.encode(safeStore2, "UTF-8").replace("+", "%20"); } catch (Exception ignored) {}
        String fullUrl = baseUrl + "/upload/voucher/" + encodedStore2 + "/" + month2 + "/" + destFilename;

        Map<String, String> result = new HashMap<>();
        result.put("url", fullUrl);
        result.put("filename", destFilename);
        if (isVideo) {
            String thumbPath = generateThumbnail(destFile.toAbsolutePath().toString());
            if (thumbPath != null) result.put("thumb", thumbUrl(fullUrl));
        }
        return R.ok(result);
    }
}
