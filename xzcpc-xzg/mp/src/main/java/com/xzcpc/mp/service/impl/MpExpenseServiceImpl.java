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
import com.xzcpc.expense.entity.SelfPurchaseMaterial;
import com.xzcpc.expense.mapper.SelfPurchaseMaterialMapper;
import com.xzcpc.mp.service.MpExpenseService;
import com.xzcpc.task.service.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MpExpenseServiceImpl implements MpExpenseService {

    private static final String STATUS_ENABLED = "enabled";
    private static final java.time.format.DateTimeFormatter DTF = java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private static final String SELF_PURCHASE_TYPE = "自购食材";

    private final ExpenseTypeMapper expenseTypeMapper;
    private final ExpenseRecordMapper expenseRecordMapper;
    private final StoreService storeService;
    private final Cache<String, List<ExpenseType>> typeCache;
    private final SelfPurchaseMaterialMapper spmMapper;

    @Override
    public List<ExpenseType> listTypes() {
        return typeCache.get("enabled", key ->
            expenseTypeMapper.selectList(new LambdaQueryWrapper<ExpenseType>()
                    .eq(ExpenseType::getStatus, STATUS_ENABLED)
                    .last("ORDER BY sort_no IS NULL, sort_no ASC, id ASC")));
    }

    @Override
    public Page<ExpenseRecord> page(String storeId, String typeId, String startDate, String endDate,
                                    int pageNum, int pageSize, String handlerName) {
        requireStore(storeId);
        List<ExpenseRecord> all = new ArrayList<>();

        // 1. 查 expense_record（非自购食材）
        boolean includeSelfPurchase = !StringUtils.hasText(typeId) || isSelfPurchaseTypeId(typeId);
        if (!isSelfPurchaseTypeId(typeId)) {
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
            if (StringUtils.hasText(handlerName)) {
                wrapper.eq(ExpenseRecord::getHandlerName, handlerName);
            }
            wrapper.orderByDesc(ExpenseRecord::getOccurredDate).orderByDesc(ExpenseRecord::getId);
            all.addAll(expenseRecordMapper.selectList(wrapper));
        } else {
            // 查全部 expense_record（排除可能混入的自购食材）
            LambdaQueryWrapper<ExpenseRecord> wrapper = new LambdaQueryWrapper<ExpenseRecord>()
                    .eq(ExpenseRecord::getStoreId, storeId)
                    .ne(ExpenseRecord::getTypeName, SELF_PURCHASE_TYPE);
            if (StringUtils.hasText(typeId)) {
                wrapper.eq(ExpenseRecord::getTypeId, typeId);
            }
            if (StringUtils.hasText(startDate)) {
                wrapper.ge(ExpenseRecord::getOccurredDate, LocalDate.parse(startDate));
            }
            if (StringUtils.hasText(endDate)) {
                wrapper.le(ExpenseRecord::getOccurredDate, LocalDate.parse(endDate));
            }
            if (StringUtils.hasText(handlerName)) {
                wrapper.eq(ExpenseRecord::getHandlerName, handlerName);
            }
            wrapper.orderByDesc(ExpenseRecord::getOccurredDate).orderByDesc(ExpenseRecord::getId);
            all.addAll(expenseRecordMapper.selectList(wrapper));
        }

        // 2. 查 self_purchase_material 并转为 ExpenseRecord
        if (includeSelfPurchase) {
            LambdaQueryWrapper<SelfPurchaseMaterial> spmWrapper = new LambdaQueryWrapper<SelfPurchaseMaterial>()
                    .eq(SelfPurchaseMaterial::getStoreId, storeId);
            if (StringUtils.hasText(startDate) && StringUtils.hasText(endDate)) {
                spmWrapper.between(SelfPurchaseMaterial::getPurchaseDate, LocalDate.parse(startDate), LocalDate.parse(endDate));
            } else if (StringUtils.hasText(startDate)) {
                spmWrapper.ge(SelfPurchaseMaterial::getPurchaseDate, LocalDate.parse(startDate));
            } else if (StringUtils.hasText(endDate)) {
                spmWrapper.le(SelfPurchaseMaterial::getPurchaseDate, LocalDate.parse(endDate));
            }
            if (StringUtils.hasText(handlerName)) {
                spmWrapper.eq(SelfPurchaseMaterial::getHandlerName, handlerName);
            }
            spmWrapper.orderByDesc(SelfPurchaseMaterial::getPurchaseDate).orderByDesc(SelfPurchaseMaterial::getId);
            List<SelfPurchaseMaterial> spmList = spmMapper.selectList(spmWrapper);
            for (SelfPurchaseMaterial spm : spmList) {
                ExpenseRecord r = new ExpenseRecord();
                r.setExpenseId(spm.getBizCode());
                r.setStoreId(spm.getStoreId());
                r.setStoreName(spm.getStoreName());
                r.setStoreMiniappNo(spm.getStoreMiniappNo());
                r.setTypeId(getSelfPurchaseTypeId());
                r.setTypeName(SELF_PURCHASE_TYPE);
                r.setAmount(spm.getTotalAmount() != null ? spm.getTotalAmount() : BigDecimal.ZERO);
                r.setOccurredDate(spm.getPurchaseDate());
                r.setHandlerName(spm.getHandlerName());
                r.setVoucherUrl(spm.getVoucherUrl());
                r.setRemark(spm.getRemark());
                if (spm.getCreatedAt() != null) r.setCreatedAt(spm.getCreatedAt());
                all.add(r);
            }
        }

        // 3. 排序 + 手动分页
        all.sort((a, b) -> {
            int dateCmp = Comparator.nullsLast(LocalDate::compareTo).compare(b.getOccurredDate(), a.getOccurredDate());
            if (dateCmp != 0) return dateCmp;
            return b.getExpenseId().compareTo(a.getExpenseId());
        });
        int total = all.size();
        int from = Math.min((pageNum - 1) * pageSize, total);
        int to = Math.min(from + pageSize, total);
        List<ExpenseRecord> paged = all.subList(from, to);

        Page<ExpenseRecord> result = new Page<>(pageNum, pageSize, total);
        result.setRecords(paged);
        return result;
    }

    private boolean isSelfPurchaseTypeId(String typeId) {
        if (!StringUtils.hasText(typeId)) return false;
        ExpenseType type = expenseTypeMapper.selectOne(new LambdaQueryWrapper<ExpenseType>()
                .eq(ExpenseType::getTypeId, typeId));
        return type != null && SELF_PURCHASE_TYPE.equals(type.getName());
    }

    private String getSelfPurchaseTypeId() {
        ExpenseType type = expenseTypeMapper.selectOne(new LambdaQueryWrapper<ExpenseType>()
                .eq(ExpenseType::getName, SELF_PURCHASE_TYPE)
                .eq(ExpenseType::getStatus, STATUS_ENABLED));
        return type != null ? type.getTypeId() : "";
    }

    @Override
    public ExpenseRecord detail(String storeId, String expenseId, String handlerName) {
        requireStore(storeId);
        ExpenseRecord record = expenseRecordMapper.selectOne(new LambdaQueryWrapper<ExpenseRecord>()
                .eq(ExpenseRecord::getStoreId, storeId)
                .eq(ExpenseRecord::getExpenseId, expenseId));
        if (record != null) {
            if (StringUtils.hasText(handlerName) && !handlerName.equals(record.getHandlerName())) {
                throw new BusinessException(403, "只能查看自己登记的支出");
            }
            return record;
        }

        // 尝试从 self_purchase_material 查找
        SelfPurchaseMaterial spm = spmMapper.selectOne(new LambdaQueryWrapper<SelfPurchaseMaterial>()
                .eq(SelfPurchaseMaterial::getBizCode, expenseId)
                .eq(SelfPurchaseMaterial::getStoreId, storeId));
        if (spm != null) {
            if (StringUtils.hasText(handlerName) && !handlerName.equals(spm.getHandlerName())) {
                throw new BusinessException(403, "只能查看自己登记的支出");
            }
            ExpenseRecord r = new ExpenseRecord();
            r.setExpenseId(spm.getBizCode());
            r.setStoreId(spm.getStoreId());
            r.setStoreName(spm.getStoreName());
            r.setStoreMiniappNo(spm.getStoreMiniappNo());
            r.setTypeId(getSelfPurchaseTypeId());
            r.setTypeName(SELF_PURCHASE_TYPE);
            r.setAmount(spm.getTotalAmount() != null ? spm.getTotalAmount() : BigDecimal.ZERO);
            r.setOccurredDate(spm.getPurchaseDate());
            r.setHandlerName(spm.getHandlerName());
            r.setVoucherUrl(spm.getVoucherUrl());
            r.setRemark(spm.getRemark());
            if (spm.getCreatedAt() != null) r.setCreatedAt(spm.getCreatedAt());
            return r;
        }
        throw new BusinessException(404, "支出记录不存在");
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

        StoreInfo store = storeService.getStoreById(storeId);
        String miniappNo = store != null ? store.getXiaochengxuid() : null;

        // 自购食材只存 self_purchase_material，不存 expense_record
        if (SELF_PURCHASE_TYPE.equals(type.getName())) {
            return createSelfPurchaseOnly(storeId, storeName, miniappNo, type, req);
        }

        ExpenseRecord record = new ExpenseRecord();
        record.setStoreId(storeId);
        record.setStoreName(StringUtils.hasText(storeName) ? storeName : "未知门店");
        record.setStoreMiniappNo(miniappNo);
        record.setWarehouseCode(store != null ? store.getCangkuid() : null);
        record.setTypeId(type.getTypeId());
        record.setTypeName(type.getName());
        record.setFirstTypeId(nvl(type.getFirstTypeId()));
        record.setFirstTypeName(nvl(type.getFirstTypeName()));
        record.setAmount(req.getAmount());
        record.setOccurredDate(req.getOccurredDate());
        record.setHandlerName(req.getHandlerName().trim());
        record.setVoucherUrl(trimToNull(req.getVoucherUrl()));
        record.setRemark(trimToNull(req.getRemark()));
        record.setExpenseId("TMP_" + System.nanoTime());
        expenseRecordMapper.insert(record);
        record.setExpenseId("EXP" + LocalDateTime.now().format(DTF) + String.format("%03d", record.getId() % 1000));
        expenseRecordMapper.updateById(record);
        return record;
    }

    private ExpenseRecord createSelfPurchaseOnly(String storeId, String storeName, String miniappNo,
                                                   ExpenseType type, MpExpenseSaveReq req) {
        SelfPurchaseMaterial spm = new SelfPurchaseMaterial();
        String spmId = "SPM" + LocalDateTime.now().format(DTF) + String.format("%03d", (int)(Math.random() * 1000));
        spm.setBizCode(spmId);
        spm.setStoreId(storeId);
        spm.setStoreName(StringUtils.hasText(storeName) ? storeName : "未知门店");
        spm.setStoreMiniappNo(miniappNo);
        spm.setMaterialId(trimToNull(req.getMaterialId()));
        spm.setMaterialName(StringUtils.hasText(req.getMaterialName()) ? req.getMaterialName().trim() : "其他");
        spm.setParentCategory(trimToNull(req.getParentCategory()));
        spm.setCategory(trimToNull(req.getCategory()));
        spm.setUnit("kg");
        spm.setPurchaseMonth(req.getOccurredDate() != null
                ? req.getOccurredDate().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"))
                : LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM")));
        spm.setPurchaseDate(req.getOccurredDate());
        spm.setPurchaseQty(req.getWeight() != null ? req.getWeight() : BigDecimal.ZERO);
        spm.setUnitPrice(req.getUnitPrice());
        if (req.getUnitPrice() != null && req.getWeight() != null && req.getWeight().compareTo(BigDecimal.ZERO) > 0) {
            spm.setTotalAmount(req.getUnitPrice().multiply(req.getWeight()).setScale(2, RoundingMode.HALF_UP));
        }
        spm.setHandlerName(StringUtils.hasText(req.getHandlerName()) ? req.getHandlerName().trim() : null);
        spm.setVoucherUrl(trimToNull(req.getVoucherUrl()));
        spm.setRemark(trimToNull(req.getRemark()));
        spmMapper.insert(spm);

        // 返回一个虚拟 ExpenseRecord 供前端展示
        ExpenseRecord record = new ExpenseRecord();
        record.setExpenseId(spmId);
        record.setStoreId(storeId);
        record.setStoreName(storeName);
        record.setTypeId(type.getTypeId());
        record.setTypeName(type.getName());
        record.setAmount(spm.getTotalAmount() != null ? spm.getTotalAmount() : BigDecimal.ZERO);
        record.setOccurredDate(req.getOccurredDate());
        record.setHandlerName(req.getHandlerName());
        record.setRemark(req.getRemark());
        return record;
    }

    private void requireStore(String storeId) {
        if (!StringUtils.hasText(storeId)) {
            throw new BusinessException(403, "请先绑定门店");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExpenseRecord update(String storeId, String expenseId, MpExpenseSaveReq req, String handlerName) {
        ExpenseType type = expenseTypeMapper.selectOne(new LambdaQueryWrapper<ExpenseType>()
                .eq(ExpenseType::getTypeId, req.getTypeId())
                .eq(ExpenseType::getStatus, STATUS_ENABLED));
        if (type == null) {
            throw new BusinessException(400, "支出类型不存在或已停用");
        }

        // 自购食材：直接从 self_purchase_material 更新
        if (SELF_PURCHASE_TYPE.equals(type.getName())) {
            SelfPurchaseMaterial spm = spmMapper.selectOne(new LambdaQueryWrapper<SelfPurchaseMaterial>()
                    .eq(SelfPurchaseMaterial::getBizCode, expenseId)
                    .eq(SelfPurchaseMaterial::getStoreId, storeId));
            if (spm == null) {
                throw new BusinessException(404, "记录不存在");
            }
            spm.setMaterialId(trimToNull(req.getMaterialId()));
            spm.setMaterialName(StringUtils.hasText(req.getMaterialName()) ? req.getMaterialName().trim() : "其他");
            spm.setParentCategory(trimToNull(req.getParentCategory()));
            spm.setCategory(trimToNull(req.getCategory()));
            spm.setPurchaseDate(req.getOccurredDate());
            spm.setPurchaseMonth(req.getOccurredDate() != null
                    ? req.getOccurredDate().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"))
                    : LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM")));
            BigDecimal qty = req.getWeight() != null ? req.getWeight() : BigDecimal.ZERO;
            spm.setPurchaseQty(qty);
            spm.setUnitPrice(req.getUnitPrice());
            if (req.getUnitPrice() != null && qty.compareTo(BigDecimal.ZERO) > 0) {
                spm.setTotalAmount(req.getUnitPrice().multiply(qty).setScale(2, RoundingMode.HALF_UP));
            }
            spm.setHandlerName(req.getHandlerName().trim());
            spm.setVoucherUrl(trimToNull(req.getVoucherUrl()));
            spm.setRemark(trimToNull(req.getRemark()));
            spmMapper.updateById(spm);

            ExpenseRecord record = new ExpenseRecord();
            record.setExpenseId(expenseId);
            record.setTypeId(type.getTypeId());
            record.setTypeName(type.getName());
            record.setAmount(spm.getTotalAmount() != null ? spm.getTotalAmount() : BigDecimal.ZERO);
            record.setOccurredDate(req.getOccurredDate());
            record.setHandlerName(req.getHandlerName());
            record.setRemark(req.getRemark());
            return record;
        }

        ExpenseRecord record = detail(storeId, expenseId, handlerName);
        record.setTypeId(type.getTypeId());
        record.setTypeName(type.getName());
        record.setFirstTypeId(nvl(type.getFirstTypeId()));
        record.setFirstTypeName(nvl(type.getFirstTypeName()));
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
    public void delete(String storeId, String expenseId, String handlerName) {
        // 先尝试 expense_record
        ExpenseRecord record = expenseRecordMapper.selectOne(new LambdaQueryWrapper<ExpenseRecord>()
                .eq(ExpenseRecord::getStoreId, storeId)
                .eq(ExpenseRecord::getExpenseId, expenseId));
        if (record != null) {
            if (StringUtils.hasText(handlerName) && !handlerName.equals(record.getHandlerName())) {
                throw new BusinessException(403, "只能删除自己登记的支出");
            }
            expenseRecordMapper.deleteById(record.getId());
            return;
        }
        // 再尝试 self_purchase_material
        SelfPurchaseMaterial spm = spmMapper.selectOne(new LambdaQueryWrapper<SelfPurchaseMaterial>()
                .eq(SelfPurchaseMaterial::getBizCode, expenseId)
                .eq(SelfPurchaseMaterial::getStoreId, storeId));
        if (spm != null) {
            if (StringUtils.hasText(handlerName) && !handlerName.equals(spm.getHandlerName())) {
                throw new BusinessException(403, "只能删除自己登记的支出");
            }
            spmMapper.deleteById(spm.getId());
            return;
        }
        throw new BusinessException(404, "记录不存在");
    }

    private String nvl(String v) { return v != null ? v : ""; }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

}
