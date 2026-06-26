package com.xzcpc.template.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class MaterialRuleSaveReq {

    private String baseUnit;
    private String stockUnit;
    private BigDecimal unitPrice;
    private List<UnitItem> units = new ArrayList<>();
    private List<UnitConversionItem> conversions = new ArrayList<>();
    private List<WeightConversionItem> weightConversions = new ArrayList<>();

    @Data
    public static class UnitItem {
        private String unitName;
    }

    @Data
    public static class UnitConversionItem {
        private BigDecimal fromQuantity;
        private String fromUnit;
        private BigDecimal toQuantity;
        private String toUnit;
    }

    @Data
    public static class WeightConversionItem {
        private BigDecimal weightQuantity;
        private String weightUnit;
        private BigDecimal countQuantity;
        private String countUnit;
    }
}
