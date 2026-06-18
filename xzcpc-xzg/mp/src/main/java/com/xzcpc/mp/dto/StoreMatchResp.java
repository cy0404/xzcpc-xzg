package com.xzcpc.mp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 老板绑定 - 查询匹配到的门店列表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreMatchResp {

    private List<StoreItem> stores;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StoreItem {
        private String storeId;
        private String storeName;
        private String ownerPhone;
        private boolean alreadyBound; // 该门店是否已被当前微信绑定
    }
}
