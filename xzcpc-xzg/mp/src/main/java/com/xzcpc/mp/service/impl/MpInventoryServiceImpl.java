package com.xzcpc.mp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xzcpc.common.exception.BusinessException;
import com.xzcpc.mp.dto.BarcodeScanResult;
import com.xzcpc.mp.dto.BarcodeSupplementReq;
import com.xzcpc.mp.entity.BarcodeSupplement;
import com.xzcpc.mp.mapper.BarcodeSupplementMapper;
import com.xzcpc.mp.service.MpInventoryService;
import com.xzcpc.task.entity.TaskZoneMaterial;
import com.xzcpc.task.mapper.TaskZoneMaterialMapper;
import com.xzcpc.template.entity.Material;
import com.xzcpc.template.mapper.MaterialMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * P0 A1: 盘点扫码服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MpInventoryServiceImpl implements MpInventoryService {

    private final MaterialMapper materialMapper;
    private final TaskZoneMaterialMapper taskZoneMaterialMapper;
    private final BarcodeSupplementMapper barcodeSupplementMapper;

    @Override
    public BarcodeScanResult scan(String barcode, Integer taskId, Integer zoneId, String storeId) {
        if (!StringUtils.hasText(barcode)) {
            throw new BusinessException("条码不能为空");
        }

        // 1. 按 qm_code 或 material_id 精确匹配（二者均可能出现在条码中）
        Material material = materialMapper.selectOne(new LambdaQueryWrapper<Material>()
                .eq(Material::getQmCode, barcode)
                .last("LIMIT 1"));
        if (material == null) {
            material = materialMapper.selectOne(new LambdaQueryWrapper<Material>()
                    .eq(Material::getMaterialId, barcode)
                    .last("LIMIT 1"));
        }

        // 2. 条码未识别 → 返回补充申请引导
        if (material == null) {
            return BarcodeScanResult.notFound(barcode);
        }

        String unit = StringUtils.hasText(material.getSpec()) ? material.getSpec() : "";
        String category = material.getCategory() != null ? material.getCategory() : "";

        // 3. 无任务/分区上下文 → 仅返回物料信息
        if (taskId == null || zoneId == null) {
            return BarcodeScanResult.found(
                    material.getMaterialId(), material.getMaterialName(),
                    unit, unit, category);
        }

        // 4. 检查物料在任务中的分区归属
        List<TaskZoneMaterial> taskMaterials = taskZoneMaterialMapper.selectList(
                new LambdaQueryWrapper<TaskZoneMaterial>()
                        .eq(TaskZoneMaterial::getTaskId, taskId)
                        .eq(TaskZoneMaterial::getMaterialId, material.getMaterialId()));

        if (taskMaterials.isEmpty()) {
            // 物料不在当前任务中
            BarcodeScanResult r = BarcodeScanResult.found(
                    material.getMaterialId(), material.getMaterialName(),
                    unit, unit, category);
            r.setNotInCurrentZone(true);
            r.setInZoneName("（物料不在当前盘点任务中）");
            r.setSuggestedAction("add_to_zone");
            return r;
        }

        // 5. 检查物料是否在当前分区
        boolean inCurrentZone = taskMaterials.stream()
                .anyMatch(m -> m.getTaskZoneId().equals(zoneId));
        boolean alreadyEntered = taskMaterials.stream()
                .anyMatch(m -> m.getTaskZoneId().equals(zoneId)
                        && m.getInputStatus() != null
                        && !"not_entered".equals(m.getInputStatus()));

        if (alreadyEntered) {
            TaskZoneMaterial entered = taskMaterials.stream()
                    .filter(m -> m.getTaskZoneId().equals(zoneId)
                            && !"not_entered".equals(m.getInputStatus()))
                    .findFirst().orElse(null);
            Object qty = entered != null && entered.getInputQty() != null ? entered.getInputQty() : 0;
            return BarcodeScanResult.alreadyScanned(
                    material.getMaterialId(), material.getMaterialName(),
                    unit, unit, qty, category);
        }

        if (!inCurrentZone) {
            // 物料在其他分区
            String otherZoneName = "其他分区";
            return BarcodeScanResult.inOtherZone(
                    material.getMaterialId(), material.getMaterialName(),
                    unit, unit, otherZoneName, category);
        }

        // 6. 在当前分区且未录入
        return BarcodeScanResult.found(
                material.getMaterialId(), material.getMaterialName(),
                unit, unit, category);
    }

    @Override
    public void submitBarcodeSupplement(BarcodeSupplementReq req, String storeId, String openid) {
        if (!StringUtils.hasText(req.getBarcode())) {
            throw new BusinessException("条码不能为空");
        }

        BarcodeSupplement supplement = new BarcodeSupplement();
        supplement.setBarcode(req.getBarcode().trim());
        supplement.setMaterialName(req.getMaterialName());
        supplement.setStoreId(storeId);
        supplement.setSubmittedBy(openid);
        supplement.setStatus("pending");
        supplement.setRemark(req.getRemark());
        barcodeSupplementMapper.insert(supplement);
    }
}
