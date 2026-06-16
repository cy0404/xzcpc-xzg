package com.xzcpc.expense.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xzcpc.expense.entity.ExpenseItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ExpenseItemMapper extends BaseMapper<ExpenseItem> {
}
