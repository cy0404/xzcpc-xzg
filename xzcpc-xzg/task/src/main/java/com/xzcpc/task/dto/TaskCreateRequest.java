package com.xzcpc.task.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class TaskCreateRequest {
    private String taskName;
    private String taskMonth;
    /** 任务类型: monthly月盘(默认)|weekly周盘 */
    private String taskType;
    /** 盘点周 YYYY-Www（仅周盘传，周盘截止时间由门店配置的订货周期自动计算） */
    private String taskWeek;
    /** 订货日 1-7（仅周盘，可选）：指定本次任务对应的订货日（盘点日=订货日-1），缺省取门店配置的第一个订货日 */
    private Integer orderDay;
    private List<String> storeIds;
    private Integer templateId;
    private LocalDateTime deadline;
}
