package com.xzcpc.mp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.benmanes.caffeine.cache.Cache;
import com.xzcpc.common.exception.BusinessException;
import com.xzcpc.expense.entity.ExpenseRecord;
import com.xzcpc.expense.entity.ExpenseType;
import com.xzcpc.common.model.StoreInfo;
import com.xzcpc.expense.mapper.ExpenseRecordMapper;
import com.xzcpc.expense.mapper.ExpenseTypeMapper;
import com.xzcpc.mp.dto.MpExpenseSaveReq;
import com.xzcpc.mp.service.MpExpenseService;
import com.xzcpc.task.service.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MpExpenseServiceImpl implements MpExpenseService {

    private static final String STATUS_ENABLED = "enabled";

    private final ExpenseTypeMapper expenseTypeMapper;
    private final ExpenseRecordMapper expenseRecordMapper;
    private final StoreService storeService;
    private final Cache<String, List<ExpenseType>> typeCache;

    @Override
    public List<ExpenseType> listTypes() {
        return typeCache.get("enabled", key ->
            expenseTypeMapper.selectList(new LambdaQueryWrapper<ExpenseType>()
                    .eq(ExpenseType::getStatus, STATUS_ENABLED)
                    .orderByDesc(ExpenseType::getUpdatedAt)));
    }

    @Override
    public Page<ExpenseRecord> page(String storeId, String typeId, String startDate, String endDate,
                                    int pageNum, int pageSize) {
        requireStore(storeId);
        LambdaQueryWrapper<ExpenseRecord> wrapper = new LambdaQueryWrapper<ExpenseRecord>()
                .eq(ExpenseRecord::getStoreId, storeId);
        if (StringUtils.hasText(typeId)) {
            wrapper.eq(ExpenseRecord::getTypeId, typeId);
        }
        if (StringUtils.hasText(startDate)) {
            wrapper.ge(ExpenseRecord::getOccurredDate, LocalDate.parse(startDate));
        }
        if (StringUtils.hasText(endDate)) {
            wrapper.le(ExpenseRecord::getOccurredDate, LocalDate.parse(endDate));
        }
        wrapper.orderByDesc(ExpenseRecord::getOccurredDate).orderByDesc(ExpenseRecord::getId);
        return expenseRecordMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public ExpenseRecord detail(String storeId, String expenseId) {
        requireStore(storeId);
        ExpenseRecord record = expenseRecordMapper.selectOne(new LambdaQueryWrapper<ExpenseRecord>()
                .eq(ExpenseRecord::getStoreId, storeId)
                .eq(ExpenseRecord::getExpenseId, expenseId));
        if (record == null) {
            throw new BusinessException(404, "支出记录不存在");
        }
        return record;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExpenseRecord create(String storeId, String storeName, MpExpenseSaveReq req) {
        requireStore(storeId);
        ExpenseType type = expenseTypeMapper.selectOne(new LambdaQueryWrapper<ExpenseType>()
                .eq(ExpenseType::getTypeId, req.getTypeId())
                .eq(ExpenseType::getStatus, STATUS_ENABLED));
        if (type == null) {
            throw new BusinessException(400, "支出类型不存在或已停用");
        }

        // storeName 已从登录上下文传入，不必调外部门店 API（超时 30s+）
        ExpenseRecord record = new ExpenseRecord();
        record.setStoreId(storeId);
        record.setStoreName(StringUtils.hasText(storeName) ? storeName : "未知门店");
        // 从门店数据库获取小程序号
        StoreInfo store = storeService.getStoreById(storeId);
        record.setStoreMiniappNo(store != null ? store.getXiaochengxuid() : null);
        record.setWarehouseCode(store != null ? store.getCangkuid() : null);
        record.setTypeId(type.getTypeId());
        record.setTypeName(type.getName());
        record.setFirstTypeId(nvl(type.getFirstTypeId()));
        record.setFirstTypeName(nvl(type.getFirstTypeName()));
        record.setItemId(trimToNull(req.getItemId()));
        record.setItemName(StringUtils.hasText(req.getItemName()) ? req.getItemName() : type.getName());
        record.setAmount(req.getAmount());
        record.setOccurredDate(req.getOccurredDate());
        record.setHandlerName(req.getHandlerName().trim());
        record.setVoucherUrl(trimToNull(req.getVoucherUrl()));
        record.setRemark(trimToNull(req.getRemark()));
        // 先设临时 expense_id，insert 拿到自增 id 后再更新为正式编码
        record.setExpenseId("TMP");
        expenseRecordMapper.insert(record);
        record.setExpenseId("EX" + String.format("%08d", record.getId()));
        expenseRecordMapper.updateById(record);
        return record;
    }

    private void requireStore(String storeId) {
        if (!StringUtils.hasText(storeId)) {
            throw new BusinessException(403, "请先绑定门店");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExpenseRecord update(String storeId, String expenseId, MpExpenseSaveReq req) {
        ExpenseRecord record = detail(storeId, expenseId);
        ExpenseType type = expenseTypeMapper.selectOne(new LambdaQueryWrapper<ExpenseType>()
                .eq(ExpenseType::getTypeId, req.getTypeId())
                .eq(ExpenseType::getStatus, STATUS_ENABLED));
        if (type == null) {
            throw new BusinessException(400, "支出类型不存在或已停用");
        }
        record.setTypeId(type.getTypeId());
        record.setTypeName(type.getName());
        record.setFirstTypeId(nvl(type.getFirstTypeId()));
        record.setFirstTypeName(nvl(type.getFirstTypeName()));
        record.setItemId(trimToNull(req.getItemId()));
        record.setItemName(StringUtils.hasText(req.getItemName()) ? req.getItemName() : type.getName());
        record.setAmount(req.getAmount());
        record.setOccurredDate(req.getOccurredDate());
        record.setHandlerName(req.getHandlerName().trim());
        record.setVoucherUrl(trimToNull(req.getVoucherUrl()));
        record.setRemark(trimToNull(req.getRemark()));
        expenseRecordMapper.updateById(record);
        return record;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String storeId, String expenseId) {
        ExpenseRecord record = detail(storeId, expenseId);
        expenseRecordMapper.deleteById(record.getId());
    }

    private String nvl(String v) { return v != null ? v : ""; }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
