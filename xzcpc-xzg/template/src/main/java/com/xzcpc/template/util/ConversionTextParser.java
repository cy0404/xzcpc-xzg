package com.xzcpc.template.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
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

    /**
     * 归一化换算条目：只保留"每个盘点单位 → 基础单位"的直接行，中间链折叠掉。
     * 1) to_unit == baseUnit 的条目原样保留（已是直接行，保持原顺序）
     * 2) 其余单位沿换算边双向 BFS 折叠到 baseUnit（反向边系数取倒数），路径系数累乘生成直接行
     * 3) 找不到路径的单位丢弃并 warn（录入了也无法换算，不落无效行）
     *
     * 例：件→包(6)、包→g(1000)，base=g → 折叠出 件→g(6000)
     * 例：1条=5卷，base=条 → 反向折叠出 卷→条(0.2)
     *
     * @return 归一化后的条目列表；无换算或 baseUnit 为空时原样返回
     */
    public static List<ConversionEntry> foldToBase(String materialId, String materialName,
                                                   List<ConversionEntry> entries, String baseUnit) {
        if (entries == null || entries.isEmpty() || !StringUtils.hasText(baseUnit)) {
            return entries;
        }
        List<ConversionEntry> result = new ArrayList<>();
        Set<String> covered = new HashSet<>();
        for (ConversionEntry e : entries) {
            if (baseUnit.equals(e.toUnit())) {
                result.add(e);
                covered.add(e.fromUnit());
            }
        }
        // 双向邻接表：1 from = factor to
        Map<String, List<Edge>> graph = new HashMap<>();
        for (ConversionEntry e : entries) {
            BigDecimal factor = e.toQuantity().divide(e.fromQuantity(), 10, RoundingMode.HALF_UP);
            graph.computeIfAbsent(e.fromUnit(), k -> new ArrayList<>())
                    .add(new Edge(e.toUnit(), factor));
            graph.computeIfAbsent(e.toUnit(), k -> new ArrayList<>())
                    .add(new Edge(e.fromUnit(), BigDecimal.ONE.divide(factor, 10, RoundingMode.HALF_UP)));
        }
        Set<String> allUnits = new LinkedHashSet<>();
        for (ConversionEntry e : entries) {
            allUnits.add(e.fromUnit());
            allUnits.add(e.toUnit());
        }
        for (String unit : allUnits) {
            if (baseUnit.equals(unit) || covered.contains(unit)) {
                continue;
            }
            List<BigDecimal> path = bfs(graph, unit, baseUnit);
            if (path == null) {
                log.warn("物料 {}[{}] 单位 [{}] 无法换算到基础单位 [{}]，换算行已丢弃",
                        materialId, materialName, unit, baseUnit);
                continue;
            }
            BigDecimal k = BigDecimal.ONE;
            for (BigDecimal factor : path) {
                k = k.multiply(factor);
            }
            result.add(new ConversionEntry(BigDecimal.ONE, unit, k.stripTrailingZeros(), baseUnit));
        }
        return result;
    }

    /** BFS 求 from → to 的边系数路径（最短），无路径返回 null */
    private static List<BigDecimal> bfs(Map<String, List<Edge>> graph, String from, String to) {
        if (from.equals(to)) {
            return List.of();
        }
        Queue<String> queue = new ArrayDeque<>();
        Map<String, List<BigDecimal>> pathMap = new HashMap<>();
        Set<String> visited = new HashSet<>();
        queue.add(from);
        pathMap.put(from, new ArrayList<>());
        visited.add(from);
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            for (Edge edge : graph.getOrDefault(cur, List.of())) {
                if (visited.contains(edge.to)) {
                    continue;
                }
                List<BigDecimal> path = new ArrayList<>(pathMap.get(cur));
                path.add(edge.factor);
                if (edge.to.equals(to)) {
                    return path;
                }
                pathMap.put(edge.to, path);
                visited.add(edge.to);
                queue.add(edge.to);
            }
        }
        return null;
    }

    /** 换算边：1 from = factor to */
    private record Edge(String to, BigDecimal factor) {}
}
