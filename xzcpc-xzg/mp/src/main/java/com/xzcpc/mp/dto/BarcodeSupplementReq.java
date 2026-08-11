package com.xzcpc.mp.dto;

import lombok.Data;

/**
 * P0 A1: 条码补充申请请求
 */
@Data
public class BarcodeSupplementReq {

    /** 未识别的条码 */
    private String barcode;

    /** 物料名称（如用户知道） */
    private String materialName;

    /** 备注 */
    private String remark;
}
