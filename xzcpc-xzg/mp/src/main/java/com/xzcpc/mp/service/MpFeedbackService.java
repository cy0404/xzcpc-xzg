package com.xzcpc.mp.service;

import com.xzcpc.mp.entity.IssueFeedback;

import java.util.List;
import java.util.Map;

/**
 * 小程序客诉处理服务（店长/老板操作，按当前登录用户门店过滤）
 */
public interface MpFeedbackService {

    /** 本店客诉列表（按状态过滤，创建时间倒序） */
    List<IssueFeedback> listByStore(String storeId, String status);

    /** 客诉详情（仅本门店可见） */
    IssueFeedback detail(String storeId, Long id);

    /** 标记已处理：处理说明 + 凭证（仅内部可见），记处理人与时间 */
    IssueFeedback markDone(String storeId, Long id, String openid, String employeeName,
                           String processNote, String evidence);

    /** 各店未处理客诉数（pending/processing > 0 的门店，首页全部门店视图用） */
    List<Map<String, Object>> overviewByStores(String openid);

    /** 单店未处理客诉数（首页单店视图用） */
    Map<String, Long> overview(String storeId);

    /** 跨店未处理客诉总数（首页全部门店视图汇总用） */
    Map<String, Long> overviewTotal(String openid);
}
