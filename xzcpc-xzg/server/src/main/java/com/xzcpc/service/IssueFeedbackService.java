package com.xzcpc.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.entity.IssueFeedback;

import java.util.List;

/**
 * 扫码问题反馈服务（总部端，独立于小程序）
 */
public interface IssueFeedbackService {

    /** 反馈类型选项（从 sys_config.feedback_type_options 读取，逗号分隔） */
    List<String> getTypeOptions();

    /** 提交反馈（公开接口，H5 表单调用；phone 必填 11 位手机号；channel 为空时默认 scan 扫码；ip 用于限频防刷） */
    void submit(String feedbackType, String channel, String storeId, String storeName,
                String phone, String content, String images, String ip);

    /**
     * 后台台账分页查询。
     * accessibleStoreIds：督导数据范围（supervisor_store_access 映射的门店ID）；
     * null=不过滤（总部/运营看全部），非空列表=仅返回这些门店的记录（督导），空列表=返回空页（督导未配置门店）
     */
    Page<IssueFeedback> adminPage(String feedbackType, String channel, String storeId, String storeName, String status,
                                  String keyword, String startDate, String endDate,
                                  int pageNum, int pageSize, List<String> accessibleStoreIds);

    /**
     * 后台详情。accessibleStoreIds 非 null 时校验记录门店在范围内（督导仅可查看自己门店的反馈）
     */
    IssueFeedback detailForAdmin(Long id, List<String> accessibleStoreIds);

    /** 顾客凭手机号查询自己的反馈（公开接口，手机号即凭证；ip 限频防刷） */
    List<IssueFeedback> queryByPhone(String phone, String ip);

    /**
     * 后台更新处理状态（status + 处理说明，自动记时间戳）。
     * accessibleStoreIds 非 null 时校验记录门店在范围内（督导仅可处理自己门店的反馈）
     */
    void updateStatus(Long id, String status, String processNote, List<String> accessibleStoreIds);
}
