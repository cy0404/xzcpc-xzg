package com.xzcpc.mp.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddZoneReq {
    @NotBlank(message = "分区名称不能为空")
    private String zoneName;
}
