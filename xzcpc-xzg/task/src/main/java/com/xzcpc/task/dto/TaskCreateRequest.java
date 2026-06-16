package com.xzcpc.task.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class TaskCreateRequest {
    private String taskName;
    private String taskMonth;
    private List<String> storeIds;
    private Integer templateId;
    private LocalDateTime deadline;
}
