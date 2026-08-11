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
        wrapper.last("ORDER BY sort_no IS NULL, sort_no ASC, id ASC");
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

    @Override
    public ExpenseType create(String firstTypeName, String name, String description, String status) {
        if (!StringUtils.hasText(name)) {
            throw new RuntimeException("项目名称不能为空");
        }
        if (!StringUtils.hasText(firstTypeName)) {
            throw new RuntimeException("所属分类不能为空");
        }
        ExpenseType entity = new ExpenseType();
        entity.setTypeId("ET" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        entity.setFirstTypeId("FT" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        entity.setFirstTypeName(firstTypeName.trim());
        entity.setName(name.trim());
        entity.setDescription(description != null ? description.trim() : "");
        entity.setStatus(StringUtils.hasText(status) ? status : STATUS_ENABLED);
        // 新增分类排到末尾：取当前最大 sort_no + 1，无则从 1 开始
        ExpenseType last = expenseTypeMapper.selectOne(new LambdaQueryWrapper<ExpenseType>()
                .isNotNull(ExpenseType::getSortNo)
                .orderByDesc(ExpenseType::getSortNo)
                .last("LIMIT 1"));
        entity.setSortNo(last != null && last.getSortNo() != null ? last.getSortNo() + 1 : 1);
        expenseTypeMapper.insert(entity);
        return entity;
    }

    @Override
    public ExpenseType update(String typeId, String firstTypeName, String name, String description, String status) {
        ExpenseType entity = expenseTypeMapper.selectOne(new LambdaQueryWrapper<ExpenseType>()
                .eq(ExpenseType::getTypeId, typeId));
        if (entity == null) throw new RuntimeException("分类不存在");
        if (StringUtils.hasText(firstTypeName)) entity.setFirstTypeName(firstTypeName.trim());
        if (StringUtils.hasText(name)) entity.setName(name.trim());
        entity.setDescription(description != null ? description.trim() : "");
        if (StringUtils.hasText(status)) entity.setStatus(status);
        expenseTypeMapper.updateById(entity);
        return entity;
    }

    @Override
    public void delete(String typeId) {
        ExpenseType entity = expenseTypeMapper.selectOne(new LambdaQueryWrapper<ExpenseType>()
                .eq(ExpenseType::getTypeId, typeId));
        if (entity != null) {
            // 检查是否有支出记录引用
            Long count = expenseRecordMapper.selectCount(new LambdaQueryWrapper<ExpenseRecord>()
                    .eq(ExpenseRecord::getTypeId, typeId));
            if (count != null && count > 0) {
                throw new RuntimeException("该分类下有 " + count + " 条支出记录，无法删除");
            }
            expenseTypeMapper.deleteById(entity.getId());
        }
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status) || "启用".equals(status)) return STATUS_ENABLED;
        if ("停用".equals(status)) return "disabled";
        return status;
    }
}
