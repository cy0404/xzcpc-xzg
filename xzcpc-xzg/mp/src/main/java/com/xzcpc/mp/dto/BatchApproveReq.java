package com.xzcpc.mp.dto;

import lombok.Data;

import java.util.List;

/**
 * 店长批量审批报损请求
 */
@Data
public class BatchApproveReq {
    /** 报损记录 id 列表 */
    private List<Long> ids;
    /** 动作：approve=通过 | reject=拒绝 */
    private String action;
}
