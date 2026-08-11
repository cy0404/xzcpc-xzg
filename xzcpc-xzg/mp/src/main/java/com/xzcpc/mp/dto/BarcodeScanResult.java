package com.xzcpc.mp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * P0 A1: 扫码识别结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BarcodeScanResult {

    /** 是否识别成功 */
    private boolean found;

    /** 物料 ID */
    private String materialId;

    /** 物料名称 */
    private String materialName;

    /** 规格 */
    private String spec;

    /** 盘点单位 */
    private String unit;

    /** 所属分区名称（当物料在其他分区时） */
    private String inZoneName;

    /** 物料不在当前分区中 */
    private boolean notInCurrentZone;

    /** 当前分区已录入该物料 */
    private boolean alreadyEntered;

    /** 已录入数量（已录入时返回） */
    private Object enteredQty;

    /** 物料所属分类 */
    private String category;

    /** 建议操作：scan_existing 查看已有 / add_to_zone 加入当前分区 / supplement 补充条码 */
    private String suggestedAction;

    // ---- 工厂方法 ----

    public static BarcodeScanResult found(String materialId, String materialName, String spec,
                                           String unit, String category) {
        BarcodeScanResult r = new BarcodeScanResult();
        r.found = true;
        r.materialId = materialId;
        r.materialName = materialName;
        r.spec = spec;
        r.unit = unit;
        r.category = category;
        r.notInCurrentZone = false;
        r.alreadyEntered = false;
        r.suggestedAction = "add_to_zone";
        return r;
    }

    public static BarcodeScanResult notFound(String barcode) {
        BarcodeScanResult r = new BarcodeScanResult();
        r.found = false;
        r.suggestedAction = "supplement";
        return r;
    }

    public static BarcodeScanResult inOtherZone(String materialId, String materialName, String spec,
                                                 String unit, String zoneName, String category) {
        BarcodeScanResult r = found(materialId, materialName, spec, unit, category);
        r.notInCurrentZone = true;
        r.inZoneName = zoneName;
        r.suggestedAction = "add_to_zone";
        return r;
    }

    public static BarcodeScanResult alreadyScanned(String materialId, String materialName, String spec,
                                                    String unit, Object enteredQty, String category) {
        BarcodeScanResult r = found(materialId, materialName, spec, unit, category);
        r.alreadyEntered = true;
        r.enteredQty = enteredQty;
        r.suggestedAction = "scan_existing";
        return r;
    }
}
