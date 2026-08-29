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

    /** 提交反馈（公开接口，H5 表单调用；phone 必填 11 位手机号；ip 用于限频防刷） */
    void submit(String feedbackType, String storeId, String storeName, String phone, String content, String images, String ip);

    /** 后台台账分页查询 */
    Page<IssueFeedback> adminPage(String feedbackType, String storeId, String storeName, String status,
                                  String keyword, String startDate, String endDate,
                                  int pageNum, int pageSize);

    /** 后台详情 */
    IssueFeedback detailForAdmin(Long id);

    /** 顾客凭手机号查询自己的反馈（公开接口，手机号即凭证；ip 限频防刷） */
    List<IssueFeedback> queryByPhone(String phone, String ip);

    /** 后台更新处理状态（status + 处理说明，自动记时间戳） */
    void updateStatus(Long id, String status, String processNote);
}
