package com.xzcpc.mp.service.impl;

import com.xzcpc.common.exception.BusinessException;
import com.xzcpc.mp.dto.BarcodeScanResult;
import com.xzcpc.mp.dto.BarcodeSupplementReq;
import com.xzcpc.mp.entity.BarcodeSupplement;
import com.xzcpc.mp.mapper.BarcodeSupplementMapper;
import com.xzcpc.task.entity.TaskZoneMaterial;
import com.xzcpc.task.mapper.TaskZoneMaterialMapper;
import com.xzcpc.template.entity.Material;
import com.xzcpc.template.mapper.MaterialMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * P0: A1 扫码盘点测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("A1 扫码盘点")
class BarcodeScanTest {

    @Mock
    private MaterialMapper materialMapper;

    @Mock
    private TaskZoneMaterialMapper taskZoneMaterialMapper;

    @Mock
    private BarcodeSupplementMapper barcodeSupplementMapper;

    @InjectMocks
    private MpInventoryServiceImpl inventoryService;

    // ===== 扫码测试 =====

    @Test
    @DisplayName("条码匹配成功 → 返回物料信息")
    void shouldReturnMaterialWhenBarcodeMatches() {
        Material material = buildMaterial("MAT001", "鸡胸肉", "500g/袋", "肉类");
        when(materialMapper.selectOne(any())).thenReturn(material);

        BarcodeScanResult result = inventoryService.scan("QM001", 1, 10, "store_001");

        assertTrue(result.isFound());
        assertEquals("MAT001", result.getMaterialId());
        assertEquals("鸡胸肉", result.getMaterialName());
    }

    @Test
    @DisplayName("条码未识别 → 返回补充申请引导")
    void shouldReturnNotFoundWhenBarcodeUnknown() {
        when(materialMapper.selectOne(any())).thenReturn(null).thenReturn(null);

        BarcodeScanResult result = inventoryService.scan("UNKNOWN_CODE", 1, 10, "store_001");

        assertFalse(result.isFound());
        assertEquals("supplement", result.getSuggestedAction());
    }

    @Test
    @DisplayName("空条码 → 参数校验失败")
    void shouldRejectEmptyBarcode() {
        assertThrows(BusinessException.class, () ->
            inventoryService.scan("", 1, 10, "store_001"));
    }

    @Test
    @DisplayName("条码格式异常（含特殊字符）→ 查询不到而非报错")
    void shouldHandleSpecialCharactersGracefully() {
        when(materialMapper.selectOne(any())).thenReturn(null).thenReturn(null);

        BarcodeScanResult result = inventoryService.scan("<script>alert(1)</script>", 1, 10, "store_001");

        assertFalse(result.isFound());
        // 不应抛出异常，仅返回未找到
    }

    @Test
    @DisplayName("物料在其他分区 → 返回 notInCurrentZone=true")
    void shouldIndicateMaterialInOtherZone() {
        Material material = buildMaterial("MAT002", "食用油", "5L/桶", "调料");
        when(materialMapper.selectOne(any())).thenReturn(null, material);

        TaskZoneMaterial otherZoneMaterial = new TaskZoneMaterial();
        otherZoneMaterial.setTaskZoneId(20); // 不同分区
        otherZoneMaterial.setMaterialId("MAT002");
        otherZoneMaterial.setInputStatus("not_entered");
        when(taskZoneMaterialMapper.selectList(any())).thenReturn(List.of(otherZoneMaterial));

        BarcodeScanResult result = inventoryService.scan("QM002", 1, 10, "store_001");

        assertTrue(result.isFound());
        assertTrue(result.isNotInCurrentZone());
        assertEquals("add_to_zone", result.getSuggestedAction());
    }

    @Test
    @DisplayName("物料已录入 → 返回 alreadyEntered=true")
    void shouldIndicateAlreadyEntered() {
        Material material = buildMaterial("MAT003", "酱油", "500ml/瓶", "调料");
        when(materialMapper.selectOne(any())).thenReturn(null, material);

        TaskZoneMaterial enteredMaterial = new TaskZoneMaterial();
        enteredMaterial.setTaskZoneId(10); // 当前分区
        enteredMaterial.setMaterialId("MAT003");
        enteredMaterial.setInputStatus("entered");
        enteredMaterial.setInputQty(new java.math.BigDecimal("5"));
        when(taskZoneMaterialMapper.selectList(any())).thenReturn(List.of(enteredMaterial));

        BarcodeScanResult result = inventoryService.scan("QM003", 1, 10, "store_001");

        assertTrue(result.isFound());
        assertTrue(result.isAlreadyEntered());
        assertEquals("scan_existing", result.getSuggestedAction());
        assertNotNull(result.getEnteredQty());
    }

    @Test
    @DisplayName("无任务上下文 → 仅返回物料基本信息")
    void shouldReturnBasicInfoWithoutTaskContext() {
        Material material = buildMaterial("MAT004", "面粉", "25kg/袋", "主食");
        when(materialMapper.selectOne(any())).thenReturn(material);

        BarcodeScanResult result = inventoryService.scan("QM004", null, null, "store_001");

        assertTrue(result.isFound());
        assertEquals("MAT004", result.getMaterialId());
        assertEquals("add_to_zone", result.getSuggestedAction());
    }

    // ===== 条码补充测试 =====

    @Test
    @DisplayName("条码补充申请成功")
    void shouldSubmitBarcodeSupplement() {
        BarcodeSupplementReq req = new BarcodeSupplementReq();
        req.setBarcode("NEW_BARCODE_001");
        req.setMaterialName("测试物料");
        req.setRemark("新增条码");

        when(barcodeSupplementMapper.insert(any(BarcodeSupplement.class))).thenReturn(1);

        assertDoesNotThrow(() ->
            inventoryService.submitBarcodeSupplement(req, "store_001", "openid_001"));

        verify(barcodeSupplementMapper, times(1)).insert(any(BarcodeSupplement.class));
    }

    @Test
    @DisplayName("条码补充申请 → 空条码被拦截")
    void shouldRejectEmptyBarcodeSupplement() {
        BarcodeSupplementReq req = new BarcodeSupplementReq();
        req.setBarcode("");

        assertThrows(BusinessException.class, () ->
            inventoryService.submitBarcodeSupplement(req, "store_001", "openid_001"));
    }

    // ===== 工厂方法测试 =====

    @Test
    @DisplayName("BarcodeScanResult.notFound 返回正确的建议操作")
    void notFoundResultShouldSuggestSupplement() {
        BarcodeScanResult result = BarcodeScanResult.notFound("UNKNOWN");
        assertFalse(result.isFound());
        assertEquals("supplement", result.getSuggestedAction());
    }

    @Test
    @DisplayName("BarcodeScanResult.alreadyScanned 返回正确的建议操作")
    void alreadyScannedResultShouldSuggestScanExisting() {
        BarcodeScanResult result = BarcodeScanResult.alreadyScanned("M1", "物料", "规格", "单位", 10, "分类");
        assertTrue(result.isFound());
        assertTrue(result.isAlreadyEntered());
        assertEquals("scan_existing", result.getSuggestedAction());
    }

    // ===== 辅助方法 =====

    private Material buildMaterial(String materialId, String name, String spec, String category) {
        Material m = new Material();
        m.setMaterialId(materialId);
        m.setMaterialName(name);
        m.setSpec(spec);
        m.setCategory(category);
        m.setQmCode("QM_" + materialId);
        return m;
    }
}
