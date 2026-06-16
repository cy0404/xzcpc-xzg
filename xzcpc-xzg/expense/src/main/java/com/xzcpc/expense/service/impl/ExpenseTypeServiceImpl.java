package com.xzcpc.expense.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xzcpc.expense.entity.ExpenseRecord;
import com.xzcpc.expense.entity.ExpenseType;
import com.xzcpc.expense.mapper.ExpenseRecordMapper;
import com.xzcpc.expense.mapper.ExpenseTypeMapper;
import com.xzcpc.expense.service.ExpenseTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExpenseTypeServiceImpl implements ExpenseTypeService {

    private static final String STATUS_ENABLED = "enabled";

    private final ExpenseTypeMapper expenseTypeMapper;
    private final ExpenseRecordMapper expenseRecordMapper;

    @Override
    public List<ExpenseType> list(String status) {
        LambdaQueryWrapper<ExpenseType> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            wrapper.eq(ExpenseType::getStatus, normalizeStatus(status));
        }
        wrapper.orderByDesc(ExpenseType::getUpdatedAt);
        List<ExpenseType> types = expenseTypeMapper.selectList(wrapper);
        if (types.isEmpty()) return types;

        List<String> typeIds = types.stream().map(ExpenseType::getTypeId).collect(Collectors.toList());
        Map<String, Long> usageMap = expenseRecordMapper.selectList(
                        new LambdaQueryWrapper<ExpenseRecord>().in(ExpenseRecord::getTypeId, typeIds))
                .stream()
                .collect(Collectors.groupingBy(ExpenseRecord::getTypeId, Collectors.counting()));
        types.forEach(type -> type.setUsageCount(usageMap.getOrDefault(type.getTypeId(), 0L)));
        return types;
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status) || "启用".equals(status)) return STATUS_ENABLED;
        if ("停用".equals(status)) return "disabled";
        return status;
    }
}
