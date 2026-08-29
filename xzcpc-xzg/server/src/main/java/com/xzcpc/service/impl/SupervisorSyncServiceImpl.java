package com.xzcpc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xzcpc.entity.AdminPermission;
import com.xzcpc.mapper.AdminPermissionMapper;
import com.xzcpc.mp.entity.SupervisorStoreAccess;
import com.xzcpc.mp.mapper.SupervisorStoreAccessMapper;
import com.xzcpc.service.SupervisorSyncService;
import com.xzcpc.task.entity.Store;
import com.xzcpc.task.mapper.StoreMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 督导-门店关系同步实现。
 *
 * 流程：
 *  1. 读 sys_config（supervisor_api_url / supervisor_api_key）
 *  2. 拉新门店接口全量列表（X-API-Key 认证）
 *  3. 收集所有 supervisor.userId → 查 admin_permission.user_id 得到 open_id/name
 *  4. 门店按 code（S开头13位）对齐本地 store_info，取本地 store_id
 *  5. 逐店 upsert supervisor_store_access（source='auto'）+ 更新 store_info.supervisor_name
 *  6. 报告：未匹配督导、无督导门店、门店未对齐、名字不一致、失效 auto 行（不自动删）
 *
 * 安全策略：
 *  - 只重建 source='auto' 的行；手工维护行（督导领导→全部门店等）永不覆盖
 *  - 不删除任何行：接口已不存在的 auto 行只进报告，人工确认后再处理
 *  - apply=false（dry-run）时只拉取对账，不写库
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupervisorSyncServiceImpl implements SupervisorSyncService {

    private static final String CFG_URL = "supervisor_api_url";
    private static final String CFG_KEY = "supervisor_api_key";

    private final RestTemplate restTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final AdminPermissionMapper adminPermissionMapper;
    private final SupervisorStoreAccessMapper accessMapper;
    private final StoreMapper storeMapper;

    @Override
    public Map<String, Object> sync(boolean apply) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("apply", apply);

        // ---- 1. 配置 ----
        String apiUrl = getConfig(CFG_URL);
        String apiKey = getConfig(CFG_KEY);
        if (!StringUtils.hasText(apiUrl) || !StringUtils.hasText(apiKey)) {
            report.put("error", "sys_config 未配置 " + CFG_URL + " / " + CFG_KEY);
            return report;
        }

        // ---- 2. 拉接口 ----
        List<Map<String, Object>> items = fetchStores(apiUrl, apiKey);
        report.put("totalStores", items.size());
        if (items.isEmpty()) {
            report.put("error", "接口返回空门店列表");
            return report;
        }

        // ---- 3. 督导 userId → AdminPermission ----
        Set<String> userIds = new HashSet<>();
        for (Map<String, Object> item : items) {
            Map<String, Object> sup = asMap(item.get("supervisor"));
            if (sup != null && StringUtils.hasText(str(sup.get("userId")))) {
                userIds.add(str(sup.get("userId")));
            }
        }
        Map<String, AdminPermission> byUserId = new HashMap<>();
        if (!userIds.isEmpty()) {
            adminPermissionMapper.selectList(new LambdaQueryWrapper<AdminPermission>()
                            .in(AdminPermission::getUserId, userIds))
                    .forEach(ap -> byUserId.put(ap.getUserId(), ap));
        }
        report.put("interfaceSupervisors", userIds.size());
        report.put("matchedSupervisors", byUserId.size());

        // ---- 4. 门店 code → Store（本地 store_id） ----
        Map<String, Store> byCode = new HashMap<>();
        storeMapper.selectList(new LambdaQueryWrapper<Store>().eq(Store::getDelFlag, 0))
                .forEach(s -> {
                    if (StringUtils.hasText(s.getStoreCode())) {
                        byCode.putIfAbsent(s.getStoreCode(), s);
                    }
                });

        // ---- 5. 逐店处理 ----
        int matchedStores = 0, noSupervisor = 0, unmatchedByCode = 0, newStores = 0;
        List<String> unmatchedSupervisors = new ArrayList<>();   // userId 本地匹配不到
        List<String> nameMismatches = new ArrayList<>();         // displayName ≠ admin_permission.name
        List<String> newStoreIds = new ArrayList<>();            // 新增门店（store_code + 生成的 store_id，供人工维护）
        Set<String> currentAutoKeys = new HashSet<>();           // 本次接口确认的 (openId|storeId)

        for (Map<String, Object> item : items) {
            // 匹配键：接口 id（数字）↔ 本地 store_code（与企迈 code 同源）
            String code = str(item.get("id"));
            Store store = StringUtils.hasText(code) ? byCode.get(code) : null;
            boolean isNewStore = false;
            if (store == null) {
                if (!apply) {
                    unmatchedByCode++;
                    continue;
                }
                // 新增门店：store_id=接口id，store_name 用小象名称（仅新店如此，旧店不改名）
                store = createStoreFromItem(item);
                if (store == null) {
                    unmatchedByCode++;
                    continue;
                }
                isNewStore = true;
                newStores++;
                newStoreIds.add("code=" + code + " → store_id=" + store.getStoreId());
            }

            // 基础门店信息同步（xinfo 来源：小象名称/省/地市/县/地址，无论有无督导都更新；新店建表时已写入）
            if (apply && !isNewStore) {
                updateStoreBaseInfo(store, item);
            }

            Map<String, Object> sup = asMap(item.get("supervisor"));
            if (sup == null) {
                noSupervisor++;
                continue;
            }

            String userId = str(sup.get("userId"));
            AdminPermission ap = StringUtils.hasText(userId) ? byUserId.get(userId) : null;
            if (ap == null) {
                // userId 缺失（历史数据）或本地无此用户 → 报告待人工
                unmatchedSupervisors.add(str(sup.get("displayName")) + " / userId=" + userId);
                continue;
            }

            // 名字一致性校验（仅提醒）
            String displayName = str(sup.get("displayName"));
            if (StringUtils.hasText(displayName) && !displayName.equals(ap.getName())) {
                nameMismatches.add("接口名「" + displayName + "」≠ 本地名「" + ap.getName() + "」(openId=" + ap.getOpenId() + ")");
            }

            String key = ap.getOpenId() + "|" + store.getStoreId();
            currentAutoKeys.add(key);
            matchedStores++;

            if (apply) {
                upsertAccess(ap, store);
                updateStoreSupervisorName(store, ap.getName());
            }
        }
        report.put("matchedStores", matchedStores);
        report.put("noSupervisor", noSupervisor);
        report.put("unmatchedByCode", unmatchedByCode);
        report.put("newStores", newStores);
        report.put("newStoreIds", newStoreIds); // 新店 store_id 清单，供人工后续维护
        report.put("unmatchedSupervisors", unmatchedSupervisors);
        report.put("nameMismatches", nameMismatches);

        // ---- 6. 失效 auto 行（接口已不存在，不自动删，报告人工确认） ----
        List<String> staleAuto = new ArrayList<>();
        accessMapper.selectList(new LambdaQueryWrapper<SupervisorStoreAccess>()
                        .eq(SupervisorStoreAccess::getSource, "auto"))
                .forEach(a -> {
                    String k = a.getOpenId() + "|" + a.getStoreId();
                    if (!currentAutoKeys.contains(k)) {
                        staleAuto.add(a.getAdminName() + " → " + a.getStoreName()
                                + " (storeId=" + a.getStoreId() + ")");
                    }
                });
        report.put("staleAutoRows", staleAuto);

        log.info("督导同步完成：接口{}家 匹配{}家 无督导{}家 未对齐门店{} 未匹配督导{} 失效auto行{}",
                items.size(), matchedStores, noSupervisor, unmatchedByCode,
                unmatchedSupervisors.size(), staleAuto.size());
        return report;
    }

    // ==================== 写库 ====================

    @Transactional(rollbackFor = Exception.class)
    public void upsertAccess(AdminPermission ap, Store store) {
        SupervisorStoreAccess ex = accessMapper.selectOne(new LambdaQueryWrapper<SupervisorStoreAccess>()
                .eq(SupervisorStoreAccess::getOpenId, ap.getOpenId())
                .eq(SupervisorStoreAccess::getStoreId, store.getStoreId()));
        if (ex == null) {
            SupervisorStoreAccess a = new SupervisorStoreAccess();
            a.setOpenId(ap.getOpenId());
            a.setAdminName(ap.getName());
            a.setStoreId(store.getStoreId());
            a.setStoreName(store.getStoreName());
            a.setSource("auto");
            accessMapper.insert(a);
        } else {
            ex.setAdminName(ap.getName());
            ex.setStoreName(store.getStoreName());
            ex.setSource("auto");
            accessMapper.updateById(ex);
        }
    }

    private void updateStoreSupervisorName(Store store, String supervisorName) {
        if (Objects.equals(store.getSupervisorName(), supervisorName)) {
            return;
        }
        store.setSupervisorName(supervisorName);
        store.setUpdatedAt(LocalDateTime.now());
        storeMapper.updateById(store);
    }

    /** 同步 xinfo 基础门店字段（小象名称/省/地市/县/地址），有变化才写库 */
    private void updateStoreBaseInfo(Store store, Map<String, Object> item) {
        if (applyBaseInfo(store, item)) {
            store.setUpdatedAt(LocalDateTime.now());
            storeMapper.updateById(store);
        }
    }

    /** 填充 xinfo 基础字段（小象名称/省/地市/县/地址），返回是否有变化；不动 store_name（旧店不改名） */
    private boolean applyBaseInfo(Store store, Map<String, Object> item) {
        boolean changed = false;
        String xinfoName = str(item.get("name"));
        if (!Objects.equals(store.getXinfoStoreName(), xinfoName)) {
            store.setXinfoStoreName(xinfoName);
            changed = true;
        }
        String province = str(item.get("province"));
        if (!Objects.equals(store.getProvince(), province)) {
            store.setProvince(province);
            changed = true;
        }
        String city = str(item.get("city"));
        if (!Objects.equals(store.getCity(), city)) {
            store.setCity(city);
            changed = true;
        }
        String district = str(item.get("district"));
        if (!Objects.equals(store.getDistrict(), district)) {
            store.setDistrict(district);
            changed = true;
        }
        String address = str(item.get("address"));
        if (!Objects.equals(store.getAddress(), address)) {
            store.setAddress(address);
            changed = true;
        }
        return changed;
    }

    /**
     * 新增门店（仅 apply 时调用）。
     * 匹配键：接口 id（数字）↔ 本地 store_code（与企迈 code 同源）。
     * store_code=接口id，store_name 用小象名称（新增才用，旧店不改名），
     * 同时写入 xinfo 基础字段；若接口 id 已存在（store_code 已占）则只补基础字段。
     */
    private Store createStoreFromItem(Map<String, Object> item) {
        String code = str(item.get("id"));
        if (!StringUtils.hasText(code)) {
            return null;
        }
        // 防重复：store_code 已存在（并发/历史残留）
        Store existing = storeMapper.selectOne(new LambdaQueryWrapper<Store>()
                .eq(Store::getStoreCode, code));
        if (existing != null) {
            if (applyBaseInfo(existing, item)) {
                existing.setUpdatedAt(LocalDateTime.now());
                storeMapper.updateById(existing);
            }
            return existing;
        }
        Store s = new Store();
        s.setStoreCode(code);
        s.setStoreId(genStoreId()); // store_id 本地自定义生成（cm 开头企迈格式），非接口采集
        s.setStoreName(str(item.get("name"))); // 新增门店用小象名称
        applyBaseInfo(s, item);
        s.setCreatedAt(LocalDateTime.now());
        s.setUpdatedAt(LocalDateTime.now());
        storeMapper.insert(s);
        return s;
    }

    /** 生成 store_id：cm + 24 位小写字母数字（企迈格式），本地自定义维护，查重保证唯一 */
    private String genStoreId() {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        Random random = new Random();
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder("cm");
            for (int i = 0; i < 24; i++) {
                sb.append(chars.charAt(random.nextInt(chars.length())));
            }
            String candidate = sb.toString();
            Long cnt = storeMapper.selectCount(new LambdaQueryWrapper<Store>()
                    .eq(Store::getStoreId, candidate));
            if (cnt == null || cnt == 0) {
                return candidate;
            }
        }
        // 极低概率全碰撞，兜底加时间戳
        return "cm" + System.currentTimeMillis() + random.nextInt(9999);
    }

    // ==================== 接口拉取 ====================

    private List<Map<String, Object>> fetchStores(String apiUrl, String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", apiKey);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                apiUrl, HttpMethod.GET, entity,
                new ParameterizedTypeReference<Map<String, Object>>() {});
        Map<String, Object> body = resp.getBody();
        if (body == null) {
            return List.of();
        }
        Object items = body.get("items");
        if (!(items instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) {
                out.add(asMap(m));
            }
        }
        return out;
    }

    // ==================== 工具 ====================

    private String getConfig(String key) {
        try {
            String val = jdbcTemplate.queryForObject(
                    "SELECT config_value FROM sys_config WHERE config_key=? LIMIT 1", String.class, key);
            return val != null ? val.trim() : "";
        } catch (Exception e) {
            return "";
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        if (o instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Map.of();
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }
}
