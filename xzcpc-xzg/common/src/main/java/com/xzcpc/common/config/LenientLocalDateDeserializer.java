package com.xzcpc.common.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.LocalDate;

/**
 * 宽松的 LocalDate 反序列化器，不限分隔符和零填充。
 * 示例：2026-06-04 / 2026-6-4 / 2026.06.04 / 2026.06.4 / 2026/06/04 / 2026-06.03
 */
public class LenientLocalDateDeserializer extends JsonDeserializer<LocalDate> {

    @Override
    public LocalDate deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String text = p.getText().trim();
        if (text.isEmpty()) {
            return null;
        }
        // 按非数字字符 split，提取年月日
        String[] parts = text.split("[^0-9]+");
        if (parts.length >= 3) {
            try {
                int y = Integer.parseInt(parts[0]);
                int m = Integer.parseInt(parts[1]);
                int d = Integer.parseInt(parts[2]);
                return LocalDate.of(y, m, d);
            } catch (Exception ignored) {
            }
        }
        throw new IOException("无法解析日期: " + text + "，示例格式: 2026-06-04 / 2026.6.4 / 2026/06/04");
    }
}
