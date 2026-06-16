package com.xzcpc.expense.dto;

import lombok.Data;

@Data
public class ExpenseTypeSaveReq {
    private String name;
    private String description;
    private String status;
}
