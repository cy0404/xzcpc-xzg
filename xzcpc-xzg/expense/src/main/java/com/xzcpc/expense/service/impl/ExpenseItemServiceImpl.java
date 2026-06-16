package com.xzcpc.expense.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xzcpc.expense.entity.ExpenseItem;
import com.xzcpc.expense.entity.ExpenseType;
import com.xzcpc.expense.mapper.ExpenseItemMapper;
import com.xzcpc.expense.mapper.ExpenseTypeMapper;
import com.xzcpc.expense.service.ExpenseItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExpenseItemServiceImpl implements ExpenseItemService {

    private static final String STATUS_ENABLED = "enabled";

    private final ExpenseItemMapper itemMapper;
    private final ExpenseTypeMapper typeMapper;

    @Override
    public List<ExpenseItem> listByTypeId(String typeId) {
        var items = itemMapper.selectList(new LambdaQueryWrapper<ExpenseItem>()
                .eq(ExpenseItem::getTypeId, typeId)
                .orderByAsc(ExpenseItem::getItemId));
        fillTypeNames(items);
        return items;
    }

    @Override
    public List<ExpenseItem> listAll() {
        var items = itemMapper.selectList(new LambdaQueryWrapper<ExpenseItem>()
                .orderByAsc(ExpenseItem::getTypeId)
                .orderByAsc(ExpenseItem::getItemId));
        fillTypeNames(items);
        return items;
    }

    @Override
    public List<ExpenseItem> listEnabled() {
        var items = itemMapper.selectList(new LambdaQueryWrapper<ExpenseItem>()
                .eq(ExpenseItem::getStatus, STATUS_ENABLED)
                .orderByAsc(ExpenseItem::getTypeId)
                .orderByAsc(ExpenseItem::getItemId));
        fillTypeNames(items);
        return items;
    }

    private void fillTypeNames(List<ExpenseItem> items) {
        if (items.isEmpty()) return;
        var typeIds = items.stream().map(ExpenseItem::getTypeId).distinct().toList();
        var typeMap = typeMapper.selectList(new LambdaQueryWrapper<ExpenseType>()
                        .in(ExpenseType::getTypeId, typeIds))
                .stream()
                .collect(Collectors.toMap(ExpenseType::getTypeId, ExpenseType::getName));
        items.forEach(i -> i.setTypeName(typeMap.getOrDefault(i.getTypeId(), i.getTypeId())));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExpenseItem createItem(String typeId, String name, String description, String status) {
        if (!StringUtils.hasText(typeId) || !StringUtils.hasText(name)) {
            throw new RuntimeException("类型和项目名称不能为空");
        }
        if (typeMapper.selectOne(new LambdaQueryWrapper<ExpenseType>()
                .eq(ExpenseType::getTypeId, typeId)) == null) {
            throw new RuntimeException("一级类型不存在");
        }
        ExpenseItem item = new ExpenseItem();
        item.setItemId("EI" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        item.setTypeId(typeId);
        item.setName(name.trim());
        item.setDescription(description != null ? description.trim() : "");
        item.setStatus(StringUtils.hasText(status) ? status : STATUS_ENABLED);
        itemMapper.insert(item);
        return item;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExpenseItem updateItem(String itemId, String typeId, String name, String description, String status) {
        ExpenseItem item = itemMapper.selectOne(new LambdaQueryWrapper<ExpenseItem>()
                .eq(ExpenseItem::getItemId, itemId));
        if (item == null) throw new RuntimeException("项目不存在");
        if (StringUtils.hasText(typeId)) item.setTypeId(typeId);
        if (StringUtils.hasText(name)) item.setName(name.trim());
        item.setDescription(description != null ? description.trim() : "");
        if (StringUtils.hasText(status)) item.setStatus(status);
        itemMapper.updateById(item);
        return item;
    }

    @Override
    public void deleteItem(String itemId) {
        ExpenseItem item = itemMapper.selectOne(new LambdaQueryWrapper<ExpenseItem>()
                .eq(ExpenseItem::getItemId, itemId));
        if (item != null) itemMapper.deleteById(item.getId());
    }

    @Override
    public void deleteByTypeId(String typeId) {
        itemMapper.delete(new LambdaQueryWrapper<ExpenseItem>().eq(ExpenseItem::getTypeId, typeId));
    }
}
