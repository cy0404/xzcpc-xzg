package com.xzcpc.mp.service;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaSubscribeMessage;
import cn.binarywang.wx.miniapp.bean.WxMaSubscribeMessage.MsgData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.error.WxErrorException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 小程序订阅消息发送（全部通知场景共用一个模板 ID，内容以【模块名】前缀区分）。
 *
 * 模板字段约定（用户在 mp.weixin.qq.com 申请模板时按序选 3 个关键词）：
 *   thing1 = 标题（如【报损审批】今日有 3 条报损待审批）
 *   thing2 = 说明内容
 *   time1  = 时间
 * 申请到的模板字段名可能不同，如不一致修改本类 data 组装即可（或改为配置化）。
 *
 * 额度由 NotificationSenderJob 发送前原子扣减；本类只负责"发出去"，不含额度逻辑。
 * 小程序 access_token 由 WxMaService 内部管理缓存，无需自建 token 缓存。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscribeMessageService {

    private final WxMaService wxMaService;

    @Value("${notify.subscribe.template-id:}")
    private String templateId;
    /** 开发者工具/体验版调试时改 trial；正式发布保持 formal */
    @Value("${notify.subscribe.miniprogram-state:formal}")
    private String miniprogramState;

    public enum Outcome { SUCCESS, NOT_CONFIGURED, USER_REJECTED, FAILED }

    /**
     * 发送一条订阅消息。
     * @return SUCCESS 成功；USER_REJECTED 43101 用户拒收/未订阅（额度应退回，勿重试）；
     *         FAILED 其他失败（额度应退回，可重试）；NOT_CONFIGURED 模板未配置（终态）
     */
    public Outcome send(String openid, String pagePath, String title, String content) {
        if (templateId == null || templateId.isBlank()) {
            return Outcome.NOT_CONFIGURED;
        }
        try {
            WxMaSubscribeMessage msg = WxMaSubscribeMessage.builder()
                    .toUser(openid)
                    .templateId(templateId)
                    .data(List.of(
                            new MsgData("thing1", truncate20(title)),
                            new MsgData("thing2", truncate20(content)),
                            new MsgData("time1", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))))
                    .build();
            msg.setMiniprogramState(miniprogramState);
            if (StringUtils.hasText(pagePath)) {
                msg.setPage(pagePath.startsWith("/") ? pagePath : "/" + pagePath);
            }
            wxMaService.getSubscribeService().sendSubscribeMsg(msg);
            return Outcome.SUCCESS;
        } catch (WxErrorException e) {
            int code = e.getError().getErrorCode();
            if (code == 43101) {
                log.warn("订阅消息 43101 用户拒收/未订阅 openid={} page={}", openid, pagePath);
                return Outcome.USER_REJECTED;
            }
            log.warn("订阅消息发送失败 openid={} code={} msg={}", openid, code, e.getError().getErrorMsg());
            return Outcome.FAILED;
        } catch (Exception e) {
            log.error("订阅消息发送异常 openid={}", openid, e);
            return Outcome.FAILED;
        }
    }

    /** thing 字段上限 20 字符，超长截断（中文按字符计） */
    private String truncate20(String s) {
        if (s == null) return "";
        return s.length() <= 20 ? s : s.substring(0, 20);
    }
}
