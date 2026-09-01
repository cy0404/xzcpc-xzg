package com.xzcpc.mp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.benmanes.caffeine.cache.Cache;
import com.xzcpc.common.exception.BusinessException;
import com.xzcpc.expense.entity.ExpenseRecord;
import com.xzcpc.expense.entity.ExpenseRecordItem;
import com.xzcpc.expense.entity.ExpenseType;
import com.xzcpc.common.model.StoreInfo;
import com.xzcpc.expense.mapper.ExpenseRecordItemMapper;
import com.xzcpc.expense.mapper.ExpenseRecordMapper;
import com.xzcpc.expense.mapper.ExpenseTypeMapper;
import com.xzcpc.mp.dto.ExpenseItemVO;
import com.xzcpc.mp.dto.MpExpenseBatchSaveReq;
import com.xzcpc.mp.dto.MpExpenseSaveReq;
import com.xzcpc.expense.entity.SelfPurchaseMaterial;
import com.xzcpc.expense.entity.SelfPurchaseMaterialItem;
import com.xzcpc.expense.mapper.SelfPurchaseMaterialMapper;
import com.xzcpc.expense.mapper.SelfPurchaseMaterialItemMapper;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MpExpenseServiceImpl implements MpExpenseService {

    private static final String STATUS_ENABLED = "enabled";
    private static final java.time.format.DateTimeFormatter DTF = java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private static final String SELF_PURCHASE_TYPE = "自购食材";
    private static final int SELF_PURCHASE_MAX_ITEMS = 10;
    private static final int MAX_VOUCHER_SIZE = 9;

    private final ExpenseTypeMapper expenseTypeMapper;
    private final ExpenseRecordMapper expenseRecordMapper;
    private final ExpenseRecordItemMapper expenseRecordItemMapper;
    private final StoreService storeService;
    private final Cache<String, List<ExpenseType>> typeCache;
    private final SelfPurchaseMaterialMapper spmMapper;
    private final SelfPurchaseMaterialItemMapper spmItemMapper;

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

        // 明细概要：批量查两类明细，填充 firstItemName/itemCount（消除前端逐笔查询的 N+1）
        if (!paged.isEmpty()) {
            Map<String, List<ExpenseRecordItem>> itemsByExpense = batchLoadRecordItems(paged);
            Map<String, List<SelfPurchaseMaterialItem>> spmItemsByBiz = batchLoadSpmItems(paged);
            for (ExpenseRecord r : paged) {
                List<ExpenseRecordItem> its = r.getId() != null
                        ? itemsByExpense.getOrDefault(r.getExpenseId(), Collections.emptyList())
                        : Collections.emptyList();
                List<SelfPurchaseMaterialItem> sIts = r.getId() == null
                        ? spmItemsByBiz.getOrDefault(r.getExpenseId(), Collections.emptyList())
                        : Collections.emptyList();
                if (!its.isEmpty()) {
                    r.setFirstItemName(its.get(0).getItemName());
                    r.setItemCount(its.size());
                } else if (!sIts.isEmpty()) {
                    r.setFirstItemName(sIts.get(0).getMaterialName());
                    r.setItemCount(sIts.size());
                }
            }
        }

        Page<ExpenseRecord> result = new Page<>(pageNum, pageSize, total);
        result.setRecords(paged);
        return result;
    }

    /** 批量查 expense_record 的明细（分页记录中主键 id 非空 = expense_record 行） */
    private Map<String, List<ExpenseRecordItem>> batchLoadRecordItems(List<ExpenseRecord> records) {
        List<String> ids = records.stream()
                .filter(r -> r.getId() != null)
                .map(ExpenseRecord::getExpenseId)
                .filter(StringUtils::hasText)
                .distinct().collect(Collectors.toList());
        if (ids.isEmpty()) return Collections.emptyMap();
        return expenseRecordItemMapper.selectList(new LambdaQueryWrapper<ExpenseRecordItem>()
                        .in(ExpenseRecordItem::getExpenseId, ids)
                        .orderByAsc(ExpenseRecordItem::getSortNo)
                        .orderByAsc(ExpenseRecordItem::getId))
                .stream().collect(Collectors.groupingBy(ExpenseRecordItem::getExpenseId, LinkedHashMap::new, Collectors.toList()));
    }

    /** 批量查 self_purchase_material 的明细（分页记录中主键 id 为空 = spm 虚拟记录） */
    private Map<String, List<SelfPurchaseMaterialItem>> batchLoadSpmItems(List<ExpenseRecord> records) {
        List<String> ids = records.stream()
                .filter(r -> r.getId() == null)
                .map(ExpenseRecord::getExpenseId)
                .filter(StringUtils::hasText)
                .distinct().collect(Collectors.toList());
        if (ids.isEmpty()) return Collections.emptyMap();
        return spmItemMapper.selectList(new LambdaQueryWrapper<SelfPurchaseMaterialItem>()
                        .in(SelfPurchaseMaterialItem::getBizCode, ids)
                        .orderByAsc(SelfPurchaseMaterialItem::getSortNo)
                        .orderByAsc(SelfPurchaseMaterialItem::getId))
                .stream().collect(Collectors.groupingBy(SelfPurchaseMaterialItem::getBizCode, LinkedHashMap::new, Collectors.toList()));
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
            List<String> vurls = splitVoucherUrls(record.getVoucherUrl());
            record.setVoucherUrls(vurls);
            record.setVoucherUrl(vurls.isEmpty() ? null : vurls.get(0));
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
            List<String> vurls = splitVoucherUrls(spm.getVoucherUrl());
            r.setVoucherUrls(vurls);
            r.setVoucherUrl(vurls.isEmpty() ? null : vurls.get(0));
            return r;
        }
        throw new BusinessException(404, "支出记录不存在");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExpenseRecord create(String storeId, String storeName, MpExpenseSaveReq req) {
        requireStore(storeId);
        ExpenseType type = findEnabledType(req.getTypeId());

        StoreInfo store = storeService.getStoreById(storeId);
        String miniappNo = store != null ? store.getXiaochengxuid() : null;

        // 自购食材只存 self_purchase_material，不存 expense_record
        if (SELF_PURCHASE_TYPE.equals(type.getName())) {
            return createSelfPurchaseOnly(storeId, storeName, miniappNo, type, req, null);
        }
        return createExpenseRecordOnly(storeId, storeName, store, type, req, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ExpenseRecord> createBatch(String storeId, String storeName, MpExpenseBatchSaveReq req) {
        requireStore(storeId);
        if (req.getRecords().size() > 10) {
            throw new BusinessException(400, "一次最多登记10个支出类型");
        }
        List<ExpenseRecord> results = new ArrayList<>();
        for (MpExpenseSaveReq r : req.getRecords()) {
            r.setOccurredDate(req.getOccurredDate());
            r.setHandlerName(req.getHandlerName());
            // 记录级说明优先（H5 按类型分组填写），未填时回退整单说明（小程序旧版整单模式）
            r.setRemark(StringUtils.hasText(r.getRemark()) ? r.getRemark() : req.getRemark());
            r.setVoucherUrl(null);
            r.setVoucherUrls(req.getVoucherUrls());
            results.add(create(storeId, storeName, r));
        }
        return results;
    }

    /** 非自购类型：expense_record 一单一行；有明细时 amount = Σ 明细，明细写 expense_record_item */
    private ExpenseRecord createExpenseRecordOnly(String storeId, String storeName, StoreInfo store,
                                                   ExpenseType type, MpExpenseSaveReq req, String fixedExpenseId) {
        List<MpExpenseSaveReq.AmountItem> itemList = resolveAmountItems(req);
        if (itemList != null) {
            validateAmountItems(itemList);
        }
        String miniappNo = store != null ? store.getXiaochengxuid() : null;

        ExpenseRecord record = new ExpenseRecord();
        record.setStoreId(storeId);
        record.setStoreName(StringUtils.hasText(storeName) ? storeName : "未知门店");
        record.setStoreMiniappNo(miniappNo);
        record.setWarehouseCode(store != null ? store.getCangkuid() : null);
        record.setTypeId(type.getTypeId());
        record.setTypeName(type.getName());
        record.setFirstTypeId(nvl(type.getFirstTypeId()));
        record.setFirstTypeName(nvl(type.getFirstTypeName()));
        List<String> vouchers = resolveVoucherUrls(req);
        record.setAmount(itemList != null ? calcPlainTotal(itemList) : req.getAmount());
        record.setOccurredDate(req.getOccurredDate());
        record.setHandlerName(req.getHandlerName().trim());
        record.setVoucherUrl(joinVouchers(vouchers));
        record.setRemark(trimToNull(req.getRemark()));
        record.setExpenseId(StringUtils.hasText(fixedExpenseId) ? fixedExpenseId : "TMP_" + System.nanoTime());
        expenseRecordMapper.insert(record);
        if (!StringUtils.hasText(fixedExpenseId)) {
            record.setExpenseId("EXP" + LocalDateTime.now().format(DTF) + String.format("%03d", record.getId() % 1000));
            expenseRecordMapper.updateById(record);
        }
        if (itemList != null) {
            insertAmountItems(record.getExpenseId(), itemList);
        }
        return record;
    }

    private ExpenseRecord createSelfPurchaseOnly(String storeId, String storeName, String miniappNo,
                                                   ExpenseType type, MpExpenseSaveReq req, String fixedBizCode) {
        List<MpExpenseSaveReq.ItemReq> itemList = resolveItems(req);
        validateItems(itemList);

        SelfPurchaseMaterial spm = new SelfPurchaseMaterial();
        // 先插 TMP_ 临时号拿自增主键，再回填正式业务号（"SPM"+分钟+自增id，数据库分配单调递增，杜绝并发撞号）
        boolean fixedBiz = StringUtils.hasText(fixedBizCode);
        spm.setBizCode(fixedBiz ? fixedBizCode : "TMP_" + System.nanoTime());
        spm.setStoreId(storeId);
        spm.setStoreName(StringUtils.hasText(storeName) ? storeName : "未知门店");
        spm.setStoreMiniappNo(miniappNo);
        spm.setPurchaseMonth(req.getOccurredDate() != null
                ? req.getOccurredDate().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"))
                : LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM")));
        spm.setPurchaseDate(req.getOccurredDate());
        List<String> vouchers = resolveVoucherUrls(req);
        spm.setTotalAmount(calcTotalAmount(itemList));
        spm.setHandlerName(StringUtils.hasText(req.getHandlerName()) ? req.getHandlerName().trim() : null);
        spm.setVoucherUrl(joinVouchers(vouchers));
        spm.setRemark(trimToNull(req.getRemark()));
        spmMapper.insert(spm);
        String spmId;
        if (fixedBiz) {
            spmId = fixedBizCode;
        } else {
            spmId = "SPM" + LocalDateTime.now().format(DTF) + spm.getId();
            spm.setBizCode(spmId);
            spmMapper.updateById(spm);
        }
        insertItems(spmId, itemList);

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

    /** 多物料入参解析：优先 items，兼容旧的扁平单物料字段（包装成单条） */
    private List<MpExpenseSaveReq.ItemReq> resolveItems(MpExpenseSaveReq req) {
        if (req.getItems() != null && !req.getItems().isEmpty()) {
            return req.getItems();
        }
        List<MpExpenseSaveReq.ItemReq> list = new ArrayList<>();
        MpExpenseSaveReq.ItemReq single = new MpExpenseSaveReq.ItemReq();
        single.setMaterialId(req.getMaterialId());
        single.setMaterialName(req.getMaterialName());
        single.setParentCategory(req.getParentCategory());
        single.setCategory(req.getCategory());
        single.setWeight(req.getWeight());
        single.setUnitPrice(req.getUnitPrice());
        list.add(single);
        return list;
    }

    /** 校验自购食材明细：最多 10 条，每条物料名/重量/单价必填且大于 0 */
    private void validateItems(List<MpExpenseSaveReq.ItemReq> itemList) {
        if (itemList.size() > SELF_PURCHASE_MAX_ITEMS) {
            throw new BusinessException(400, "最多添加" + SELF_PURCHASE_MAX_ITEMS + "种物料");
        }
        for (MpExpenseSaveReq.ItemReq item : itemList) {
            if (!StringUtils.hasText(item.getMaterialName())) {
                throw new BusinessException(400, "请选择物料");
            }
            if (item.getWeight() == null || item.getWeight().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(400, "物料[" + item.getMaterialName() + "]请填写重量");
            }
            if (item.getUnitPrice() == null || item.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(400, "物料[" + item.getMaterialName() + "]请填写单价");
            }
        }
    }

    private BigDecimal calcTotalAmount(List<MpExpenseSaveReq.ItemReq> itemList) {
        BigDecimal total = BigDecimal.ZERO;
        for (MpExpenseSaveReq.ItemReq item : itemList) {
            BigDecimal amt = item.getWeight().multiply(item.getUnitPrice()).setScale(2, RoundingMode.HALF_UP);
            total = total.add(amt);
        }
        return total;
    }

    private void insertItems(String bizCode, List<MpExpenseSaveReq.ItemReq> itemList) {
        int sortNo = 0;
        for (MpExpenseSaveReq.ItemReq item : itemList) {
            SelfPurchaseMaterialItem spmi = new SelfPurchaseMaterialItem();
            spmi.setBizCode(bizCode);
            spmi.setMaterialId(trimToNull(item.getMaterialId()));
            spmi.setMaterialName(item.getMaterialName().trim());
            spmi.setParentCategory(trimToNull(item.getParentCategory()));
            spmi.setCategory(trimToNull(item.getCategory()));
            spmi.setUnit("kg");
            spmi.setPurchaseQty(item.getWeight());
            spmi.setUnitPrice(item.getUnitPrice());
            spmi.setTotalAmount(item.getWeight().multiply(item.getUnitPrice()).setScale(2, RoundingMode.HALF_UP));
            spmi.setRemark(trimToNull(item.getRemark()));
            spmi.setSortNo(sortNo++);
            spmItemMapper.insert(spmi);
        }
    }

    /** 软删某单的全部明细（编辑重建用） */
    private void deleteItems(String bizCode) {
        List<SelfPurchaseMaterialItem> oldItems = spmItemMapper.selectList(
                new LambdaQueryWrapper<SelfPurchaseMaterialItem>()
                        .eq(SelfPurchaseMaterialItem::getBizCode, bizCode));
        for (SelfPurchaseMaterialItem oi : oldItems) {
            spmItemMapper.deleteById(oi.getId());
        }
    }

    private void requireStore(String storeId) {
        if (!StringUtils.hasText(storeId)) {
            throw new BusinessException(403, "请先绑定门店");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExpenseRecord update(String storeId, String expenseId, MpExpenseSaveReq req, String handlerName) {
        ExpenseType type = findEnabledType(req.getTypeId());
        boolean newIsSelfPurchase = SELF_PURCHASE_TYPE.equals(type.getName());

        // 定位原记录所在表
        ExpenseRecord oldRecord = expenseRecordMapper.selectOne(new LambdaQueryWrapper<ExpenseRecord>()
                .eq(ExpenseRecord::getStoreId, storeId)
                .eq(ExpenseRecord::getExpenseId, expenseId));
        SelfPurchaseMaterial oldSpm = oldRecord == null
                ? spmMapper.selectOne(new LambdaQueryWrapper<SelfPurchaseMaterial>()
                        .eq(SelfPurchaseMaterial::getBizCode, expenseId)
                        .eq(SelfPurchaseMaterial::getStoreId, storeId))
                : null;
        if (oldRecord == null && oldSpm == null) {
            throw new BusinessException(404, "记录不存在");
        }
        if (StringUtils.hasText(handlerName)) {
            String owner = oldSpm != null ? oldSpm.getHandlerName() : oldRecord.getHandlerName();
            if (!handlerName.equals(owner)) {
                throw new BusinessException(403, "只能修改自己登记的支出");
            }
        }
        boolean oldIsSelfPurchase = oldSpm != null;

        // 跨类切换（自购食材 ↔ 其他类型）：软删旧记录，按新类型原地重建（expenseId 不变）
        if (newIsSelfPurchase != oldIsSelfPurchase) {
            String storeName = oldSpm != null ? oldSpm.getStoreName() : oldRecord.getStoreName();
            if (oldIsSelfPurchase) {
                spmItemMapper.delete(new LambdaQueryWrapper<SelfPurchaseMaterialItem>()
                        .eq(SelfPurchaseMaterialItem::getBizCode, expenseId));
                spmMapper.deleteById(oldSpm.getId());
            } else {
                deleteAmountItems(expenseId);
                expenseRecordMapper.deleteById(oldRecord.getId());
            }
            StoreInfo store = storeService.getStoreById(storeId);
            if (newIsSelfPurchase) {
                return createSelfPurchaseOnly(storeId, storeName,
                        store != null ? store.getXiaochengxuid() : null, type, req, expenseId);
            }
            return createExpenseRecordOnly(storeId, storeName, store, type, req, expenseId);
        }

        // 自购食材：主表更新 + 明细软删重建
        if (newIsSelfPurchase) {
            List<MpExpenseSaveReq.ItemReq> itemList = resolveItems(req);
            validateItems(itemList);

            oldSpm.setPurchaseDate(req.getOccurredDate());
            oldSpm.setPurchaseMonth(req.getOccurredDate() != null
                    ? req.getOccurredDate().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"))
                    : LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM")));
            List<String> vouchers = resolveVoucherUrls(req);
            oldSpm.setTotalAmount(calcTotalAmount(itemList));
            oldSpm.setHandlerName(req.getHandlerName().trim());
            oldSpm.setVoucherUrl(joinVouchers(vouchers));
            oldSpm.setRemark(trimToNull(req.getRemark()));
            spmMapper.updateById(oldSpm);

            deleteItems(expenseId);
            insertItems(expenseId, itemList);

            ExpenseRecord record = new ExpenseRecord();
            record.setExpenseId(expenseId);
            record.setTypeId(type.getTypeId());
            record.setTypeName(type.getName());
            record.setAmount(oldSpm.getTotalAmount() != null ? oldSpm.getTotalAmount() : BigDecimal.ZERO);
            record.setOccurredDate(req.getOccurredDate());
            record.setHandlerName(req.getHandlerName());
            record.setRemark(req.getRemark());
            return record;
        }

        // 其他类型：expense_record 更新 + 明细软删重建（明细为空时仅清旧明细，金额手填）
        List<MpExpenseSaveReq.AmountItem> itemList = resolveAmountItems(req);
        if (itemList != null) {
            validateAmountItems(itemList);
        }
        oldRecord.setTypeId(type.getTypeId());
        oldRecord.setTypeName(type.getName());
        oldRecord.setFirstTypeId(nvl(type.getFirstTypeId()));
        oldRecord.setFirstTypeName(nvl(type.getFirstTypeName()));
        List<String> vouchers = resolveVoucherUrls(req);
        oldRecord.setAmount(itemList != null ? calcPlainTotal(itemList) : req.getAmount());
        oldRecord.setOccurredDate(req.getOccurredDate());
        oldRecord.setHandlerName(req.getHandlerName().trim());
        oldRecord.setVoucherUrl(joinVouchers(vouchers));
        oldRecord.setRemark(trimToNull(req.getRemark()));
        expenseRecordMapper.updateById(oldRecord);
        deleteAmountItems(expenseId);
        if (itemList != null) {
            insertAmountItems(expenseId, itemList);
        }
        return oldRecord;
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
            deleteAmountItems(expenseId);
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

    /** 凭证解析：优先 voucherUrls（多张），兜底单值 voucherUrl；最多9张 */
    private List<String> resolveVoucherUrls(MpExpenseSaveReq req) {
        if (req.getVoucherUrls() != null && !req.getVoucherUrls().isEmpty()) {
            List<String> list = new ArrayList<>();
            for (String u : req.getVoucherUrls()) {
                if (StringUtils.hasText(u)) {
                    list.add(u.trim());
                }
            }
            if (!list.isEmpty()) {
                if (list.size() > MAX_VOUCHER_SIZE) {
                    throw new BusinessException(400, "最多上传" + MAX_VOUCHER_SIZE + "张凭证");
                }
                return list;
            }
        }
        String single = trimToNull(req.getVoucherUrl());
        return single != null ? Collections.singletonList(single) : Collections.emptyList();
    }

    /** 凭证拼接存储：逗号分隔（多张，首张=主凭证） */
    private String joinVouchers(List<String> urls) {
        return urls.isEmpty() ? null : String.join(",", urls);
    }

    /** 凭证拆分读取：逗号分隔（兼容历史单张），过滤空段 */
    private List<String> splitVoucherUrls(String voucherUrl) {
        List<String> list = new ArrayList<>();
        if (StringUtils.hasText(voucherUrl)) {
            for (String u : voucherUrl.split(",")) {
                if (StringUtils.hasText(u)) {
                    list.add(u.trim());
                }
            }
        }
        return list;
    }

    private ExpenseType findEnabledType(String typeId) {
        ExpenseType type = expenseTypeMapper.selectOne(new LambdaQueryWrapper<ExpenseType>()
                .eq(ExpenseType::getTypeId, typeId)
                .eq(ExpenseType::getStatus, STATUS_ENABLED));
        if (type == null) {
            throw new BusinessException(400, "支出类型不存在或已停用");
        }
        return type;
    }

    /** 非自购类型明细入参：null = 未提供明细（金额走 req.amount 手填） */
    private List<MpExpenseSaveReq.AmountItem> resolveAmountItems(MpExpenseSaveReq req) {
        if (req.getAmountItems() != null && !req.getAmountItems().isEmpty()) {
            return req.getAmountItems();
        }
        return null;
    }

    /** 校验非自购类型明细：最多 10 条，每条名称/金额必填且金额大于 0 */
    private void validateAmountItems(List<MpExpenseSaveReq.AmountItem> itemList) {
        if (itemList.size() > SELF_PURCHASE_MAX_ITEMS) {
            throw new BusinessException(400, "最多添加" + SELF_PURCHASE_MAX_ITEMS + "项明细");
        }
        for (MpExpenseSaveReq.AmountItem item : itemList) {
            if (!StringUtils.hasText(item.getName())) {
                throw new BusinessException(400, "请填写明细名称");
            }
            if (item.getAmount() == null || item.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(400, "明细[" + item.getName() + "]请填写金额");
            }
        }
    }

    private BigDecimal calcPlainTotal(List<MpExpenseSaveReq.AmountItem> itemList) {
        BigDecimal total = BigDecimal.ZERO;
        for (MpExpenseSaveReq.AmountItem item : itemList) {
            total = total.add(item.getAmount().setScale(2, RoundingMode.HALF_UP));
        }
        return total;
    }

    private void insertAmountItems(String expenseId, List<MpExpenseSaveReq.AmountItem> itemList) {
        int sortNo = 0;
        for (MpExpenseSaveReq.AmountItem item : itemList) {
            ExpenseRecordItem eri = new ExpenseRecordItem();
            eri.setExpenseId(expenseId);
            eri.setItemName(item.getName().trim());
            eri.setAmount(item.getAmount().setScale(2, RoundingMode.HALF_UP));
            eri.setSortNo(sortNo++);
            expenseRecordItemMapper.insert(eri);
        }
    }

    private void deleteAmountItems(String expenseId) {
        expenseRecordItemMapper.delete(new LambdaQueryWrapper<ExpenseRecordItem>()
                .eq(ExpenseRecordItem::getExpenseId, expenseId));
    }

    @Override
    public List<ExpenseItemVO> listItems(String storeId, String expenseId) {
        // 先查 expense_record（普通支出明细）
        ExpenseRecord record = expenseRecordMapper.selectOne(new LambdaQueryWrapper<ExpenseRecord>()
                .eq(ExpenseRecord::getStoreId, storeId)
                .eq(ExpenseRecord::getExpenseId, expenseId));
        if (record != null) {
            List<ExpenseItemVO> vos = new ArrayList<>();
            List<ExpenseRecordItem> items = expenseRecordItemMapper.selectList(new LambdaQueryWrapper<ExpenseRecordItem>()
                    .eq(ExpenseRecordItem::getExpenseId, expenseId)
                    .orderByAsc(ExpenseRecordItem::getSortNo)
                    .orderByAsc(ExpenseRecordItem::getId));
            for (ExpenseRecordItem it : items) {
                ExpenseItemVO vo = new ExpenseItemVO();
                vo.setName(it.getItemName());
                vo.setAmount(it.getAmount());
                vo.setSortNo(it.getSortNo());
                vos.add(vo);
            }
            return vos;
        }
        // 再查 self_purchase_material（自购食材物料明细）
        SelfPurchaseMaterial spm = spmMapper.selectOne(new LambdaQueryWrapper<SelfPurchaseMaterial>()
                .eq(SelfPurchaseMaterial::getBizCode, expenseId)
                .eq(SelfPurchaseMaterial::getStoreId, storeId));
        if (spm != null) {
            List<ExpenseItemVO> vos = new ArrayList<>();
            List<SelfPurchaseMaterialItem> items = spmItemMapper.selectList(new LambdaQueryWrapper<SelfPurchaseMaterialItem>()
                    .eq(SelfPurchaseMaterialItem::getBizCode, expenseId)
                    .orderByAsc(SelfPurchaseMaterialItem::getSortNo)
                    .orderByAsc(SelfPurchaseMaterialItem::getId));
            for (SelfPurchaseMaterialItem it : items) {
                ExpenseItemVO vo = new ExpenseItemVO();
                vo.setMaterialId(it.getMaterialId());
                vo.setName(it.getMaterialName());
                vo.setParentCategory(it.getParentCategory());
                vo.setCategory(it.getCategory());
                vo.setUnit(it.getUnit());
                vo.setQty(it.getPurchaseQty());
                vo.setUnitPrice(it.getUnitPrice());
                vo.setAmount(it.getTotalAmount());
                vo.setSortNo(it.getSortNo());
                vo.setRemark(it.getRemark());
                vos.add(vo);
            }
            return vos;
        }
        return Collections.emptyList();
    }

}
