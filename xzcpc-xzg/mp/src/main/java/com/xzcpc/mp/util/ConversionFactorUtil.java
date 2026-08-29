package com.xzcpc.mp.util;

import com.xzcpc.template.entity.MaterialConversionRule;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 单位换算链工具（从 MpTransferController 抽取，供调货与智能订货共用）。
 * 换算规则语义：from_qty from_unit = to_qty to_unit。
 */
public final class ConversionFactorUtil {

    private ConversionFactorUtil() {}

    /**
     * 通过 unit 类换算链计算：1 stockUnit = ? baseUnit
     *
     * @param stockUnit 订货单位（起点）
     * @param baseUnit  基础盘点单位（终点）
     * @param rules     该物料的换算规则列表（仅 unit 类参与计算）
     * @return factor = 多少 baseUnit 等于 1 stockUnit；相同单位返回 1；无法换算返回 null
     */
    public static BigDecimal computeConversionFactor(
            String stockUnit, String baseUnit,
            List<MaterialConversionRule> rules) {
        if (rules.isEmpty()) return null;
        if (stockUnit != null && stockUnit.equals(baseUnit)) return BigDecimal.ONE;

        // 构建双向图: unit -> [(ratio, nextUnit)]
        // ratio 表示"1 unit = ratio nextUnit"
        Map<String, List<ConversionEdge>> graph = new HashMap<>();
        for (MaterialConversionRule r : rules) {
            if (!"unit".equals(r.getConversionType())) continue; // 仅单位换算
            // from_qty from_unit = to_qty to_unit
            // → 1 from_unit = (to_qty / from_qty) to_unit
            // → 1 to_unit = (from_qty / to_qty) from_unit
            BigDecimal fromToTo = r.getToQuantity().divide(r.getFromQuantity(), 10, RoundingMode.HALF_UP);
            BigDecimal toToFrom = r.getFromQuantity().divide(r.getToQuantity(), 10, RoundingMode.HALF_UP);
            graph.computeIfAbsent(r.getFromUnit(), k -> new ArrayList<>())
                    .add(new ConversionEdge(r.getToUnit(), fromToTo));
            graph.computeIfAbsent(r.getToUnit(), k -> new ArrayList<>())
                    .add(new ConversionEdge(r.getFromUnit(), toToFrom));
        }

        if (!graph.containsKey(stockUnit)) return null;

        // BFS from stockUnit to baseUnit
        Map<String, BigDecimal> visited = new HashMap<>();
        Deque<String> queue = new ArrayDeque<>();
        visited.put(stockUnit, BigDecimal.ONE);
        queue.add(stockUnit);

        while (!queue.isEmpty()) {
            String cur = queue.poll();
            BigDecimal curFactor = visited.get(cur);
            if (cur.equals(baseUnit)) return curFactor;

            List<ConversionEdge> edges = graph.getOrDefault(cur, List.of());
            for (ConversionEdge e : edges) {
                if (visited.containsKey(e.toUnit)) continue;
                BigDecimal newFactor = curFactor.multiply(e.ratio);
                visited.put(e.toUnit, newFactor);
                queue.add(e.toUnit);
            }
        }
        return null;
    }

    /** BFS 边 */
    private record ConversionEdge(String toUnit, BigDecimal ratio) {}
}
