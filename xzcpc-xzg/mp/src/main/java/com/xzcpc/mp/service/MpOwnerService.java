package com.xzcpc.mp.service;

import com.xzcpc.mp.dto.OwnerDashboardResp;

public interface MpOwnerService {
    OwnerDashboardResp dashboard(String openid);
}
