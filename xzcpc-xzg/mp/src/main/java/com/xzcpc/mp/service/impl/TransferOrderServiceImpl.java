package com.xzcpc.mp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.common.context.AdminContextHolder;
import com.xzcpc.common.context.AdminUser;
import com.xzcpc.common.exception.BusinessException;
import com.xzcpc.common.service.StoreAccessService;
import com.xzcpc.common.util.BizCodeUtil;
import com.xzcpc.mp.context.UserContextHolder;
import com.xzcpc.mp.dto.TransferCreateReq;
import com.xzcpc.mp.entity.TransferOrder;
import com.xzcpc.mp.entity.TransferOrderItem;
import com.xzcpc.mp.mapper.TransferOrderItemMapper;
import com.xzcpc.mp.mapper.TransferOrderMapper;
import com.xzcpc.mp.service.MpStaffService;
import com.xzcpc.mp.service.NotificationService;
import com.xzcpc.mp.service.TransferOrderService;
import com.xzcpc.people.entity.Employee;
import com.xzcpc.people.mapper.EmployeeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransferOrderServiceImpl implements TransferOrderService {

    private final TransferOrderMapper orderMapper;
    private final TransferOrderItemMapper itemMapper;
    private final EmployeeMapper employeeMapper;
    private final MpStaffService staffService;
    private final StoreAccessService storeAccessService;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public TransferOrder create(TransferCreateReq req) {
        var user = UserContextHolder.get();
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new BusinessException("请至少添加一个调货物料");
        }

        TransferOrder order = new TransferOrder();
        order.setBizCode(BizCodeUtil.of("TRF"));
        order.setFromStoreId(req.getFromStoreId());
        order.setFromStoreName(req.getFromStoreName());
        order.setToStoreId(req.getToStoreId());
        order.setToStoreName(req.getToStoreName());
        order.setStatus("pending_confirm");
        order.setRemark(req.getRemark());
        order.setCreatorStoreId(user.getStoreId());
        order.setCreatedBy(user.getOpenid());

        // Calculate total qty
        BigDecimal totalQty = req.getItems().stream()
                .map(TransferCreateReq.TransferItemReq::getTransferQty)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setTotalQty(totalQty);

        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        orderMapper.insert(order);

        // Insert items
        for (var itemReq : req.getItems()) {
            TransferOrderItem item = new TransferOrderItem();
            item.setTransferId(order.getId());
            item.setMaterialName(itemReq.getMaterialName());
            item.setSpec(itemReq.getSpec() != null ? itemReq.getSpec() : "");
            item.setUnit(itemReq.getUnit());
            item.setTransferQty(itemReq.getTransferQty());
            item.setBaseUnit(itemReq.getBaseUnit());
            item.setBaseQty(itemReq.getBaseQty());
            item.setInputUnit(itemReq.getInputUnit());
            item.setInputQty(itemReq.getInputQty());
            item.setUnitPrice(itemReq.getUnitPrice());
            item.setRemark(itemReq.getRemark());
            itemMapper.insert(item);
        }

        // 如果调出地是本人门店，自动确认+发货
        if (order.getFromStoreId().equals(user.getStoreId())) {
            order.setStatus("confirmed");
            order.setConfirmedBy(user.getOpenid());
            order.setConfirmedAt(LocalDateTime.now());
            order.setStatus("pending_ship");
            order.setShippedBy(user.getOpenid());
            order.setShippedAt(LocalDateTime.now());
            order.setHandoff("门店自取");
            order.setUpdatedAt(LocalDateTime.now());
            orderMapper.updateById(order);
        }

        // 订阅消息：本店主动调出（自动发货）→ 通知调入方待收货；申请调货 → 通知调出方确认
        if (order.getFromStoreId().equals(user.getStoreId())) {
            notificationService.enqueueToStoreManagers(order.getToStoreId(), "TRANSFER_RECEIVE",
                    "【调货】有一笔调货待收货",
                    "调出方已发货，请及时确认收货",
                    String.valueOf(order.getId()),
                    "/pages/transfer/list/index");
        } else {
            notificationService.enqueueToStoreManagers(order.getFromStoreId(), "TRANSFER_CONFIRM",
                    "【调货】收到一笔调货申请",
                    "请确认后安排发货",
                    String.valueOf(order.getId()),
                    "/pages/transfer/list/index");
        }

        return order;
    }

    @Override
    @Transactional
    public TransferOrder confirm(Long id) {
        var user = UserContextHolder.get();
        TransferOrder order = getByIdForStore(id, user.getStoreId());
        if (!order.getFromStoreId().equals(user.getStoreId())) {
            throw new BusinessException(403, "仅调出方可确认调货");
        }
        order.assertCanTransition("confirm");
        order.setStatus("confirmed");
        order.setConfirmedBy(user.getOpenid());
        order.setConfirmedAt(LocalDateTime.now());
        // 确认后自动发货：confirmed → pending_ship
        order.setStatus("pending_ship");
        order.setShippedBy(user.getOpenid());
        order.setShippedAt(LocalDateTime.now());
        // 默认交接方式为门店自取
        if (order.getHandoff() == null || order.getHandoff().isEmpty()) {
            order.setHandoff("门店自取");
        }
        order.setUpdatedAt(LocalDateTime.now());
        orderMapper.updateById(order);

        // 订阅消息：调出方确认（自动发货）→ 通知调入方待收货
        notificationService.enqueueToStoreManagers(order.getToStoreId(), "TRANSFER_RECEIVE",
                "【调货】有一笔调货待收货",
                "调出方已确认发货，请及时确认收货",
                String.valueOf(order.getId()),
                "/pages/transfer/list/index");
        return order;
    }

    @Override
    @Transactional
    public TransferOrder ship(Long id) {
        var user = UserContextHolder.get();
        TransferOrder order = getByIdForStore(id, user.getStoreId());
        if (!order.getFromStoreId().equals(user.getStoreId())) {
            throw new BusinessException(403, "仅调出方可标记发货");
        }
        order.assertCanTransition("ship");
        order.setStatus("pending_ship");
        order.setShippedBy(user.getOpenid());
        order.setShippedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        orderMapper.updateById(order);
        return order;
    }

    @Override
    @Transactional
    public TransferOrder receive(Long id) {
        var user = UserContextHolder.get();
        TransferOrder order = getByIdForStore(id, user.getStoreId());
        // 只有调入方（to_store）可以确认收货
        if (!order.getToStoreId().equals(user.getStoreId())) {
            throw new BusinessException(403, "仅调入方可确认收货");
        }
        order.assertCanTransition("receive");
        order.setStatus("pending_receive");
        order.setReceivedBy(user.getOpenid());
        order.setReceivedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        orderMapper.updateById(order);

        // Auto-complete: pending_receive → completed
        order.setStatus("completed");
        order.setCompletedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        orderMapper.updateById(order);
        return order;
    }

    @Override
    @Transactional
    public TransferOrder cancel(Long id) {
        var user = UserContextHolder.get();
        TransferOrder order = getByIdForStore(id, user.getStoreId());
        // 只有发起方（调入方）可以取消
        if (!order.getToStoreId().equals(user.getStoreId())) {
            throw new BusinessException(403, "仅发起方可取消调货");
        }
        order.assertCanTransition("cancel");
        order.setStatus("cancelled");
        order.setCancelledAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        orderMapper.updateById(order);
        return order;
    }

    @Override
    @Transactional
    public TransferOrder reject(Long id) {
        var user = UserContextHolder.get();
        TransferOrder order = getByIdForStore(id, user.getStoreId());
        // 只有调出方可以拒绝
        if (!order.getFromStoreId().equals(user.getStoreId())) {
            throw new BusinessException(403, "仅调出方可拒绝调货");
        }
        order.assertCanTransition("reject");
        order.setStatus("rejected");
        order.setCancelledAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        orderMapper.updateById(order);

        // 订阅消息：调货被拒 → 通知发起方店长
        notificationService.enqueueToStoreManagers(order.getCreatorStoreId(), "TRANSFER_REJECT",
                "【调货】您的调货申请被拒绝",
                "请查看原调货单，可联系对方沟通",
                String.valueOf(order.getId()),
                "/pages/transfer/list/index");
        return order;
    }

    @Override
    public Page<TransferOrder> pageByStore(String storeId, String status, int pageNum, int pageSize) {
        var qw = new LambdaQueryWrapper<TransferOrder>()
                .and(w -> w.eq(TransferOrder::getFromStoreId, storeId)
                        .or().eq(TransferOrder::getToStoreId, storeId))
                .orderByDesc(TransferOrder::getCreatedAt);
        if (StringUtils.hasText(status)) {
            qw.eq(TransferOrder::getStatus, status);
        }
        return orderMapper.selectPage(new Page<>(pageNum, pageSize), qw);
    }

    @Override
    public Page<TransferOrder> pageByStores(String openid, String status, int pageNum, int pageSize) {
        List<Map<String, Object>> stores = staffService.findStoresByOpenid(openid);
        List<String> storeIds = stores.stream().map(s -> (String) s.get("storeId")).filter(Objects::nonNull).toList();
        if (storeIds.isEmpty()) {
            return new Page<>(pageNum, pageSize);
        }
        var qw = new LambdaQueryWrapper<TransferOrder>()
                .and(w -> {
                    w.in(TransferOrder::getFromStoreId, storeIds)
                     .or().in(TransferOrder::getToStoreId, storeIds);
                })
                .orderByDesc(TransferOrder::getCreatedAt);
        if (StringUtils.hasText(status)) {
            qw.eq(TransferOrder::getStatus, status);
        }
        return orderMapper.selectPage(new Page<>(pageNum, pageSize), qw);
    }

    @Override
    public TransferOrder detail(Long id, String storeId) {
        TransferOrder order = getByIdForStore(id, storeId);
        return order;
    }

    @Override
    public List<TransferOrderItem> getItems(Long transferId) {
        return itemMapper.selectList(
                new LambdaQueryWrapper<TransferOrderItem>()
                        .eq(TransferOrderItem::getTransferId, transferId));
    }

    @Override
    public Map<Long, List<TransferOrderItem>> getItemsBatch(List<Long> transferIds) {
        if (transferIds.isEmpty()) return Map.of();
        return itemMapper.selectList(
                new LambdaQueryWrapper<TransferOrderItem>()
                        .in(TransferOrderItem::getTransferId, transferIds))
                .stream().collect(Collectors.groupingBy(TransferOrderItem::getTransferId));
    }

    @Override
    public Map<String, Long> overview(String storeId) {
        // Query all non-terminal orders for this store
        var list = orderMapper.selectList(
                new LambdaQueryWrapper<TransferOrder>()
                        .and(w -> w.eq(TransferOrder::getFromStoreId, storeId)
                                .or().eq(TransferOrder::getToStoreId, storeId))
                        .notIn(TransferOrder::getStatus, "completed", "cancelled"));

        Map<String, Long> result = new LinkedHashMap<>();
        result.put("pendingConfirm", 0L);
        result.put("pendingShip", 0L);
        result.put("completed", 0L);
        result.put("rejected", 0L);

        for (TransferOrder o : list) {
            switch (o.getStatus()) {
                case "pending_confirm" -> result.merge("pendingConfirm", 1L, Long::sum);
                case "pending_ship" -> result.merge("pendingShip", 1L, Long::sum);
                case "pending_receive" -> result.merge("pendingShip", 1L, Long::sum);
                case "rejected" -> result.merge("rejected", 1L, Long::sum);
            }
        }

        Long completedCount = orderMapper.selectCount(
                new LambdaQueryWrapper<TransferOrder>()
                        .and(w -> w.eq(TransferOrder::getFromStoreId, storeId)
                                .or().eq(TransferOrder::getToStoreId, storeId))
                        .eq(TransferOrder::getStatus, "completed"));
        result.put("completed", completedCount);

        return result;
    }

    @Override
    public List<Map<String, Object>> overviewByStores(String openid) {
        List<Map<String, Object>> stores = staffService.findStoresByOpenid(openid);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> s : stores) {
            String sid = (String) s.get("storeId");
            String sname = (String) s.get("storeName");
            // 需要本店操作的：待确认=fromStore，待收货=toStore
            Long confirm = orderMapper.selectCount(
                    new LambdaQueryWrapper<TransferOrder>()
                            .eq(TransferOrder::getFromStoreId, sid)
                            .eq(TransferOrder::getStatus, "pending_confirm"));
            Long ship = orderMapper.selectCount(
                    new LambdaQueryWrapper<TransferOrder>()
                            .eq(TransferOrder::getToStoreId, sid)
                            .in(TransferOrder::getStatus, "pending_ship", "pending_receive"));
            long total = (confirm != null ? confirm : 0) + (ship != null ? ship : 0);
            if (total > 0) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("storeId", sid);
                item.put("storeName", sname);
                item.put("pending", total);
                result.add(item);
            }
        }
        return result;
    }

    @Override
    public long overviewActionRequired(String storeId) {
        Long confirm = orderMapper.selectCount(
                new LambdaQueryWrapper<TransferOrder>()
                        .eq(TransferOrder::getFromStoreId, storeId)
                        .eq(TransferOrder::getStatus, "pending_confirm"));
        Long ship = orderMapper.selectCount(
                new LambdaQueryWrapper<TransferOrder>()
                        .eq(TransferOrder::getToStoreId, storeId)
                        .in(TransferOrder::getStatus, "pending_ship", "pending_receive"));
        return (confirm != null ? confirm : 0) + (ship != null ? ship : 0);
    }

    @Override
    public Map<String, Long> overviewTotal(String openid) {
        List<Map<String, Object>> stores = staffService.findStoresByOpenid(openid);
        List<String> storeIds = stores.stream().map(s -> (String) s.get("storeId")).filter(Objects::nonNull).toList();
        if (storeIds.isEmpty()) {
            Map<String, Long> empty = new LinkedHashMap<>();
            empty.put("pendingConfirm", 0L); empty.put("pendingShip", 0L);
            empty.put("completed", 0L); empty.put("rejected", 0L);
            return empty;
        }
        // Single query: count unique orders across all stores (each order counted once even if both stores belong to user)
        Long confirm = orderMapper.selectCount(
                new LambdaQueryWrapper<TransferOrder>()
                        .and(w -> w.in(TransferOrder::getFromStoreId, storeIds)
                                .or().in(TransferOrder::getToStoreId, storeIds))
                        .eq(TransferOrder::getStatus, "pending_confirm"));
        Long ship = orderMapper.selectCount(
                new LambdaQueryWrapper<TransferOrder>()
                        .and(w -> w.in(TransferOrder::getFromStoreId, storeIds)
                                .or().in(TransferOrder::getToStoreId, storeIds))
                        .in(TransferOrder::getStatus, "pending_ship", "pending_receive"));
        Long completed = orderMapper.selectCount(
                new LambdaQueryWrapper<TransferOrder>()
                        .and(w -> w.in(TransferOrder::getFromStoreId, storeIds)
                                .or().in(TransferOrder::getToStoreId, storeIds))
                        .eq(TransferOrder::getStatus, "completed"));
        Long rejected = orderMapper.selectCount(
                new LambdaQueryWrapper<TransferOrder>()
                        .and(w -> w.in(TransferOrder::getFromStoreId, storeIds)
                                .or().in(TransferOrder::getToStoreId, storeIds))
                        .eq(TransferOrder::getStatus, "rejected"));
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("pendingConfirm", confirm != null ? confirm : 0);
        result.put("pendingShip", ship != null ? ship : 0);
        result.put("completed", completed != null ? completed : 0);
        result.put("rejected", rejected != null ? rejected : 0);
        return result;
    }

    @Override
    public Page<TransferOrder> pageAll(String fromStoreId, String toStoreId, String supervisorName, String status, String handoff,
                                       String keyword, String startDate, String endDate,
                                       int pageNum, int pageSize) {
        var qw = new LambdaQueryWrapper<TransferOrder>()
                .orderByDesc(TransferOrder::getCreatedAt);

        // 督导角色：按可访问门店过滤（调出或调入任一为自己管辖门店）
        AdminUser admin = AdminContextHolder.get();
        if (admin != null && storeAccessService.isSupervisorOnly(admin)) {
            List<String> accessibleStoreIds = storeAccessService.getAccessibleStoreIds(admin.getOpenId());
            if (accessibleStoreIds.isEmpty()) {
                return new Page<>(pageNum, pageSize);
            }
            qw.and(w -> w.in(TransferOrder::getFromStoreId, accessibleStoreIds)
                    .or().in(TransferOrder::getToStoreId, accessibleStoreIds));
        }

        // 按指定督导过滤
        if (StringUtils.hasText(supervisorName)) {
            List<String> supervisorStoreIds = storeAccessService.getAccessibleStoreIdsBySupervisorName(supervisorName);
            if (supervisorStoreIds.isEmpty()) {
                return new Page<>(pageNum, pageSize);
            }
            qw.and(w -> w.in(TransferOrder::getFromStoreId, supervisorStoreIds)
                    .or().in(TransferOrder::getToStoreId, supervisorStoreIds));
        }

        if (StringUtils.hasText(fromStoreId)) qw.eq(TransferOrder::getFromStoreId, fromStoreId);
        if (StringUtils.hasText(toStoreId)) qw.eq(TransferOrder::getToStoreId, toStoreId);
        if (StringUtils.hasText(status)) qw.eq(TransferOrder::getStatus, status);
        if (StringUtils.hasText(handoff)) qw.eq(TransferOrder::getHandoff, handoff);

        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(TransferOrder::getBizCode, keyword)
                    .or().like(TransferOrder::getFromStoreName, keyword)
                    .or().like(TransferOrder::getToStoreName, keyword)
                    .or().like(TransferOrder::getRemark, keyword));
        }
        if (StringUtils.hasText(startDate)) {
            qw.ge(TransferOrder::getCreatedAt, LocalDate.parse(startDate).atStartOfDay());
        }
        if (StringUtils.hasText(endDate)) {
            qw.le(TransferOrder::getCreatedAt, LocalDate.parse(endDate).plusDays(1).atStartOfDay());
        }

        Page<TransferOrder> page = orderMapper.selectPage(new Page<>(pageNum, pageSize), qw);

        // 批量填充物品名称摘要
        List<TransferOrder> records = page.getRecords();
        if (!records.isEmpty()) {
            List<Long> orderIds = records.stream().map(TransferOrder::getId).toList();
            List<TransferOrderItem> allItems = itemMapper.selectList(
                    new LambdaQueryWrapper<TransferOrderItem>()
                            .in(TransferOrderItem::getTransferId, orderIds));
            Map<Long, List<TransferOrderItem>> itemsByOrder = allItems.stream()
                    .collect(java.util.stream.Collectors.groupingBy(TransferOrderItem::getTransferId));
            for (TransferOrder order : records) {
                List<TransferOrderItem> items = itemsByOrder.get(order.getId());
                if (items != null && !items.isEmpty()) {
                    String firstName = items.get(0).getMaterialName();
                    order.setItemNames(items.size() > 1 ? firstName + " 等" + items.size() + "项" : firstName);
                }
            }

            // 批量填充发起人姓名（openid → employee.name）
            java.util.Set<String> openids = records.stream()
                    .map(TransferOrder::getCreatedBy)
                    .filter(StringUtils::hasText)
                    .collect(java.util.stream.Collectors.toSet());
            if (!openids.isEmpty()) {
                List<Employee> employees = employeeMapper.selectList(
                        new LambdaQueryWrapper<Employee>()
                                .in(Employee::getOpenid, new java.util.ArrayList<>(openids)));
                Map<String, String> nameByOpenid = employees.stream()
                        .collect(java.util.stream.Collectors.toMap(Employee::getOpenid, Employee::getName, (a, b) -> a));
                for (TransferOrder order : records) {
                    if (StringUtils.hasText(order.getCreatedBy())) {
                        order.setCreatedByName(nameByOpenid.getOrDefault(order.getCreatedBy(), order.getCreatedBy()));
                    }
                }
            }
        }

        // 填充督导姓名
        Set<String> fromStoreIds = records.stream().map(TransferOrder::getFromStoreId).filter(id -> id != null).collect(Collectors.toSet());
        if (!fromStoreIds.isEmpty()) {
            Map<String, String> supervisorMap = storeAccessService.getSupervisorNamesByStoreIds(fromStoreIds);
            records.forEach(r -> r.setSupervisorName(supervisorMap.getOrDefault(r.getFromStoreId(), "")));
        }

        return page;
    }

    private TransferOrder getByIdForStore(Long id, String storeId) {
        TransferOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException("调货单不存在");
        }
        // null storeId = allow all (for all-stores mode)
        if (storeId != null && !storeId.equals(order.getFromStoreId()) && !storeId.equals(order.getToStoreId())) {
            throw new BusinessException(403, "无权查看该调货单");
        }
        return order;
    }
}
