package com.xzcpc.mp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 老板登录时查询自身绑定状态响应。
 * 用于登录页判断：进老板首页 / 审核中 / 已拒绝 / 无记录。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerMyStatusResp {

    /** 是否已有已绑定门店（owner_openid 匹配） */
    private boolean hasBound;

    /** 已绑定门店数量 */
    private int boundStoreCount;

    /** 最新一条绑定申请（无申请时为 null） */
    private LatestApplication latestApplication;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LatestApplication {
        /** bindCode（状态页路由使用） */
        private String bindCode;
        /** 状态：pending|auto_bound|approved|rejected */
        private String status;
        /** 申请匹配到的门店名称 */
        private List<String> storeNames;
        /** 拒绝原因 */
        private String rejectReason;
        /** 提交时间 */
        private String submitTime;
        /** 申请人姓名 */
        private String name;
        /** 手机号（脱敏） */
        private String phoneMasked;
    }
}
