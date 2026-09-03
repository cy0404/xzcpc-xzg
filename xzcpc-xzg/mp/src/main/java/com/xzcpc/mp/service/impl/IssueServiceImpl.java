package com.xzcpc.mp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.common.context.AdminContextHolder;
import com.xzcpc.common.context.AdminUser;
import com.xzcpc.common.exception.BusinessException;
import com.xzcpc.common.service.StoreAccessService;
import com.xzcpc.common.util.BizCodeUtil;
import com.xzcpc.mp.context.UserContextHolder;
import com.xzcpc.mp.dto.IssueCreateReq;
import com.xzcpc.mp.entity.Issue;
import com.xzcpc.mp.mapper.IssueMapper;
import com.xzcpc.mp.service.IssueService;
import com.xzcpc.mp.service.MpStaffService;
import com.xzcpc.mp.service.NotificationService;
import com.xzcpc.mp.service.XiangmuSyncService;
import com.xzcpc.task.entity.Store;
import com.xzcpc.task.mapper.StoreMapper;
import com.xzcpc.task.service.StoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IssueServiceImpl implements IssueService {

    private final IssueMapper issueMapper;
    private final XiangmuSyncService xiangmuSyncService;
    private final MpStaffService staffService;
    private final StoreService storeService;
    private final RestTemplate restTemplate;
    private final StoreAccessService storeAccessService;
    private final StoreMapper storeMapper;
    private final NotificationService notificationService;

    @Value("${xiangmu.base-url:http://127.0.0.1:9000}")
    private String xiangmuBaseUrl;

    @Value("${xiangmu.api-key:}")
    private String xiangmuApiKey;

    @Value("${xiangmu.records-api-key:}")
    private String recordsApiKey;

    @Override
    @Transactional
    public Issue create(IssueCreateReq req) {
        var user = UserContextHolder.get();

        Issue issue = new Issue();
        issue.setBizCode(BizCodeUtil.of("ISS"));
        issue.setStoreId(user.getStoreId());
        issue.setStoreName(user.getStoreName());
        issue.setTitle(req.getTitle());
        issue.setIssueType(req.getIssueType());
        issue.setSubType(req.getSubType());
        issue.setUrgency(req.getUrgency());
        issue.setDescription(req.getDescription());
        issue.setSubmitterOpenid(user.getOpenid());
        issue.setStatus("pending");
        issue.setSyncStatus("pending");
        issue.setSource("MINI_PROGRAM");
        issue.setCreatedAt(LocalDateTime.now());
        issue.setUpdatedAt(LocalDateTime.now());
        issueMapper.insert(issue);

        // 推送象目经理外部系统（未就绪时 Stub 返回 null，问题保留、状态待同步）
        String xiangmuId = xiangmuSyncService.pushIssue(issue);
        if (StringUtils.hasText(xiangmuId)) {
            issue.setXiangmuId(xiangmuId);
            issue.setSyncStatus("synced");
            issue.setUpdatedAt(LocalDateTime.now());
            issueMapper.updateById(issue);
        }
        return issue;
    }

    @Override
    public Page<Issue> pageByStore(String storeId, String status, int pageNum, int pageSize) {
        var qw = new LambdaQueryWrapper<Issue>()
                .eq(Issue::getStoreId, storeId)
                .orderByDesc(Issue::getCreatedAt);
        if (StringUtils.hasText(status)) {
            qw.eq(Issue::getStatus, status);
        }
        return issueMapper.selectPage(new Page<>(pageNum, pageSize), qw);
    }

    @Override
    public Page<Issue> pageByStores(String openid, String status, int pageNum, int pageSize) {
        List<Map<String, Object>> stores = staffService.findStoresByOpenid(openid);
        List<String> storeIds = stores.stream()
                .map(s -> (String) s.get("storeId")).filter(Objects::nonNull).toList();
        if (storeIds.isEmpty()) {
            return new Page<>(pageNum, pageSize);
        }
        var qw = new LambdaQueryWrapper<Issue>()
                .in(Issue::getStoreId, storeIds)
                .orderByDesc(Issue::getCreatedAt);
        if (StringUtils.hasText(status)) {
            qw.eq(Issue::getStatus, status);
        }
        return issueMapper.selectPage(new Page<>(pageNum, pageSize), qw);
    }

    @Override
    public Map<String, Long> overview(String storeId) {
        return countOverview(List.of(storeId));
    }

    @Override
    public List<Map<String, Object>> overviewByStores(String openid) {
        List<Map<String, Object>> stores = staffService.findStoresByOpenid(openid);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> s : stores) {
            String sid = (String) s.get("storeId");
            String sname = (String) s.get("storeName");
            Long count = issueMapper.selectCount(new LambdaQueryWrapper<Issue>()
                    .eq(Issue::getStoreId, sid)
                    .eq(Issue::getStatus, "PENDING_ACCEPTANCE"));
            if (count != null && count > 0) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("storeId", sid);
                item.put("storeName", sname);
                item.put("pending", count);
                result.add(item);
            }
        }
        return result;
    }

    @Override
    public Map<String, Long> overviewTotal(String openid) {
        List<Map<String, Object>> stores = staffService.findStoresByOpenid(openid);
        List<String> storeIds = stores.stream()
                .map(s -> (String) s.get("storeId")).filter(Objects::nonNull).toList();
        if (storeIds.isEmpty()) {
            Map<String, Long> empty = new LinkedHashMap<>();
            empty.put("processing", 0L); empty.put("pendingAcceptance", 0L);
            empty.put("resolved", 0L); empty.put("all", 0L);
            return empty;
        }
        return countOverview(storeIds);
    }

    private Map<String, Long> countOverview(List<String> storeIds) {
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("processing", countByStores(storeIds, "IN_PROGRESS"));
        result.put("pendingAcceptance", countByStores(storeIds, "PENDING_ACCEPTANCE"));
        result.put("resolved", countByStores(storeIds, "CLOSED") + countByStores(storeIds, "RESOLVED"));
        Long all = issueMapper.selectCount(
                new LambdaQueryWrapper<Issue>().in(Issue::getStoreId, storeIds));
        result.put("all", all != null ? all : 0L);
        return result;
    }

    private Long countByStores(Collection<String> storeIds, String status) {
        Long c = issueMapper.selectCount(new LambdaQueryWrapper<Issue>()
                .in(Issue::getStoreId, storeIds)
                .eq(Issue::getStatus, status));
        return c != null ? c : 0L;
    }

    @Override
    public Issue detail(Long id, String storeId) {
        Issue issue = issueMapper.selectById(id);
        if (issue == null) {
            throw new BusinessException("问题不存在");
        }
        if (storeId != null && !storeId.equals(issue.getStoreId())) {
            throw new BusinessException(403, "无权查看该问题");
        }
        return issue;
    }

    @Override
    @Transactional
    public Issue accept(Long id, String remark) {
        var user = UserContextHolder.get();
        Issue issue = detail(id, user.getStoreId());
        if (!"PENDING_ACCEPTANCE".equals(issue.getStatus())) {
            throw new BusinessException("仅待验收的问题可验收");
        }
        issue.setStatus("CLOSED");
        issue.setReplyText(remark);
        issue.setUpdatedAt(LocalDateTime.now());
        issueMapper.updateById(issue);
        return issue;
    }

    @Override
    public Issue acceptFromFeishu(Long id) {
        Issue issue = issueMapper.selectById(id);
        if (issue == null || !"PENDING_ACCEPTANCE".equalsIgnoreCase(issue.getStatus())) {
            throw new BusinessException("仅待验收的问题可验收");
        }
        issue.setStatus("CLOSED");
        issue.setReplyText("飞书卡片验收");
        issue.setUpdatedAt(LocalDateTime.now());
        issueMapper.updateById(issue);
        log.info("[issue] 飞书卡片验收通过：id={} bizCode={}", id, issue.getBizCode());
        return issue;
    }

    @Override
    public Page<Issue> pageAll(String storeId, String supervisorName, String issueType, String urgency, String status,
                               String keyword, String startDate, String endDate, String source, int pageNum, int pageSize) {
        var qw = new LambdaQueryWrapper<Issue>()
                .orderByDesc(Issue::getCreatedAt);

        // 督导角色：按可访问门店过滤
        AdminUser admin = AdminContextHolder.get();
        if (admin != null && storeAccessService.isSupervisorOnly(admin)) {
            List<String> accessibleStoreIds = storeAccessService.getAccessibleStoreIds(admin.getOpenId());
            if (accessibleStoreIds.isEmpty()) {
                return new Page<>(pageNum, pageSize);
            }
            qw.in(Issue::getStoreId, accessibleStoreIds);
        }

        // 按指定督导过滤
        if (StringUtils.hasText(supervisorName)) {
            List<String> supervisorStoreIds = storeAccessService.getAccessibleStoreIdsBySupervisorName(supervisorName);
            if (supervisorStoreIds.isEmpty()) {
                return new Page<>(pageNum, pageSize);
            }
            qw.in(Issue::getStoreId, supervisorStoreIds);
        }

        if (StringUtils.hasText(storeId)) qw.eq(Issue::getStoreId, storeId);
        if (StringUtils.hasText(issueType)) qw.eq(Issue::getIssueType, issueType);
        if (StringUtils.hasText(urgency)) qw.eq(Issue::getUrgency, urgency);
        if (StringUtils.hasText(status)) qw.eq(Issue::getStatus, status);
        if (StringUtils.hasText(source)) qw.eq(Issue::getSource, source);
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(Issue::getTitle, keyword)
                    .or().like(Issue::getBizCode, keyword)
                    .or().like(Issue::getStoreName, keyword));
        }
        if (StringUtils.hasText(startDate)) {
            qw.ge(Issue::getCreatedAt, LocalDate.parse(startDate).atStartOfDay());
        }
        if (StringUtils.hasText(endDate)) {
            qw.le(Issue::getCreatedAt, LocalDate.parse(endDate).plusDays(1).atStartOfDay());
        }
        Page<Issue> result = issueMapper.selectPage(new Page<>(pageNum, pageSize), qw);

        // 批量填充督导姓名
        List<Issue> records = result.getRecords();
        if (!records.isEmpty()) {
            Set<String> storeIds = records.stream().map(Issue::getStoreId).filter(id -> id != null).collect(Collectors.toSet());
            if (!storeIds.isEmpty()) {
                Map<String, String> supervisorMap = storeMapper.selectList(
                        new LambdaQueryWrapper<Store>().in(Store::getStoreId, storeIds))
                        .stream().filter(s -> s.getSupervisorName() != null)
                        .collect(Collectors.toMap(Store::getStoreId, Store::getSupervisorName, (a, b) -> a));
                records.forEach(r -> r.setSupervisorName(supervisorMap.getOrDefault(r.getStoreId(), "")));
            }
        }

        return result;
    }

    @Override
    public Issue detailForAdmin(Long id) {
        return detail(id, null);
    }

    @Override
    @Transactional
    public Issue applyExternalStatus(Long id, String extStatus, String processResult) {
        Issue issue = detail(id, null);
        if (!StringUtils.hasText(extStatus)) {
            throw new BusinessException("缺少状态值");
        }
        issue.setStatus(extStatus);
        issue.setSyncStatus("synced");
        issue.setUpdatedAt(LocalDateTime.now());
        updateWithRetry(issue);
        notifySubmitterOnStatus(issue, extStatus);
        return issue;
    }

    @Override
    @Transactional
    public Issue syncExternal(Long externalId, String issueNo, String externalStatus) {
        var user = UserContextHolder.get();
        Issue issue = new Issue();
        issue.setBizCode(issueNo != null ? issueNo : BizCodeUtil.of("ISS"));
        issue.setStoreId(user.getStoreId());
        issue.setStoreName(user.getStoreName());
        issue.setTitle(issueNo != null ? "问题 #" + issueNo : "门店问题反馈");
        issue.setStatus(StringUtils.hasText(externalStatus) ? externalStatus : "PENDING_CONFIRMATION");
        issue.setXiangmuId(String.valueOf(externalId));
        issue.setSyncStatus("synced");
        issue.setUrgency("normal");
        issue.setIssueType("");
        issue.setDescription("");
        issue.setCreatedAt(LocalDateTime.now());
        issue.setUpdatedAt(LocalDateTime.now());
        issueMapper.insert(issue);
        return issue;
    }

    @Override
    @Transactional
    public Issue syncByCallback(String chatId, Long externalId, String issueNo, String externalStatus,
                                String title, String storeName, String issueType, String severity, String handler,
                                String equipment, String source) {
        String xiangmuId = String.valueOf(externalId);
        Issue existing = issueMapper.selectOne(new LambdaQueryWrapper<Issue>()
                .eq(Issue::getXiangmuId, xiangmuId));
        // 纯状态更新（无 title/issueNo）且找不到 externalId → 跳过
        boolean isCreation = StringUtils.hasText(title) || StringUtils.hasText(issueNo);
        if (existing == null && !isCreation) {
            log.info("[issue] callback externalId={} not found (status-only), skip", externalId);
            return null;
        }
        if (existing != null) {
            existing.setStatus(StringUtils.hasText(externalStatus) ? externalStatus : existing.getStatus());
            if (StringUtils.hasText(title)) existing.setTitle(title);
            if (StringUtils.hasText(storeName)) existing.setStoreName(storeName);
            if (StringUtils.hasText(issueNo)) existing.setBizCode(issueNo);
            if (StringUtils.hasText(issueType)) existing.setIssueType(issueType);
            if (StringUtils.hasText(severity)) existing.setUrgency(severity);
            if (StringUtils.hasText(handler)) existing.setProcessedBy(handler);
            if (StringUtils.hasText(equipment)) existing.setSubType(equipment);
            if (StringUtils.hasText(source)) existing.setSource(source);
            existing.setSyncStatus("synced");
            existing.setUpdatedAt(LocalDateTime.now());
            updateWithRetry(existing);
            notifySubmitterOnStatus(existing, existing.getStatus());
            return existing;
        }
        // 新问题 → 插入
        Store store = storeService.getStoreByChatId(chatId);
        if (store == null) {
            log.warn("[issue] callback with unknown chatId: {}", chatId);
            throw new BusinessException("未找到对应门店，chatId=" + chatId);
        }
        Issue issue = new Issue();
        issue.setBizCode(issueNo != null ? issueNo : BizCodeUtil.of("ISS"));
        issue.setStoreId(store.getStoreId());
        issue.setStoreName(StringUtils.hasText(storeName) ? storeName : store.getStoreName());
        issue.setTitle(StringUtils.hasText(title) ? title : (issueNo != null ? "问题 #" + issueNo : "门店问题反馈"));
        issue.setStatus(StringUtils.hasText(externalStatus) ? externalStatus : "PENDING_CONFIRMATION");
        issue.setXiangmuId(xiangmuId);
        issue.setSyncStatus("synced");
        issue.setIssueType(StringUtils.hasText(issueType) ? issueType : "");
        issue.setUrgency(StringUtils.hasText(severity) ? severity : "normal");
        issue.setProcessedBy(StringUtils.hasText(handler) ? handler : "");
        issue.setSubType(StringUtils.hasText(equipment) ? equipment : "");
        issue.setSource(StringUtils.hasText(source) ? source : null);
        issue.setDescription("");
        issue.setCreatedAt(LocalDateTime.now());
        issue.setUpdatedAt(LocalDateTime.now());
        issueMapper.insert(issue);
        return issue;
    }

    @Override
    public Issue findByExternalId(Long externalId) {
        Issue issue = issueMapper.selectOne(new LambdaQueryWrapper<Issue>()
                .eq(Issue::getXiangmuId, String.valueOf(externalId)));
        if (issue == null) {
            throw new BusinessException("问题不存在，externalId=" + externalId);
        }
        return issue;
    }

    @Override
    public void saveRecords(Long externalId, String recordsJson) {
        Issue issue = issueMapper.selectOne(new LambdaQueryWrapper<Issue>()
                .eq(Issue::getXiangmuId, String.valueOf(externalId)));
        if (issue == null) {
            log.warn("[issue] saveRecords: issue not found for externalId={}", externalId);
            return;
        }
        // 只更新 records_data + updated_at：不更新整个实体 → 不触发乐观锁、不写回旧字段，
        // 与状态回调并发时不会占掉 version 位，也不覆盖对方刚写入的状态
        issueMapper.update(null,
                new LambdaUpdateWrapper<Issue>()
                        .eq(Issue::getId, issue.getId())
                        .set(Issue::getRecordsData, recordsJson)
                        .set(Issue::getUpdatedAt, LocalDateTime.now()));
    }

    /**
     * updateById 乐观锁冲突时静默影响 0 行（并发回调场景），重读最新 version 重试，最多 3 次。
     */
    private void updateWithRetry(Issue issue) {
        for (int i = 0; i < 3; i++) {
            if (issueMapper.updateById(issue) > 0) {
                return;
            }
            Issue fresh = issueMapper.selectById(issue.getId());
            if (fresh == null) {
                return;
            }
            issue.setVersion(fresh.getVersion());
        }
        log.warn("[issue] updateWithRetry failed after 3 attempts, id={}", issue.getId());
    }

    /**
     * 订阅消息：问题状态推进到 待联系(PENDING_CONTACT)/待验收(PENDING_ACCEPTANCE) 时，通知问题提交人。
     * 两个状态是需要提交人配合的回环状态（联系补上/请验收）；仅小程序上报的问题存有提交人 openid。
     */
    private void notifySubmitterOnStatus(Issue issue, String newStatus) {
        if (issue == null || !StringUtils.hasText(newStatus)
                || !StringUtils.hasText(issue.getSubmitterOpenid())) {
            return;
        }
        boolean acceptance = newStatus.equalsIgnoreCase("PENDING_ACCEPTANCE");
        boolean contact = newStatus.equalsIgnoreCase("PENDING_CONTACT");
        if (!acceptance && !contact) return;
        String label = acceptance ? "请验收" : "需联系补上";
        notificationService.enqueue(issue.getSubmitterOpenid(), issue.getStoreId(),
                acceptance ? "ISSUE_PENDING_ACCEPTANCE" : "ISSUE_PENDING_CONTACT",
                "【问题】您上报的问题" + label,
                "请打开小程序查看问题详情并处理",
                String.valueOf(issue.getId()),
                "/pages/issue/detail/index?id=" + issue.getId());
    }

    @Override
    public Map<String, Object> getExternalRecords(Long issueId) {
        Issue issue = issueMapper.selectById(issueId);
        if (issue == null || !StringUtils.hasText(issue.getXiangmuId())) {
            throw new BusinessException("未关联外部问题单");
        }
        String url = xiangmuBaseUrl + "/api/mini-program/issue/" + issue.getXiangmuId() + "/records";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Api-Key", recordsApiKey);
            ResponseEntity<Map> resp = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> body = resp.getBody();
                Map<String, Object> data = (Map<String, Object>) body.getOrDefault("data", Map.of());
                // 相对路径补全为绝对 URL（如 /uploads/xxx → http://IP:9000/uploads/xxx）
                prefixRelativeUrls(data, xiangmuBaseUrl, "solutionPhotos");
                prefixRelativeUrls(data, xiangmuBaseUrl, "records", "mediaUrls");
                prefixRelativeUrls(data, xiangmuBaseUrl, "records", "photos");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> replies = (List<Map<String, Object>>) data.get("replies");
                if (replies != null) {
                    for (Map<String, Object> reply : replies) {
                        prefixRelativeUrlsInField(reply, xiangmuBaseUrl, "mediaUrls");
                    }
                }
                return data;
            }
        } catch (Exception e) {
            log.warn("[issue] failed to fetch external records for issue={}", issue.getBizCode(), e);
        }
        return Map.of("records", List.of(), "solutionPhotos", List.of(), "replies", List.of());
    }

    @Override
    public Map<String, Object> replyExternal(Long issueId, String replyText, String mediaUrls) {
        Issue issue = issueMapper.selectById(issueId);
        if (issue == null || !StringUtils.hasText(issue.getXiangmuId())) {
            throw new BusinessException("未关联外部问题单");
        }
        String url = xiangmuBaseUrl + "/api/mini-program/issue/" + issue.getXiangmuId() + "/reply";
        Map<String, String> body = new LinkedHashMap<>();
        body.put("replyText", replyText);
        if (StringUtils.hasText(mediaUrls)) {
            body.put("mediaUrls", mediaUrls);
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Api-Key", recordsApiKey);
            ResponseEntity<Map> resp = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> respBody = resp.getBody();

                // 将门店回复的"未解决原因"回写到 reply_text 字段
                issue.setReplyText(replyText);
                issue.setUpdatedAt(LocalDateTime.now());
                issueMapper.updateById(issue);

                return (Map<String, Object>) respBody.getOrDefault("data", Map.of());
            }
        } catch (Exception e) {
            log.error("[issue] failed to reply external issue={}", issue.getBizCode(), e);
            throw new BusinessException("回复失败，请稍后重试");
        }
        return Map.of();
    }

    private void prefixRelativeUrls(Map<String, Object> data, String baseUrl, String field) {
        Object val = data.get(field);
        if (val == null) return;
        if (val instanceof List<?> list) {
            List<String> fixed = new java.util.ArrayList<>();
            for (Object item : list) {
                if (item instanceof String s && s.startsWith("/")) fixed.add(baseUrl + s);
                else if (item != null) fixed.add(String.valueOf(item));
            }
            data.put(field, fixed);
        } else if (val instanceof String s && org.springframework.util.StringUtils.hasText(s)) {
            data.put(field, prefixRelativeStr(s, baseUrl));
        }
    }
    private void prefixRelativeUrls(Map<String, Object> data, String baseUrl, String listField, String field) {
        Object list = data.get(listField);
        if (!(list instanceof List<?> items)) return;
        for (Object item : items) {
            if (item instanceof Map<?, ?> m) {
                Object val = m.get(field);
                if (val == null) continue;
                if (val instanceof List<?> innerList) {
                    List<String> fixed = new ArrayList<>();
                    for (Object li : innerList) {
                        if (li instanceof String s) {
                            if (s.startsWith("/")) fixed.add(baseUrl + s);
                            else if (s.startsWith("http://")) fixed.add(s.replaceFirst("https?://[^/]+", baseUrl.replaceFirst("https?://", "https://")));
                            else fixed.add(s);
                        } else if (li != null) fixed.add(String.valueOf(li));
                        else if (li != null) fixed.add(String.valueOf(li));
                    }
                    ((Map<String, Object>) m).put(field, fixed);
                } else if (val instanceof String s && org.springframework.util.StringUtils.hasText(s)) {
                    ((Map<String, Object>) m).put(field, prefixRelativeStr(s, baseUrl));
                }
            }
        }
    }
    private void prefixRelativeUrlsInField(Map<String, Object> data, String baseUrl, String field) {
        Object val = data.get(field);
        if (val == null) return;
        if (val instanceof List<?> list) {
            List<String> fixed = new java.util.ArrayList<>();
            for (Object item : list) {
                if (item instanceof String s && s.startsWith("/")) fixed.add(baseUrl + s);
                else if (item != null) fixed.add(String.valueOf(item));
            }
            data.put(field, fixed);
        } else if (val instanceof String s && org.springframework.util.StringUtils.hasText(s)) {
            data.put(field, prefixRelativeStr(s, baseUrl));
        }
    }
    private String prefixRelativeStr(String urls, String baseUrl) {
        String[] parts = urls.split(",");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String u = parts[i].trim();
            if (!u.isEmpty()) {
                if (u.startsWith("/")) {
                    u = baseUrl + u;
                } else if (u.startsWith("http://")) {
                    // 替换为 HTTPS 域名
                    u = u.replaceFirst("https?://[^/]+", baseUrl.replaceFirst("https?://", "https://"));
                }
            }
            if (i > 0) sb.append(",");
            sb.append(u);
        }
        return sb.toString();
    }

}
