package com.xzcpc.template.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * xinfo 换算文本解析器。
 * 输入如 "1瓶=950g, 1件=6瓶" / "1kg=20包"，按分隔符切段后解析为换算三元组。
 * 解析失败的分段跳过并告警，不中断整体同步。
 */
@Slf4j
public final class ConversionTextParser {

    private ConversionTextParser() {}

    /** 单段正则：数量 单位 = 数量 单位（单位可含空格/中文/字母数字） */
    private static final Pattern SEGMENT_PATTERN =
            Pattern.compile("^(\\d+(?:\\.\\d+)?)\\s*([^=]+?)\\s*=\\s*(\\d+(?:\\.\\d+)?)\\s*(.+)$");

    /** 单条换算：from_qty from_unit = to_qty to_unit */
    public record ConversionEntry(BigDecimal fromQuantity, String fromUnit,
                                  BigDecimal toQuantity, String toUnit) {}

    /** 规格单段正则："1000g/kg"、"50g/包"、"1kg/瓶"（to_qty 可省略，缺省 1） */
    private static final Pattern SPEC_PATTERN =
            Pattern.compile("^(\\d+(?:\\.\\d+)?)\\s*([^/]+?)\\s*/\\s*(\\d+(?:\\.\\d+)?)?\\s*([^/]+)$");

    /**
     * 解析规格换算（半成品用）：规格形如 "1000g/kg"（每 kg 含 1000g）、"50g/包"、"1kg/瓶"，
     * 生成换算 X a = Y b（Y 缺省为 1），即"包装小单位 → 基础大单位"。
     * 纯单位规格（kg、kg/kg、1kg）无换算信息，返回空列表；非法规格跳过并 warn。
     *
     * @param materialId 物料 ID（日志定位用）
     * @param materialName 物料名称（日志定位用）
     * @param spec 规格文本，如 "1000g/kg"；空白/无斜杠返回空列表
     */
    public static List<ConversionEntry> parseSpec(String materialId, String materialName, String spec) {
        List<ConversionEntry> entries = new ArrayList<>();
        if (!StringUtils.hasText(spec)) {
            return entries;
        }
        Matcher matcher = SPEC_PATTERN.matcher(spec.trim());
        if (!matcher.matches()) {
            return entries;
        }
        try {
            BigDecimal fromQty = new BigDecimal(matcher.group(1));
            String fromUnit = matcher.group(2).trim();
            BigDecimal toQty = matcher.group(3) != null && StringUtils.hasText(matcher.group(3))
                    ? new BigDecimal(matcher.group(3)) : BigDecimal.ONE;
            String toUnit = matcher.group(4).trim();
            if (fromQty.compareTo(BigDecimal.ZERO) <= 0 || toQty.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("物料 {}[{}] 规格换算数量非法（<=0），已跳过: {}", materialId, materialName, spec.trim());
                return entries;
            }
            if (!StringUtils.hasText(fromUnit) || !StringUtils.hasText(toUnit)
                    || fromUnit.contains("/") || toUnit.contains("/")) {
                log.warn("物料 {}[{}] 规格换算单位非法，已跳过: {}", materialId, materialName, spec.trim());
                return entries;
            }
            entries.add(new ConversionEntry(fromQty, fromUnit, toQty, toUnit));
        } catch (NumberFormatException e) {
            log.warn("物料 {}[{}] 规格换算数量格式非法，已跳过: {}", materialId, materialName, spec.trim());
        }
        return entries;
    }

    /**
     * 解析换算文本，返回按原文顺序的换算条目；非法分段跳过并 warn。
     *
     * @param materialId 物料 ID（日志定位用）
     * @param materialName 物料名称（日志定位用）
     * @param text 换算文本，如 "1瓶=950g, 1件=6瓶"；空白返回空列表
     */
    public static List<ConversionEntry> parse(String materialId, String materialName, String text) {
        List<ConversionEntry> entries = new ArrayList<>();
        if (!StringUtils.hasText(text)) {
            return entries;
        }
        for (String segment : text.split("[,，;；]")) {
            if (!StringUtils.hasText(segment)) {
                continue;
            }
            String trimmed = segment.trim();
            Matcher matcher = SEGMENT_PATTERN.matcher(trimmed);
            if (!matcher.matches()) {
                log.warn("物料 {}[{}] 换算段解析失败，已跳过: {}", materialId, materialName, trimmed);
                continue;
            }
            try {
                BigDecimal fromQty = new BigDecimal(matcher.group(1));
                String fromUnit = matcher.group(2).trim();
                BigDecimal toQty = new BigDecimal(matcher.group(3));
                String toUnit = matcher.group(4).trim();
                if (fromQty.compareTo(BigDecimal.ZERO) <= 0 || toQty.compareTo(BigDecimal.ZERO) <= 0) {
                    log.warn("物料 {}[{}] 换算段数量非法（<=0），已跳过: {}", materialId, materialName, trimmed);
                    continue;
                }
                if (!StringUtils.hasText(fromUnit) || !StringUtils.hasText(toUnit)) {
                    log.warn("物料 {}[{}] 换算段单位为空，已跳过: {}", materialId, materialName, trimmed);
                    continue;
                }
                entries.add(new ConversionEntry(fromQty, fromUnit, toQty, toUnit));
            } catch (NumberFormatException e) {
                log.warn("物料 {}[{}] 换算段数量格式非法，已跳过: {}", materialId, materialName, trimmed);
            }
        }
        return entries;
    }
}
