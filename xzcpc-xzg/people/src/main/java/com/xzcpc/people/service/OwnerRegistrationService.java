package com.xzcpc.people.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.people.dto.OwnerRegistrationSaveReq;
import com.xzcpc.people.entity.OwnerRegistration;

public interface OwnerRegistrationService {

    Page<OwnerRegistration> page(String storeId, String status, String name, String phone,
                                   int pageNum, int pageSize);

    OwnerRegistration detail(Long id);

    OwnerRegistration bind(Long id, OwnerRegistrationSaveReq req);

    OwnerRegistration update(Long id, OwnerRegistrationSaveReq req);
}
