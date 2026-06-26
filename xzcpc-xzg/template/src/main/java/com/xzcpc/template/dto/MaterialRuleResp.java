package com.xzcpc.template.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class MaterialRuleResp {

    private String materialId;
    private String materialName;
    private String qmCode;
    private String parentCategory;
    private String category;
    private String spec;
    private String ruleId;
    private String baseUnit;
    private String stockUnit;
    private BigDecimal unitPrice;
    private LocalDateTime updatedAt;
    private String ruleStatus;
    private List<UnitItem> units = new ArrayList<>();
    private List<UnitConversionItem> conversions = new ArrayList<>();
    private List<WeightConversionItem> weightConversions = new ArrayList<>();

    @Data
    public static class UnitItem {
        private String unitName;
        private Boolean baseUnit;
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
