package com.xzcpc.template.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.xzcpc.template.entity.Material;
import com.xzcpc.template.entity.MaterialInventoryRule;
import com.xzcpc.template.entity.SemiFormulaItem;
import com.xzcpc.template.entity.SemiFormulaVersion;
import com.xzcpc.template.entity.SemiProduct;
import com.xzcpc.template.mapper.MaterialInventoryRuleMapper;
import com.xzcpc.template.mapper.MaterialMapper;
import com.xzcpc.template.mapper.SemiFormulaItemMapper;
import com.xzcpc.template.mapper.SemiFormulaVersionMapper;
import com.xzcpc.template.mapper.SemiProductMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 半成品 BOM 爆炸引擎单元测试。
 *
 * 数据镜像测试库 2026-08-31 实测的 WP0531（牛油果泥）：
 * 净出量 1259.34 kg，配方 2 行（WP0338 238.43g + WP0352 1020.91g，损耗率均 0.0094）。
 *
 * 期望值用与实现相同的公式独立计算：
 *   factor = 100 / 1259.34 = 0.07940667
 *   WP0338: 0.07940667 × 238.43 × 1.0094 g → kg → 0.0191
 *   WP0352: 0.07940667 × 1020.91 × 1.0094 g → kg → 0.0818
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("半成品BOM爆炸引擎")
class SemiFormulaExplodeServiceTest {

    private static final String SQL_ENABLED =
            "SELECT config_value FROM sys_config WHERE config_key='semi_bom_explode_enabled' LIMIT 1";
    private static final String SQL_BLACKLIST =
            "SELECT config_value FROM sys_config WHERE config_key='semi_no_explode_codes' LIMIT 1";

    @Mock
    private SemiProductMapper semiProductMapper;
    @Mock
    private SemiFormulaVersionMapper versionMapper;
    @Mock
    private SemiFormulaItemMapper itemMapper;
    @Mock
    private MaterialMapper materialMapper;
    @Mock
    private MaterialInventoryRuleMapper ruleMapper;
    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private SemiFormulaExplodeServiceImpl service;

    // ===== WP0531 单层（MATERIAL 两行，g → kg） =====
    private static final String SS1 = "ss1-wp0531";
    private static final String MS = "ms-wp0531";   // 半成品自身在 material 表的物料id
    private static final String MA = "ma-wp0338";
    private static final String MB = "ma-wp0352";

    private SemiProduct semi(String id, String code, String netOut, String netOutUnit, String unit) {
        SemiProduct sp = new SemiProduct();
        sp.setSemiId(id);
        sp.setCode(code);
        sp.setName("半成品-" + code);
        if (netOut != null) sp.setNetOutputQuantity(new BigDecimal(netOut));
        sp.setNetOutputUnit(netOutUnit);
        sp.setUnit(unit);
        sp.setStatus("ENABLED");
        return sp;
    }

    private SemiFormulaVersion version(long versionId, String semiId) {
        SemiFormulaVersion v = new SemiFormulaVersion();
        v.setVersionId(versionId);
        v.setSemiId(semiId);
        v.setStatus("ACTIVE");
        return v;
    }

    private SemiFormulaItem materialItem(long versionId, String semiId, String mid, String qmCode,
                                         String qty, String unit, String lossRate) {
        SemiFormulaItem it = new SemiFormulaItem();
        it.setVersionId(versionId);
        it.setSemiId(semiId);
        it.setItemType("MATERIAL");
        it.setMaterialId(mid);
        it.setQimaiProductCode(qmCode);
        it.setItemName("原料-" + qmCode);
        it.setQuantity(new BigDecimal(qty));
        it.setUnit(unit);
        if (lossRate != null) it.setLossRate(new BigDecimal(lossRate));
        return it;
    }

    private SemiFormulaItem semiItem(long versionId, String semiId, String childSemiId,
                                     String qty, String unit, String lossRate) {
        SemiFormulaItem it = new SemiFormulaItem();
        it.setVersionId(versionId);
        it.setSemiId(semiId);
        it.setItemType("SEMI_FINISHED");
        it.setSemiFinishedProductId(childSemiId);
        it.setItemName("子半成品-" + childSemiId);
        it.setQuantity(new BigDecimal(qty));
        it.setUnit(unit);
        if (lossRate != null) it.setLossRate(new BigDecimal(lossRate));
        return it;
    }

    private Material material(String mid, String qmCode) {
        Material m = new Material();
        m.setMaterialId(mid);
        m.setQmCode(qmCode);
        return m;
    }

    private MaterialInventoryRule rule(String mid, String baseUnit) {
        MaterialInventoryRule r = new MaterialInventoryRule();
        r.setMaterialId(mid);
        r.setBaseUnit(baseUnit);
        return r;
    }

    /** 默认全量数据 = 测试库镜像（WP0531 + 3 个基础原料 + 自身），黑名单空 */
    @BeforeEach
    void setUpSnapshot() {
        when(jdbcTemplate.queryForObject(eq(SQL_ENABLED), eq(String.class))).thenReturn("1");
        when(jdbcTemplate.queryForObject(eq(SQL_BLACKLIST), eq(String.class))).thenReturn("");

        when(semiProductMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(semi(SS1, "WP0531", "1259.3400", "kg", "kg")));
        when(versionMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(version(79L, SS1)));
        when(itemMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(
                        materialItem(79L, SS1, MA, "WP0338", "238.43", "g", "0.0094"),
                        materialItem(79L, SS1, MB, "WP0352", "1020.91", "g", "0.0094")));
        when(materialMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(material(MS, "WP0531"), material(MA, "WP0338"), material(MB, "WP0352")));
        when(ruleMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(rule(MS, "kg"), rule(MA, "kg"), rule(MB, "kg")));
        service.prepare();
    }

    // ==================== 开关 ====================

    @Test
    @DisplayName("isEnabled：开关=1 返回 true，异常返回 false")
    void isEnabled() {
        assertTrue(service.isEnabled());
        when(jdbcTemplate.queryForObject(eq(SQL_ENABLED), eq(String.class))).thenThrow(new RuntimeException("db down"));
        assertFalse(service.isEnabled());
    }

    @Test
    @DisplayName("isBlacklisted：逗号分隔匹配编码")
    void isBlacklisted() {
        when(jdbcTemplate.queryForObject(eq(SQL_BLACKLIST), eq(String.class))).thenReturn("WP0531, WP9999");
        assertTrue(service.isBlacklisted("WP0531"));
        assertTrue(service.isBlacklisted("WP9999"));
        assertFalse(service.isBlacklisted("WP0338"));
        assertFalse(service.isBlacklisted(null));
    }

    // ==================== WP0531 单层爆炸 ====================

    @Test
    @DisplayName("WP0531 爆炸 100kg → WP0338 0.0191kg + WP0352 0.0818kg")
    void explodeWp0531() {
        Map<String, BigDecimal> result = service.explode(SS1, new BigDecimal("100"), "kg");
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(new BigDecimal("0.0191"), result.get(MA));
        assertEquals(new BigDecimal("0.0818"), result.get(MB));
    }

    @Test
    @DisplayName("单位为空 → 用半成品自身基础单位（kg）兜底，结果一致")
    void explodeWithNullUnitFallsBackToBaseUnit() {
        Map<String, BigDecimal> result = service.explode(SS1, new BigDecimal("100"), null);
        assertNotNull(result);
        assertEquals(new BigDecimal("0.0191"), result.get(MA));
    }

    @Test
    @DisplayName("explodeByCode：qm_code 通道（PG 消耗/采购路径）结果一致")
    void explodeByCode() {
        Map<String, BigDecimal> result = service.explodeByCode("WP0531", new BigDecimal("100"), "kg");
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(new BigDecimal("0.0191"), result.get(MA));
    }

    @Test
    @DisplayName("explode：material_id 通道（盘点/报损传盘点主键，与 semi_id 不同值，经 qm_code 转半成品）")
    void explodeWithMaterialId() {
        // 调用方传 material 表主键 MS，内部必须解析到半成品 SS1（双身份兜底）
        Map<String, BigDecimal> result = service.explode(MS, new BigDecimal("100"), "kg");
        assertNotNull(result);
        assertEquals(new BigDecimal("0.0191"), result.get(MA));
        assertEquals(new BigDecimal("0.0818"), result.get(MB));
    }

    @Test
    @DisplayName("数量为 0 或 null → 空 map（不爆炸也不报错）")
    void zeroOrNullQty() {
        assertEquals(Map.of(), service.explode(SS1, BigDecimal.ZERO, "kg"));
        assertEquals(Map.of(), service.explode(SS1, null, "kg"));
    }

    @Test
    @DisplayName("未知半成品 id / code → null（保留自身行）")
    void unknownSemi() {
        assertNull(service.explode("not-exist", new BigDecimal("100"), "kg"));
        assertNull(service.explodeByCode("WP9999", new BigDecimal("100"), "kg"));
    }

    // ==================== 兜底（fallback = null） ====================

    @Test
    @DisplayName("黑名单半成品 → null（逃生舱）")
    void blacklistFallback() {
        when(jdbcTemplate.queryForObject(eq(SQL_BLACKLIST), eq(String.class))).thenReturn("WP0531");
        assertNull(service.explode(SS1, new BigDecimal("100"), "kg"));
    }

    @Test
    @DisplayName("无 ACTIVE 配方 → null")
    void noFormulaFallback() {
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        service.prepare();
        assertNull(service.explode(SS1, new BigDecimal("100"), "kg"));
    }

    @Test
    @DisplayName("净出量缺失 → null")
    void noNetOutputFallback() {
        when(semiProductMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(semi(SS1, "WP0531", null, "kg", "kg")));
        service.prepare();
        assertNull(service.explode(SS1, new BigDecimal("100"), "kg"));
    }

    @Test
    @DisplayName("数量单位无法换算（箱）→ null，防算错")
    void unknownUnitFallback() {
        assertNull(service.explode(SS1, new BigDecimal("100"), "箱"));
    }

    @Test
    @DisplayName("半成品自身无盘点基础单位且未传单位 → null")
    void noBaseUnitFallback() {
        when(ruleMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(rule(MA, "kg"), rule(MB, "kg"))); // MS 无规则
        service.prepare();
        assertNull(service.explode(SS1, new BigDecimal("100"), null));
    }

    @Test
    @DisplayName("配方行用量单位无法换算（件）→ null，防算错")
    void itemUnitUnconvertibleFallback() {
        when(itemMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(
                        materialItem(79L, SS1, MA, "WP0338", "238.43", "件", "0.0094"),
                        materialItem(79L, SS1, MB, "WP0352", "1020.91", "g", "0.0094")));
        service.prepare();
        assertNull(service.explode(SS1, new BigDecimal("100"), "kg"));
    }

    // ==================== 递归（SEMI_FINISHED） ====================

    @Test
    @DisplayName("递归爆炸：WP0200 → 子半成品 WP0201 → 原料 WP0400 55kg")
    void recursiveExplode() {
        // WP0200: 净出 100kg，配方 1 行 SEMI_FINISHED → WP0201 用量 50kg 无损耗
        String s2 = "ss2-wp0200";
        String s3 = "ss3-wp0201";
        String mc = "mc-wp0400";
        when(semiProductMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(
                        semi(s2, "WP0200", "100.0000", "kg", "kg"),
                        semi(s3, "WP0201", "10.0000", "kg", "kg")));
        when(versionMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(version(81L, s2), version(82L, s3)));
        when(itemMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(
                        semiItem(81L, s2, s3, "50", "kg", null),
                        materialItem(82L, s3, mc, "WP0400", "5", "kg", "0.1")));
        when(materialMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(material(mc, "WP0400")));
        when(ruleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(rule(mc, "kg")));
        service.prepare();

        // 200kg / 100kg净出 × 50kg × 1.0 = 100kg 子半成品；/10kg净出 × 5kg × 1.1 = 55kg
        Map<String, BigDecimal> result = service.explode(s2, new BigDecimal("200"), "kg");
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(new BigDecimal("55.0000"), result.get(mc));
    }

    @Test
    @DisplayName("递归环保护：A→B→A → null")
    void cycleProtection() {
        String sa = "ssa";
        String sb = "ssb";
        when(semiProductMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(semi(sa, "WP1001", "10.0000", "kg", "kg"),
                        semi(sb, "WP1002", "10.0000", "kg", "kg")));
        when(versionMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(version(91L, sa), version(92L, sb)));
        when(itemMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(
                        semiItem(91L, sa, sb, "1", "kg", null),
                        semiItem(92L, sb, sa, "1", "kg", null)));
        when(materialMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(ruleMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        service.prepare();

        assertNull(service.explode(sa, new BigDecimal("100"), "kg"));
    }

    @Test
    @DisplayName("深度保护：12 层递归链（11 跳 > MAX_DEPTH=10）→ null")
    void depthProtection() {
        int n = 12;
        java.util.List<SemiProduct> semis = new java.util.ArrayList<>();
        java.util.List<SemiFormulaVersion> versions = new java.util.ArrayList<>();
        java.util.List<SemiFormulaItem> items = new java.util.ArrayList<>();
        for (int i = 1; i <= n; i++) {
            String id = "sd" + i;
            semis.add(semi(id, "WP9" + i, "10.0000", "kg", "kg"));
            versions.add(version(100L + i, id));
            if (i < n) {
                items.add(semiItem(100L + i, id, "sd" + (i + 1), "1", "kg", null));
            } else {
                items.add(materialItem(100L + i, id, "md-leaf", "WP9000", "1", "kg", null));
            }
        }
        when(semiProductMapper.selectList(any(Wrapper.class))).thenReturn(semis);
        when(versionMapper.selectList(any(Wrapper.class))).thenReturn(versions);
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(items);
        when(materialMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(material("md-leaf", "WP9000")));
        when(ruleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(rule("md-leaf", "kg")));
        service.prepare();

        assertNull(service.explode("sd1", new BigDecimal("100"), "kg"));
    }

    // ==================== 可爆炸性判断 ====================

    @Test
    @DisplayName("isExplodable / isExplodableCode / explodableQmCodes")
    void explodableChecks() {
        assertTrue(service.isExplodable(MS));             // 盘点主键 material_id 通道
        assertTrue(service.isExplodableCode("WP0531"));   // qm_code 通道
        assertFalse(service.isExplodable("not-exist"));
        assertFalse(service.isExplodableCode("WP9999"));

        Set<String> codes = service.explodableQmCodes();
        assertTrue(codes.contains("WP0531"));
        assertFalse(codes.contains("WP9999"));
    }

    @Test
    @DisplayName("黑名单半成品不可爆炸")
    void blacklistNotExplodable() {
        when(jdbcTemplate.queryForObject(eq(SQL_BLACKLIST), eq(String.class))).thenReturn("WP0531");
        assertFalse(service.isExplodable(MS));
        assertFalse(service.isExplodableCode("WP0531"));
        assertFalse(service.explodableQmCodes().contains("WP0531"));
    }

    @Test
    @DisplayName("无 ACTIVE 配方半成品不可爆炸")
    void noFormulaNotExplodable() {
        when(itemMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        service.prepare();
        assertFalse(service.isExplodable(MS));
        assertFalse(service.isExplodableCode("WP0531"));
        assertFalse(service.explodableQmCodes().contains("WP0531"));
    }
}
