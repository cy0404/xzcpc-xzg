package com.xzcpc.mp.job;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 孤儿分片目录清理。
 * <p>
 * 客户端中断/放弃上传后，chunks/ 下会残留未合并的分片目录（每个最多几十 MB），
 * 每天凌晨 3:30 清理最后修改时间超过 24 小时的目录。
 * 只删除名字匹配 32 位 hex（uploadId 格式）的目录，防止误删其他数据。
 */
@Slf4j
@Component
public class ChunkCleanupJob {

    @Value("${app.upload.path:./upload}")
    private String uploadPath;

    private static final long MAX_AGE_MS = 24L * 60 * 60 * 1000;
    private static final Pattern UPLOAD_ID = Pattern.compile("[0-9a-f]{32}");

    @Scheduled(cron = "0 30 3 * * ?")
    public void cleanOrphanChunks() {
        Path chunksDir = Paths.get(uploadPath).toAbsolutePath().normalize().resolve("chunks");
        if (!Files.isDirectory(chunksDir)) return;
        try (Stream<Path> dirs = Files.list(chunksDir)) {
            dirs.filter(p -> UPLOAD_ID.matcher(p.getFileName().toString()).matches()).forEach(dir -> {
                try {
                    long age = System.currentTimeMillis() - Files.getLastModifiedTime(dir).toMillis();
                    if (age > MAX_AGE_MS) {
                        try (Stream<Path> files = Files.walk(dir)) {
                            files.sorted(Comparator.reverseOrder())
                                    .forEach(f -> {
                                        try {
                                            Files.deleteIfExists(f);
                                        } catch (IOException e) {
                                            log.warn("删除孤儿分片失败: {}", f, e);
                                        }
                                    });
                        }
                        log.info("已清理孤儿分片目录: {}", dir);
                    }
                } catch (IOException e) {
                    log.warn("检查分片目录失败: {}", dir, e);
                }
            });
        } catch (IOException e) {
            log.warn("清理孤儿分片目录失败: {}", chunksDir, e);
        }
    }
}
