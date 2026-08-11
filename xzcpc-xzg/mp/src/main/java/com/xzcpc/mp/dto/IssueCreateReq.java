package com.xzcpc.mp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * P0 B1: 门店上报问题入参
 */
@Data
public class IssueCreateReq {

    @NotBlank(message = "请填写问题标题")
    @Size(max = 200, message = "标题过长")
    private String title;

    @NotBlank(message = "请选择问题类型")
    private String issueType;

    /** 子类型（如设备问题下的制冰机/净水器），可空 */
    private String subType;

    @NotBlank(message = "请选择紧急程度")
    private String urgency;

    @NotBlank(message = "请填写问题描述")
    @Size(max = 2000, message = "描述过长")
    private String description;
}
