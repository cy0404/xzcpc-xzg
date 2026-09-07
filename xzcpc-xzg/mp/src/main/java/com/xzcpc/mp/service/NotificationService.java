package com.xzcpc.mp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xzcpc.mp.entity.NotificationLog;
import com.xzcpc.mp.entity.NotificationQuota;
import com.xzcpc.mp.mapper.NotificationLogMapper;
import com.xzcpc.mp.mapper.NotificationQuotaMapper;
import com.xzcpc.people.entity.Employee;
import com.xzcpc.people.mapper.EmployeeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订阅消息通知业务入口：
 * 1) 入队（与业务同事务写 notification_log status=0，由 NotificationSenderJob 轮询发送，发送永不阻塞业务）；
 * 2) 接收人解析：门店店长/经理/老板（employee 表 role 为中文：老板绑定也写入 employee.role=老板，一张表全覆盖）；
 * 3) 授权额度：plusQuota 授权 +1 / tryConsumeQuota 发送 -1 / refundQuota 失败回补。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    /** employee.status 在职 */
    private static final String STATUS_ACTIVE = "在职";

    private final NotificationLogMapper notificationLogMapper;
    private final NotificationQuotaMapper quotaMapper;
    private final EmployeeMapper employeeMapper;

    // ==================== 入队 ====================

    /** 按门店通知店长/老板（经理管多店 → 每店各收一条，由调用方按店入队） */
    public int enqueueToStoreManagers(String storeId, String eventType, String title,
                                      String content, String sourceId, String pagePath) {
        List<String> openids = resolveManagerOpenids(storeId);
        return enqueueToOpenids(openids, storeId, eventType, title, content, sourceId, pagePath);
    }

    /** 向指定 openid 列表入队（如问题提交人） */
    public int enqueueToOpenids(List<String> openids, String storeId, String eventType, String title,
                                String content, String sourceId, String pagePath) {
        int count = 0;
        for (String openid : openids) {
            enqueue(openid, storeId, eventType, title, content, sourceId, pagePath);
            count++;
        }
        return count;
    }

    /** 单条入队；与业务调用处于同一事务，业务回滚则通知不产生 */
    public void enqueue(String openid, String storeId, String eventType, String title,
                        String content, String sourceId, String pagePath) {
        if (!StringUtils.hasText(openid)) return;
        NotificationLog n = new NotificationLog();
        n.setEventType(eventType);
        n.setStoreId(storeId != null ? storeId : "");
        n.setTargetOpenid(openid);
        n.setTitle(truncate(title, 150));
        n.setContent(truncate(content, 800));
        // 跳转 URL 自动带上业务门店：多门店店长点消息卡片先进对应门店，落地页按 storeId 切店
        n.setPagePath(withStoreParam(pagePath, storeId));
        n.setSourceId(sourceId);
        n.setStatus(0);
        notificationLogMapper.insert(n);
    }

    /** 跳转路径拼接业务门店参数（pagePath 为空 / storeId 为空 / 已带 storeId 时不处理） */
    private String withStoreParam(String pagePath, String storeId) {
        if (!StringUtils.hasText(pagePath) || !StringUtils.hasText(storeId)) return pagePath;
        if (pagePath.contains("storeId=")) return pagePath;
        return pagePath + (pagePath.contains("?") ? "&" : "?") + "storeId=" + storeId;
    }

    /** 该门店在职工长/经理/老板 openid（老板绑定自动写入 employee.role=老板，故一张表全覆盖） */
    public List<String> resolveManagerOpenids(String storeId) {
        if (!StringUtils.hasText(storeId)) return List.of();
        List<Employee> list = employeeMapper.selectList(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getStoreId, storeId)
                .eq(Employee::getStatus, STATUS_ACTIVE)
                .and(w -> w.eq(Employee::getRole, "老板")
                        .or().like(Employee::getRole, "店长")
                        .or().like(Employee::getRole, "经理")));
        return list.stream()
                .map(Employee::getOpenid)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    // ==================== 店级广播展开 ====================

    /**
     * 展开店级广播行（target_openid 为空）：总部 server（task 模块）创建任务时不知道店长 openid，
     * 只入队"店级行"；本进程持有 employee 表，将店级行展开为店长/老板逐人行（status=0 供发送），
     * 原行标记 status=3（已展开，避免重复消费）。由 NotificationSenderJob 每轮开头调用。
     */
    @org.springframework.transaction.annotation.Transactional
    public void expandStoreBroadcasts(int limit) {
        List<NotificationLog> rows = notificationLogMapper.selectList(
                new LambdaQueryWrapper<NotificationLog>()
                        .isNull(NotificationLog::getTargetOpenid)
                        .eq(NotificationLog::getStatus, 0)
                        .last("LIMIT " + limit));
        if (rows.isEmpty()) return;
        for (NotificationLog row : rows) {
            if (!StringUtils.hasText(row.getStoreId())) {
                notificationLogMapper.update(null, new LambdaUpdateWrapper<NotificationLog>()
                        .eq(NotificationLog::getId, row.getId())
                        .set(NotificationLog::getStatus, 3));
                continue;
            }
            List<String> openids = resolveManagerOpenids(row.getStoreId());
            for (String openid : openids) {
                NotificationLog n = new NotificationLog();
                n.setEventType(row.getEventType());
                n.setStoreId(row.getStoreId());
                n.setTargetOpenid(openid);
                n.setTitle(row.getTitle());
                n.setContent(row.getContent());
                n.setPagePath(row.getPagePath());
                n.setSourceId(row.getSourceId());
                n.setStatus(0);
                notificationLogMapper.insert(n);
            }
            notificationLogMapper.update(null, new LambdaUpdateWrapper<NotificationLog>()
                    .eq(NotificationLog::getId, row.getId())
                    .set(NotificationLog::getStatus, 3));
        }
    }

    // ==================== 额度 ====================

    /** 授权成功上报：+1（首次建行）；并发首充冲突时改走累加 */
    public void plusQuota(String openid) {
        if (!StringUtils.hasText(openid)) return;
        if (addQuota(openid, 1)) return;
        NotificationQuota q = new NotificationQuota();
        q.setOpenid(openid);
        q.setQuota(1);
        q.setUpdatedAt(LocalDateTime.now());
        try {
            quotaMapper.insert(q);
        } catch (DuplicateKeyException e) {
            // 并发首充：行已被建，再累加一次
            addQuota(openid, 1);
        }
    }

    /** 发送前原子扣 1，余额不足返回 false（调用方降级站内，不再发微信） */
    public boolean tryConsumeQuota(String openid) {
        if (!StringUtils.hasText(openid)) return false;
        return addQuota(openid, -1);
    }

    /** 发送失败回补额度 */
    public void refundQuota(String openid) {
        addQuota(openid, 1);
    }

    private boolean addQuota(String openid, int delta) {
        try {
            LambdaUpdateWrapper<NotificationQuota> uw = new LambdaUpdateWrapper<NotificationQuota>()
                    .eq(NotificationQuota::getOpenid, openid)
                    .setSql("quota = quota + " + delta)
                    .set(NotificationQuota::getUpdatedAt, LocalDateTime.now());
            // 扣减（delta<0）：仅当余额 > 0 才允许扣（扣后最低到 0，不穿负，并发下防超扣）；
            // 累加（delta>0）：无条件（余额非负由扣侧保证）
            if (delta < 0) {
                uw.gt(NotificationQuota::getQuota, 0);
            }
            return quotaMapper.update(null, uw) > 0;
        } catch (Exception e) {
            log.error("额度更新失败 openid={} delta={}", openid, delta, e);
            return false;
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
