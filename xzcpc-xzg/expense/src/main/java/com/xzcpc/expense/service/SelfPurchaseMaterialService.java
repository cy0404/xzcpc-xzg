package com.xzcpc.expense.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.expense.entity.SelfPurchaseMaterial;

public interface SelfPurchaseMaterialService {
    Page<SelfPurchaseMaterial> page(String storeIds, String supervisorName, String startDate, String endDate,
                                     String handlerName, int pageNum, int pageSize);
}
