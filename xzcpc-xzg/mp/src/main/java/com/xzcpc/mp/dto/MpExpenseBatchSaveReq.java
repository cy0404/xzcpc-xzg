package com.xzcpc.mp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 多类型组批量登记：一个表单一次提交多个支出类型组。
 * 整单共享：日期、经手人、说明、凭证（最多9张）；每条记录由 service 覆盖整单字段。
 */
@Data
public class MpExpenseBatchSaveReq {

    @NotNull(message = "支出日期不能为空")
    private LocalDate occurredDate;

    @NotBlank(message = "经手人不能为空")
    private String handlerName;

    /** 整单凭证（最多9张） */
    private List<String> voucherUrls;

    private String remark;

    @NotEmpty(message = "至少登记一个支出类型")
    private List<MpExpenseSaveReq> records;
}
