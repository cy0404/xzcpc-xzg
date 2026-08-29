package com.xzcpc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.common.exception.BusinessException;
import com.xzcpc.common.feishu.FeishuMessageService;
import com.xzcpc.entity.IssueFeedback;
import com.xzcpc.mapper.IssueFeedbackMapper;
import com.xzcpc.service.IssueFeedbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 扫码问题反馈服务实现（总部端，独立于小程序）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IssueFeedbackServiceImpl implements IssueFeedbackService {

    /** 反馈内容最大长度 */
    private static final int CONTENT_MAX_LEN = 1000;
    /** 图片最大张数 */
    private static final int IMAGE_MAX_COUNT = 3;
    /** 类型选项配置 key */
    private static final String OPTIONS_KEY = "feedback_type_options";
    /** 默认选项（sys_config 未配置时的兜底） */
    private static final String DEFAULT_OPTIONS = "门店服务,饮品品质,其他";

    // ===== 提交即入库：客诉推送门店群后，门店端（H5 客诉处理）必须立即可见该条记录，故不做攒批 =====

    // ===== 公开接口防刷（投诉表单全国可扫，必须有频率限制） =====
    /** 同 IP 每分钟最多提交次数（防脚本刷屏，又不误伤门店同址多顾客） */
    private static final int RATE_MINUTE_LIMIT = 3;
    /** 同 IP 24 小时最多提交次数 */
    private static final int RATE_DAY_LIMIT = 20;
    /** 内存计数：ip -> [分钟窗口起始, 分钟计数, 日窗口起始, 日计数]，重启清零可接受 */
    private final ConcurrentHashMap<String, long[]> rateMap = new ConcurrentHashMap<>();

    /** 查询进度限频：ip -> [分钟窗口起始, 分钟计数]，同 IP 每分钟最多 30 次（防脚本遍历手机号） */
    private final ConcurrentHashMap<String, long[]> queryRateMap = new ConcurrentHashMap<>();

    private final IssueFeedbackMapper issueFeedbackMapper;
    private final JdbcTemplate jdbcTemplate;
    private final FeishuMessageService fms;
    /** 客诉通知专用线程池（common 模块 AsyncConfig），与日志线程池隔离，避免外呼阻塞提交/flush 线程。
     *  注：不能用 final+Lombok 构造注入——@Qualifier 不会被复制到 Lombok 生成的构造参数上（且编译无 -parameters），
     *  多 Executor bean 场景会启动失败；改 @Autowired 字段注入，@Qualifier 按名匹配 */
    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.beans.factory.annotation.Qualifier("feedbackNotifyExecutor")
    private java.util.concurrent.Executor feedbackNotifyExecutor;

    /** 通知卡片「督导督促」提示语（业务 SLA：被 @ 的督导管理该门店，需督促门店 24h 内处理） */
    private static final String NOTIFY_SLA_TEXT = "**⏰ 请督导督促门店尽快处理（24 小时内）**";

    @Override
    public List<String> getTypeOptions() {
        String val = getConfig(OPTIONS_KEY, DEFAULT_OPTIONS);
        return Arrays.stream(val.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    @Override
    public void submit(String feedbackType, String storeId, String storeName, String phone, String content, String images, String ip) {
        checkRate(ip);
        if (!StringUtils.hasText(phone) || !phone.matches("^1\\d{10}$")) {
            throw new BusinessException("请填写正确的11位手机号");
        }
        if (!StringUtils.hasText(feedbackType)) {
            throw new BusinessException("请选择反馈类型");
        }
        if (!StringUtils.hasText(storeId)) {
            throw new BusinessException("请选择门店");
        }
        if (feedbackType.length() > 32) {
            throw new BusinessException("反馈类型过长");
        }
        if (!StringUtils.hasText(content)) {
            throw new BusinessException("请填写反馈内容");
        }
        if (content.length() > CONTENT_MAX_LEN) {
            throw new BusinessException("反馈内容过长，最多" + CONTENT_MAX_LEN + "字");
        }
        if (storeId != null && storeId.length() > 64) {
            storeId = storeId.substring(0, 64);
        }
        if (storeName != null && storeName.length() > 128) {
            storeName = storeName.substring(0, 128);
        }
        if (StringUtils.hasText(images)) {
            String[] urls = images.split(",");
            if (urls.length > IMAGE_MAX_COUNT) {
                throw new BusinessException("图片最多" + IMAGE_MAX_COUNT + "张");
            }
        }

        IssueFeedback f = new IssueFeedback();
        f.setFeedbackType(feedbackType.trim());
        f.setStoreId(StringUtils.hasText(storeId) ? storeId.trim() : null);
        f.setStoreName(StringUtils.hasText(storeName) ? normalizeStoreName(storeName) : null);
        f.setContent(content.trim());
        f.setImages(StringUtils.hasText(images) ? images : null);
        f.setPhone(phone.trim());
        f.setStatus("pending");
        // 提交即入库：推送后门店端立即可见，攒批会导致「已推送但查不到」；插入失败直接抛异常，前端可见
        issueFeedbackMapper.insert(f);
        // 客诉实时推送门店群：独立线程池异步外呼，不阻塞提交线程；提交已落库，通知失败不影响数据
        feedbackNotifyExecutor.execute(() -> notifyStoreGroup(f));
        log.info("[feedback] new submit saved: id={} type={} store={}", f.getId(), f.getFeedbackType(), f.getStoreName());
    }

    /**
     * 门店名称统一规则（与 H5 展示一致）：「XX（YY）」取括号内 YY，无括号取整体，再去除品牌前缀。
     * 保证后台台账与页面展示的门店名完全一致。
     */
    private static String normalizeStoreName(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        String n = name.trim();
        int l = n.indexOf('（');
        int r = n.indexOf('）');
        if (l < 0) {
            l = n.indexOf('(');
            r = n.indexOf(')');
        }
        if (l >= 0 && r > l) {
            n = n.substring(l + 1, r).trim();
        }
        return n.replace("象子茶铺茶", "");
    }

    // ==================== 客诉通知（门店飞书群 @对应督导） ====================

    /**
     * 新客诉 → 发对应门店飞书群，并 @该店督导。
     * 链路：issue_feedback.store_id → store_info（chat_id 门店群）
     *       → supervisor_store_access（store_id → 督导 admin_name + open_id，同表权威数据源）→ 卡片 <at>
     * 降级：督导未配置 → 卡片不 @ 不写负责人（照常发送）；门店未配群 → 跳过并记日志。均不阻断。
     */
    private void notifyStoreGroup(IssueFeedback f) {
        if (f == null || !StringUtils.hasText(f.getStoreId())) {
            return;
        }
        try {
            // 门店飞书群 ID：store_info.chat_id
            Map<String, Object> store;
            try {
                store = jdbcTemplate.queryForMap(
                        "SELECT chat_id FROM store_info WHERE store_id=? AND del_flag=0", f.getStoreId());
            } catch (Exception e) {
                log.warn("[feedback] notify skip, store not found: {} err={}", f.getStoreId(), e.getMessage());
                return;
            }
            String chatId = store.get("chat_id") == null ? null : String.valueOf(store.get("chat_id"));
            if (!StringUtils.hasText(chatId)) {
                log.warn("[feedback] notify skip, store {} has no feishu group", f.getStoreId());
                return;
            }
            // 督导：supervisor_store_access 按门店查（姓名 + open_id 同表）；未配置/无督导不影响发送
            String supName = null;
            String openId = null;
            try {
                Map<String, Object> sup = jdbcTemplate.queryForMap(
                        "SELECT admin_name, open_id FROM supervisor_store_access WHERE store_id=? AND del_flag=0 LIMIT 1",
                        f.getStoreId());
                supName = sup.get("admin_name") == null ? null : String.valueOf(sup.get("admin_name"));
                openId = sup.get("open_id") == null ? null : String.valueOf(sup.get("open_id"));
            } catch (Exception e) {
                // 督导未配置：卡片不 @ 不写负责人，照常发送
                log.warn("[feedback] supervisor lookup skip, store {} err={}", f.getStoreId(), e.getMessage());
            }

            String token = fms.getTenantToken();
            if (token == null) {
                log.warn("[feedback] notify skip, no feishu tenant token");
                return;
            }
            String content = StringUtils.hasText(f.getContent()) ? f.getContent() : "";
            String summary = content.length() > 50 ? content.substring(0, 50) + "…" : content;
            String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));
            String info = "**📢 新客诉提醒**\n门店：" + nvl(f.getStoreName()) + "\n类型："
                    + nvl(f.getFeedbackType()) + "\n内容：" + summary + "\n手机号：" + nvl(f.getPhone())
                    + "\n\n" + NOTIFY_SLA_TEXT;
            if (StringUtils.hasText(openId)) {
                info += "\n<at id=" + openId.trim() + "></at>";
            } else if (StringUtils.hasText(supName)) {
                info += "\n负责人：" + supName.trim();
            }

            Map<String, Object> card = new LinkedHashMap<>();
            card.put("header", fms.cardHeader("red", "客诉提醒 · " + nvl(f.getFeedbackType())));
            List<Map<String, Object>> els = new ArrayList<>();
            els.add(fms.mdEl(info));
            els.add(fms.tagEl("hr"));
            els.add(fms.mdEl("提交时间：" + time + "\n已同步至象子掌柜小程序「客诉处理」，请店长/老板尽快跟进"));
            card.put("elements", els);

            fms.sendToChat(token, chatId, card);
            log.info("[feedback] notified store group: {} chatId={} supervisor={} at={}", f.getStoreId(), chatId, supName, openId);
        } catch (Exception e) {
            log.error("[feedback] notify failed, store={} phone={}", f.getStoreId(), f.getPhone(), e);
        }
    }

    private static String nvl(String s) {
        return s != null ? s : "";
    }

    @Override
    public Page<IssueFeedback> adminPage(String feedbackType, String storeId, String storeName, String status,
                                         String keyword, String startDate, String endDate,
                                         int pageNum, int pageSize) {
        LambdaQueryWrapper<IssueFeedback> qw = new LambdaQueryWrapper<>();
        qw.eq(StringUtils.hasText(feedbackType), IssueFeedback::getFeedbackType, feedbackType);
        qw.eq(StringUtils.hasText(status), IssueFeedback::getStatus, status);
        qw.eq(StringUtils.hasText(storeId), IssueFeedback::getStoreId, storeId);
        qw.like(StringUtils.hasText(storeName), IssueFeedback::getStoreName, storeName);
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(IssueFeedback::getContent, keyword)
                    .or().like(IssueFeedback::getStoreName, keyword));
        }
        qw.ge(StringUtils.hasText(startDate), IssueFeedback::getCreatedAt, startDate + " 00:00:00");
        qw.le(StringUtils.hasText(endDate), IssueFeedback::getCreatedAt, endDate + " 23:59:59");
        qw.orderByDesc(IssueFeedback::getId);
        return issueFeedbackMapper.selectPage(new Page<>(pageNum, pageSize), qw);
    }

    @Override
    public IssueFeedback detailForAdmin(Long id) {
        IssueFeedback f = issueFeedbackMapper.selectById(id);
        if (f == null) {
            throw new BusinessException("反馈记录不存在");
        }
        return f;
    }

    @Override
    public List<IssueFeedback> queryByPhone(String phone, String ip) {
        checkRateQuery(ip);
        if (!StringUtils.hasText(phone) || !phone.matches("^1\\d{10}$")) {
            throw new BusinessException("请填写正确的11位手机号");
        }
        List<IssueFeedback> list = issueFeedbackMapper.selectList(new LambdaQueryWrapper<IssueFeedback>()
                .eq(IssueFeedback::getPhone, phone.trim())
                .orderByDesc(IssueFeedback::getCreatedAt));
        // 处理说明/凭证/处理人均仅内部可见，顾客凭手机号查询时不返回
        for (IssueFeedback f : list) {
            f.setProcessNote(null);
            f.setEvidence(null);
            f.setProcessedBy(null);
            f.setProcessedName(null);
        }
        return list;
    }

    @Override
    public void updateStatus(Long id, String status, String processNote) {
        if (id == null || !StringUtils.hasText(status)) {
            throw new BusinessException("参数不完整");
        }
        if (!Arrays.asList("pending", "processing", "done", "closed").contains(status)) {
            throw new BusinessException("无效的处理状态");
        }
        if (processNote != null && processNote.length() > 500) {
            throw new BusinessException("处理说明最多500字");
        }
        IssueFeedback f = issueFeedbackMapper.selectById(id);
        if (f == null) {
            throw new BusinessException("反馈记录不存在");
        }
        f.setStatus(status);
        if (StringUtils.hasText(processNote)) {
            f.setProcessNote(processNote.trim());
        }
        LocalDateTime now = LocalDateTime.now();
        if ("processing".equals(status)) {
            f.setProcessingAt(now);
        } else if ("done".equals(status) || "closed".equals(status)) {
            f.setProcessedAt(now);
            if (f.getProcessingAt() == null) {
                f.setProcessingAt(now); // 直接完成（跳过处理中）时补记开始时间，时间线完整
            }
        }
        issueFeedbackMapper.updateById(f);
    }

    /** 查询进度限频：同 IP 每分钟最多 30 次（防脚本遍历） */
    private void checkRateQuery(String ip) {
        if (!StringUtils.hasText(ip)) {
            return;
        }
        long now = System.currentTimeMillis();
        long[] r = queryRateMap.compute(ip, (k, v) -> {
            if (v == null) {
                return new long[]{now, 1};
            }
            if (now - v[0] > 60_000) {
                v[0] = now;
                v[1] = 1;
            } else {
                v[1]++;
            }
            return v;
        });
        if (r[1] > 30) {
            throw new BusinessException("查询过于频繁，请稍后再试");
        }
    }

    /**
     * 同 IP 限频：1 分钟内最多 RATE_MINUTE_LIMIT 次、24 小时内最多 RATE_DAY_LIMIT 次。
     * 内存滑窗计数，窗口过期自动重置；ip 为空（取不到）时放行，不误伤。
     */
    private void checkRate(String ip) {
        if (!StringUtils.hasText(ip)) {
            return;
        }
        long now = System.currentTimeMillis();
        long[] r = rateMap.compute(ip, (k, v) -> {
            if (v == null) {
                return new long[]{now, 1, now, 1};
            }
            if (now - v[0] > 60_000) {
                v[0] = now;
                v[1] = 1;
            } else {
                v[1]++;
            }
            if (now - v[2] > 86_400_000) {
                v[2] = now;
                v[3] = 1;
            } else {
                v[3]++;
            }
            return v;
        });
        if (r[1] > RATE_MINUTE_LIMIT) {
            throw new BusinessException("提交过于频繁，请稍后再试");
        }
        if (r[3] > RATE_DAY_LIMIT) {
            throw new BusinessException("今日提交次数已达上限，明天再来吧");
        }
    }

    /** 读 sys_config，失败/为空返回默认值 */
    private String getConfig(String key, String defaultValue) {
        try {
            String val = jdbcTemplate.queryForObject(
                    "SELECT config_value FROM sys_config WHERE config_key=? LIMIT 1", String.class, key);
            return StringUtils.hasText(val) ? val : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
