package com.xzcpc.mp.dto;

import lombok.Data;

@Data
public class StaffApprovalReq {
    /** approve 或 reject */
    private String action;
    private String rejectReason;
    private String role;
    private String employmentType;
}
