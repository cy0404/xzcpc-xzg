package com.xzcpc.mp.service;

import com.xzcpc.mp.dto.WorkHoursSaveReq;
import com.xzcpc.mp.entity.StoreWorkHours;

import java.util.List;

public interface MpWorkHoursService {

    List<StoreWorkHours> list(String storeId);

    StoreWorkHours create(String storeId, String storeName, String openid, WorkHoursSaveReq req);

    StoreWorkHours update(String storeId, String openid, String recordId, WorkHoursSaveReq req);

    void delete(String storeId, String openid, String recordId);
}
