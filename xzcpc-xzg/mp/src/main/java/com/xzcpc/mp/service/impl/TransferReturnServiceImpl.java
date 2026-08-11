package com.xzcpc.mp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xzcpc.common.exception.BusinessException;
import com.xzcpc.mp.dto.TransferReturnReq;
import com.xzcpc.mp.entity.TransferOrder;
import com.xzcpc.mp.entity.TransferOrderItem;
import com.xzcpc.mp.entity.TransferReturnRecord;
import com.xzcpc.mp.mapper.TransferOrderItemMapper;
import com.xzcpc.mp.mapper.TransferOrderMapper;
import com.xzcpc.mp.mapper.TransferReturnRecordMapper;
import com.xzcpc.mp.service.TransferReturnService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransferReturnServiceImpl implements TransferReturnService {

    private final TransferOrderMapper orderMapper;
    private final TransferOrderItemMapper itemMapper;
    private final TransferReturnRecordMapper returnMapper;

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void doReturn(Long transferId, String storeId, String employeeName, TransferReturnReq req) {
        TransferOrder order = orderMapper.selectById(transferId);
        if (order == null) throw new BusinessException(400, "调货单不存在");
        if (!"completed".equals(order.getStatus())) {
            throw new BusinessException(400, "仅已完成的调货单可以还货");
        }
        if (order.getToStoreId() == null || !order.getToStoreId().equals(storeId)) {
            throw new BusinessException(403, "仅调入方门店可以还货");
        }

        List<TransferOrderItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<TransferOrderItem>().eq(TransferOrderItem::getTransferId, transferId));

        String action = req.getAction();
        if ("bulk_goods".equals(action) || "bulk_money".equals(action)) {
            String returnType = "bulk_goods".equals(action) ? "goods" : "money";
            for (TransferOrderItem item : items) {
                BigDecimal remaining = getRemainingQty(item);
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) continue;
                doReturnItem(order, item, returnType, remaining,
                        returnType.equals("money") ? remaining.multiply(
                                item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO) : BigDecimal.ZERO,
                        employeeName, req.getRemark());
            }
        } else {
            if (req.getItems() == null || req.getItems().isEmpty()) {
                throw new BusinessException(400, "请选择要归还的物料");
            }
            for (TransferReturnReq.ReturnItem ri : req.getItems()) {
                TransferOrderItem item = items.stream()
                        .filter(i -> i.getId().equals(ri.getItemId())).findFirst()
                        .orElseThrow(() -> new BusinessException(400, "物料明细不存在: " + ri.getItemId()));

                BigDecimal qty = ri.getReturnQty() != null ? ri.getReturnQty() : BigDecimal.ZERO;
                BigDecimal amount = ri.getReturnAmount() != null ? ri.getReturnAmount() : BigDecimal.ZERO;
                if ("money".equals(ri.getReturnType())) {
                    // 还钱: 由金额反推数量
                    BigDecimal price = item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ONE;
                    if (amount.compareTo(BigDecimal.ZERO) <= 0) continue;
                    qty = amount.divide(price, 4, java.math.RoundingMode.HALF_UP);
                } else {
                    // 还货: 校验不超过剩余数量
                    BigDecimal remaining = getRemainingQty(item);
                    if (remaining.compareTo(BigDecimal.ZERO) <= 0) continue;
                    if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;
                    if (qty.compareTo(remaining) > 0) {
                        throw new BusinessException(400, "归还数量超过剩余数量: " + item.getMaterialName());
                    }
                }
                doReturnItem(order, item, ri.getReturnType(), qty, amount, employeeName, req.getRemark());
            }
        }

        // 不再自动变更为 returned，由调出门店手动确认
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmReturn(Long transferId, String storeId) {
        TransferOrder order = orderMapper.selectById(transferId);
        if (order == null) throw new BusinessException(400, "调货单不存在");
        if (!"completed".equals(order.getStatus())) {
            throw new BusinessException(400, "仅已完成的调货单可确认还货");
        }
        if (order.getFromStoreId() == null || !order.getFromStoreId().equals(storeId)) {
            throw new BusinessException(403, "仅调出门店可确认还货完成");
        }

        order.setHandoff("returned");
        order.setCompletedAt(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    private void doReturnItem(TransferOrder order, TransferOrderItem item, String returnType,
                               BigDecimal qty, BigDecimal amount, String handlerName, String remark) {
        String recId = "TRR" + LocalDateTime.now().format(DTF) + String.format("%04d", (int)(Math.random() * 10000));
        TransferReturnRecord rec = new TransferReturnRecord();
        rec.setRecordId(recId);
        rec.setTransferId(order.getId());
        rec.setItemId(item.getId());
        rec.setReturnType(returnType);
        rec.setReturnQty(qty);
        rec.setReturnAmount(amount);
        rec.setUnitPrice(item.getUnitPrice());
        rec.setHandlerName(handlerName);
        rec.setRemark(remark);
        returnMapper.insert(rec);
    }

    /** 计算物料剩余可归还数量(调货总数 - 还货已归还 - 还钱折算数量) */
    private BigDecimal getRemainingQty(TransferOrderItem item) {
        BigDecimal goodsReturned = getReturnedQtyByType(item.getId(), "goods");
        // 还钱: amount/unitPrice = 折算数量
        BigDecimal moneyAmount = getReturnedAmount(item.getId());
        BigDecimal price = item.getUnitPrice() != null && item.getUnitPrice().compareTo(BigDecimal.ZERO) > 0
                ? item.getUnitPrice() : BigDecimal.ONE;
        BigDecimal moneyQty = moneyAmount.divide(price, 4, java.math.RoundingMode.HALF_UP);
        return item.getTransferQty().subtract(goodsReturned).subtract(moneyQty);
    }

    private BigDecimal getReturnedQtyByType(Long itemId, String type) {
        List<TransferReturnRecord> records = returnMapper.selectList(
                new LambdaQueryWrapper<TransferReturnRecord>()
                        .eq(TransferReturnRecord::getItemId, itemId)
                        .eq(TransferReturnRecord::getReturnType, type));
        return records.stream()
                .map(r -> r.getReturnQty() != null ? r.getReturnQty() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 聚合某物料还钱总金额 */
    private BigDecimal getReturnedAmount(Long itemId) {
        List<TransferReturnRecord> records = returnMapper.selectList(
                new LambdaQueryWrapper<TransferReturnRecord>()
                        .eq(TransferReturnRecord::getItemId, itemId)
                        .eq(TransferReturnRecord::getReturnType, "money"));
        return records.stream()
                .map(r -> r.getReturnAmount() != null ? r.getReturnAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 从归还记录表聚合某物料的已归还总数(含还货+还钱) */
    public BigDecimal getReturnedQty(Long itemId) {
        return getReturnedQtyByType(itemId, "goods").add(getReturnedQtyByType(itemId, "money"));
    }

}
