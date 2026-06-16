package com.xzcpc.mp.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BindStoreReq {
    @NotBlank(message = "门店ID不能为空")
    private String storeId;
}
