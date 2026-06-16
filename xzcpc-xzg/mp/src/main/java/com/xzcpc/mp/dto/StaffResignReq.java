package com.xzcpc.mp.dto;

import lombok.Data;

@Data
public class StaffResignReq {
    private String resignDate;
    private String resignReason;
    private String remark;
}
