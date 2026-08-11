package com.xzcpc.task.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TaskUpdateRequest {
    private String taskName;
    private String taskMonth;
    private LocalDateTime deadline;
}
