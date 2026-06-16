package com.xzcpc.mp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 老板绑定状态响应。
 * auto_bound: 自动绑定成功，可直接进入老板首页。
 * pending: 等待总部核验。
 * approved: 人工审核通过。
 * rejected: 人工审核拒绝。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerBindStatusResp {

    /** 状态：auto_bound|pending|approved|rejected */
    private String status;

    /** 状态对应的中文描述 */
    private String message;

    /** 自动绑定的门店数量（仅 auto_bound 时有值） */
    private Integer storeCount;

    /** 自动绑定的门店名称列表（仅 auto_bound 时有值） */
    private List<String> storeNames;

    /** 提交人姓名（回显） */
    private String name;

    /** 提交人手机号（脱敏回显） */
    private String phoneMasked;

    /** 提交时间 */
    private String submitTime;

    /** 拒绝原因（仅 rejected 时有值） */
    private String rejectReason;
}
