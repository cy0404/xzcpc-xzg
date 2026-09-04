package com.xzcpc.common.feishu;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 飞书消息发送工具类 — 统一管理 token、卡片发送、分类分组逻辑。
 * 替代 LossReportDailySummaryJob / LossReportMonthlyVoucherJob / LossReportH5Controller 中的重复代码。
 */
@Slf4j
@Service
public class FeishuMessageService {

    private final JdbcTemplate jdbcTemplate;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${feishu.app-id:}")
    private String appId;
    @Value("${feishu.app-secret:}")
    private String appSecret;
    @Value("${app.server-url:http://localhost:4026}")
    private String serverUrl;
    /** 本地测试固定群（未验收问题提醒），配置后不按门店分组、只发该群 */
    @Value("${feishu.issue-reminder-test-chat-id:}")
    private String issueReminderTestChatId;

    /** token 简单缓存 */
    private volatile String cachedToken;
    private volatile LocalDateTime tokenExpireAt;

    public FeishuMessageService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    // ==================== Token ====================

    @SuppressWarnings("rawtypes")
    public synchronized String getTenantToken() {
        if (cachedToken != null && tokenExpireAt != null && LocalDateTime.now().isBefore(tokenExpireAt)) {
            return cachedToken;
        }
        if (appId == null || appId.isBlank()) return null;
        try {
            Map<String, String> req = Map.of("app_id", appId, "app_secret", appSecret);
            ResponseEntity<Map> resp = restTemplate.postForEntity(
                    "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal", req, Map.class);
            Map<?, ?> body = resp.getBody();
            if (body != null && "ok".equals(String.valueOf(body.get("msg")))) {
                cachedToken = (String) body.get("tenant_access_token");
                Integer expire = (Integer) body.get("expire");
                tokenExpireAt = LocalDateTime.now().plusSeconds(expire != null ? expire - 120 : 7000);
                return cachedToken;
            }
        } catch (Exception e) {
            log.error("获取飞书 tenant token 失败", e);
        }
        return null;
    }

    // ==================== H5 免登（操作人身份采集） ====================

    /**
     * 飞书 H5 免登 code 换 open_id（审核页采集"谁点的拒绝"，识别操作人）：
     * app_access_token(internal) → authen/v1/access_token 换 user_access_token → user_info 取 open_id。
     * 外部联系人（无本租户账号）/飞书外浏览器/任一环节失败 → 返回 null，调用方回落默认身份（"厂家"）。
     */
    @SuppressWarnings("rawtypes")
    public String exchangeCodeOpenId(String code) {
        if (appId == null || appId.isBlank() || code == null || code.isBlank()) return null;
        try {
            Map<String, String> appReq = Map.of("app_id", appId, "app_secret", appSecret);
            ResponseEntity<Map> appResp = restTemplate.postForEntity(
                    "https://open.feishu.cn/open-apis/auth/v3/app_access_token/internal", appReq, Map.class);
            Map<?, ?> appBody = appResp.getBody();
            if (appBody == null || !"ok".equals(String.valueOf(appBody.get("msg")))) return null;
            String appToken = (String) appBody.get("app_access_token");
            if (appToken == null || appToken.isEmpty()) return null;

            HttpHeaders authHeaders = new HttpHeaders();
            authHeaders.setBearerAuth(appToken);
            HttpEntity<Map<String, String>> authEntity = new HttpEntity<>(
                    Map.of("grant_type", "authorization_code", "code", code), authHeaders);
            ResponseEntity<Map> authResp = restTemplate.postForEntity(
                    "https://open.feishu.cn/open-apis/authen/v1/access_token", authEntity, Map.class);
            Map<?, ?> authData = authResp.getBody() == null ? null : (Map<?, ?>) authResp.getBody().get("data");
            String userToken = authData == null ? null : (String) authData.get("access_token");
            if (userToken == null || userToken.isEmpty()) return null;

            HttpHeaders userHeaders = new HttpHeaders();
            userHeaders.setBearerAuth(userToken);
            HttpEntity<Void> userEntity = new HttpEntity<>(userHeaders);
            ResponseEntity<Map> userResp = restTemplate.exchange(
                    "https://open.feishu.cn/open-apis/authen/v1/user_info", HttpMethod.GET, userEntity, Map.class);
            Map<?, ?> userData = userResp.getBody() == null ? null : (Map<?, ?>) userResp.getBody().get("data");
            String openId = userData == null ? null : (String) userData.get("open_id");
            if (openId == null || openId.isBlank()) return null;
            return openId;
        } catch (Exception e) {
            log.warn("H5 免登换 open_id 失败（回落默认身份）", e);
            return null;
        }
    }

    // ==================== 发送 ====================

    /** 发送交互卡片给个人（open_id），支持逗号分隔多人；返回最后一条成功消息的 message_id */
    public String sendToUser(String token, String userId, Map<String, Object> card) {
        String lastId = null;
        if (userId == null || userId.isEmpty()) return null;
        for (String uid : userId.split(",")) {
            String u = uid.trim();
            if (!u.isEmpty()) lastId = sendMessage(token, u, "open_id", card);
        }
        return lastId;
    }

    /** 发送交互卡片给群（chat_id）；返回消息 message_id（失败返回 null，可用于查询已读）。
     *  兼容配置值带 chat_ 前缀（如 sendToCardTargets 的群标记），发送前剥掉，避免把 chat_oc_xxx 当 chat_id 传给飞书 */
    public String sendToChat(String token, String chatId, Map<String, Object> card) {
        String cid = chatId;
        if (cid.startsWith("chat_")) cid = cid.substring("chat_".length());
        return sendMessage(token, cid, "chat_id", card);
    }

    /**
     * 按配置收件人发送卡片（逗号分隔，支持混合）：以 chat_ 开头的值视为群 chat_id（发群），
     * 否则视为个人 open_id（发个人）。用于外部厂家等场景：把配置行的 feishu_user_id
     * 改成 chat_<chat_id> 即可把该卡片改为发群，无需改代码。
     */
    public String sendToCardTargets(String token, String targets, Map<String, Object> card) {
        String lastId = null;
        if (targets == null || targets.isEmpty()) return null;
        for (String t : targets.split(",")) {
            String s = t.trim();
            if (s.isEmpty()) continue;
            lastId = s.startsWith("chat_") ? sendToChat(token, s, card) : sendToUser(token, s, card);
        }
        return lastId;
    }

    private String sendMessage(String token, String receiveId, String idType, Map<String, Object> card) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("config", Map.of("wide_screen_mode", true));
        content.put("header", card.get("header"));
        content.put("elements", card.get("elements"));
        String json;
        try {
            json = objectMapper.writeValueAsString(content);
        } catch (JsonProcessingException e) {
            log.error("卡片序列化失败", e);
            return null;
        }
        log.info("飞书卡片发送 target={} idType={} json={}", receiveId, idType, json);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("receive_id", receiveId);
        body.put("msg_type", "interactive");
        body.put("content", json);
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        try {
            ResponseEntity<String> resp = restTemplate.exchange(
                    "https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=" + idType,
                    HttpMethod.POST, new HttpEntity<>(body, h), String.class);
            // 飞书业务错误（如 230002 机器人不在群）是 HTTP 200 + body code != 0，不检查会静默失败
            if (resp.getBody() != null && !resp.getBody().isBlank()) {
                try {
                    Map<?, ?> rb = objectMapper.readValue(resp.getBody(), Map.class);
                    Object code = rb.get("code");
                    if (code != null && !"0".equals(String.valueOf(code))) {
                        log.warn("飞书卡片发送失败 target={} idType={} code={} msg={}",
                                receiveId, idType, code, rb.get("msg"));
                        return null;
                    }
                    // 成功：取 message_id 供后续查询已读
                    Object data = rb.get("data");
                    if (data instanceof Map<?, ?> dm) {
                        Object mid = dm.get("message_id");
                        if (mid != null) return String.valueOf(mid);
                    }
                } catch (Exception ignored) {
                    // 响应体不是 JSON 时忽略，HTTP 异常已由外层 catch 兜底
                }
            }
        } catch (Exception e) {
            log.error("飞书卡片发送失败 target={}", receiveId, e);
        }
        return null;
    }

    // ==================== 已读回执 ====================

    /** 查询机器人自己发送的消息的已读用户列表（7 天内有效；仅返回已读用户） */
    public List<Map<String, Object>> getReadUsers(String token, String messageId) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (messageId == null || messageId.isEmpty()) return out;
        String pageToken = "";
        for (int page = 0; page < 10; page++) {
            String url = "https://open.feishu.cn/open-apis/im/v1/messages/" + messageId
                    + "/read_users?page_size=100" + (pageToken.isEmpty() ? "" : "&page_token=" + pageToken);
            HttpHeaders h = new HttpHeaders();
            h.setBearerAuth(token);
            try {
                ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(h), Map.class);
                Map<?, ?> body = resp.getBody();
                if (body == null || !"0".equals(String.valueOf(body.get("code")))) {
                    log.warn("查询已读失败 messageId={} body={}", messageId, body);
                    break;
                }
                Object data = body.get("data");
                if (!(data instanceof Map<?, ?> dm)) break;
                Object items = dm.get("items");
                if (items instanceof List<?> list) {
                    for (Object it : list) {
                        if (it instanceof Map<?, ?> m) {
                            Object uid = m.get("user_id") != null ? m.get("user_id") : m.get("open_id");
                            if (uid != null) {
                                out.add(Map.of(
                                        "open_id", String.valueOf(uid),
                                        "read_at", formatReadTime(m.get("timestamp"))));
                            }
                        }
                    }
                }
                Object pt = dm.get("page_token");
                pageToken = pt != null ? String.valueOf(pt) : "";
                if (pageToken.isEmpty()) break;
            } catch (Exception e) {
                log.error("查询已读失败 messageId={}", messageId, e);
                break;
            }
        }
        return out;
    }

    /** 飞书已读时间戳（秒/毫秒）→ MM-dd HH:mm */
    private String formatReadTime(Object ts) {
        if (ts == null) return "";
        String s = String.valueOf(ts).trim();
        try {
            long v = Long.parseLong(s);
            if (s.length() >= 13) v = v / 1000;
            return java.time.Instant.ofEpochSecond(v)
                    .atZone(java.time.ZoneId.of("Asia/Shanghai"))
                    .format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));
        } catch (Exception e) {
            return s;
        }
    }

    // ==================== 配置查询 ====================

    /** 读 sys_config */
    public String getConfig(String key) {
        try {
            String val = jdbcTemplate.queryForObject(
                    "SELECT config_value FROM sys_config WHERE config_key=? LIMIT 1", String.class, key);
            return val != null ? val : "";
        } catch (Exception e) {
            return "";
        }
    }

    /** 读竖表：获取某分类+卡片类型的收件人 */
    public String getCardUserId(String categoryGroup, String cardType) {
        try {
            String uid = jdbcTemplate.queryForObject(
                    "SELECT feishu_user_id FROM loss_notify_card_config WHERE category=? AND card_type=? AND status=1 LIMIT 1",
                    String.class, categoryGroup, cardType);
            return uid != null ? uid : "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 未验收问题提醒测试群：
     * 优先读 sys_config.feishu_issue_reminder_test_chat_id（改库即生效，无需重启，生产可随时切换 测试群/门店群 模式）；
     * sys_config 未配置或为空时，回退 @Value 配置项（yml / 环境变量）。
     */
    public String getIssueReminderTestChatId() {
        String v = getConfig("feishu_issue_reminder_test_chat_id");
        if (v != null && !v.isBlank()) {
            return v.trim();
        }
        return issueReminderTestChatId;
    }

    public String getLossChatId() {
        return getConfig("feishu_loss_chat_id");
    }

    /** 获取牛油果泥 material_id（sys_config 未配置或为空时，按物料名称兜底查找） */
    public String getAvocadoMaterialId() {
        String id = getConfig("feishu_avocado_material_id");
        if (id != null && !id.isEmpty()) return id;
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT material_id FROM material WHERE material_name LIKE '%牛油果泥%' AND del_flag=0 ORDER BY updated_at DESC LIMIT 1");
            if (!rows.isEmpty()) return String.valueOf(rows.get(0).get("material_id"));
        } catch (Exception e) { }
        return "";
    }

    /** 牛油果泥 件→包 换算：1件=24包 */
    public static final BigDecimal AVOCADO_CONV = new BigDecimal("24");
    public boolean isAvocado(String materialId) {
        String avoId = getAvocadoMaterialId();
        return !avoId.isEmpty() && avoId.equals(materialId);
    }
    /** 牛油果泥数量换算：仅当单位是"件"时才 ×24 */
    public BigDecimal convertAvocadoQty(String materialId, BigDecimal qty, String unit) {
        if (!isAvocado(materialId)) return qty;
        return "件".equals(unit) ? qty.multiply(AVOCADO_CONV) : qty;
    }
    /** 牛油果泥单位换算：仅当单位是"件"时才改为"包" */
    public String convertAvocadoUnit(String materialId, String unit) {
        if (!isAvocado(materialId)) return unit;
        return "件".equals(unit) ? "包" : unit;
    }

    // ==================== 分类分组 ====================

    /**
     * 物料分类 → 分组 key（返回 loss_notify_card_config.category 的值）。
     * 例如 material.category='苹果' → 命中 config 行 category='水果蔬菜' → 返回 "水果蔬菜"。
     */
    public String resolveGroupKey(String materialCategory) {
        if (materialCategory == null || materialCategory.isEmpty()) return "其他类";
        try {
            String key = jdbcTemplate.queryForObject(
                    "SELECT DISTINCT category FROM loss_notify_card_config " +
                    "WHERE status=1 AND category != '其他类' AND FIND_IN_SET(?, category) > 0 " +
                    "ORDER BY LENGTH(category) ASC LIMIT 1",
                    String.class, materialCategory);
            return key != null && !key.isEmpty() ? key : "其他类";
        } catch (Exception e) {
            return "其他类";
        }
    }

    /** 全量加载分类→分组映射（供 resolveFruitVeg 等批量场景使用，避免逐条查库） */
    public Map<String, String> loadCategoryGroupMap() {
        Map<String, String> map = new HashMap<>();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT DISTINCT category FROM loss_notify_card_config WHERE status=1 AND category != '其他类'");
            for (Map<String, Object> row : rows) {
                String cats = (String) row.get("category");
                if (cats != null) {
                    for (String c : cats.split(",")) {
                        String tc = c.trim();
                        if (!tc.isEmpty()) map.put(tc, cats);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("加载分类分组映射失败", e);
        }
        // 兜底：sys_config.feishu_fruitveg_categories 里列出的分类也归入水果蔬菜组
        String fv = getConfig("feishu_fruitveg_categories");
        if (fv != null && !fv.isEmpty()) {
            for (String c : fv.split(",")) {
                String tc = c.trim();
                if (!tc.isEmpty()) map.put(tc, "水果蔬菜");
            }
        }
        return map;
    }

    /** 获取某分组的 @用户列表（逗号分隔，用于卡片内 <at> 提及） */
    public String getAtUsers(String groupKey) {
        try {
            List<String> uids = jdbcTemplate.queryForList(
                    "SELECT DISTINCT feishu_user_id FROM loss_notify_card_config " +
                    "WHERE category=? AND status=1 AND feishu_user_id IS NOT NULL AND feishu_user_id != ''",
                    String.class, groupKey);
            return uids.isEmpty() ? "" : String.join(",", uids);
        } catch (Exception e) {
            return "";
        }
    }

    // ==================== 卡片构建工具方法 ====================

    public Map<String, Object> textObj(String content) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("tag", "plain_text");
        t.put("content", content);
        return t;
    }

    public Map<String, Object> mdEl(String content) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tag", "markdown");
        m.put("content", content);
        return m;
    }

    public Map<String, Object> tagEl(String tag) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("tag", tag);
        return e;
    }

    public String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    public String getCardColor(String groupName) {
        if (groupName.contains("水果")) return "yellow";
        if ("其他类".equals(groupName)) return "blue";
        return "green";
    }

    /** 构建卡片 header */
    public Map<String, Object> cardHeader(String color, String title) {
        Map<String, Object> hdr = new LinkedHashMap<>();
        hdr.put("template", color);
        hdr.put("title", textObj(title));
        return hdr;
    }

    /** 构建飞书 applink */
    public String buildApplink(String path) {
        String rawUrl = serverUrl + path;
        return "https://applink.feishu.cn/client/web_app/open?appId=" + appId
                + "&lk_target_url=" + urlEncode(rawUrl);
    }

    /**
     * 卡片 markdown 直链（供外部联系人场景）：
     * web_app 型 applink 需要收件人有本租户自建应用访问权，外部人员/游客点击会报「应用页面已失效」；
     * 直接返回 serverUrl+path 裸 http(s) 链接，飞书内置浏览器直接打开，不依赖应用容器。
     */
    public String buildWebUrl(String path) {
        return serverUrl + path;
    }

    /** 拼接 @ 提及到 markdown 文本末尾 */
    public String appendAtMentions(String text, String atUsers) {
        if (atUsers == null || atUsers.isEmpty()) return text;
        StringBuilder sb = new StringBuilder(text);
        for (String uid : atUsers.split(",")) {
            String u = uid.trim();
            if (!u.isEmpty()) sb.append("\n<at id=").append(u).append("></at>");
        }
        return sb.toString();
    }
}
