package com.xzcpc.mp.service;

import com.xzcpc.mp.dto.AddZoneReq;
import com.xzcpc.mp.dto.ItemSaveReq;
import com.xzcpc.mp.dto.SaveZoneReq;
import com.xzcpc.mp.dto.SortReq;

import java.util.List;
import java.util.Map;

public interface MpZoneService {
    List<Map<String, Object>> getMaterials(Integer taskId, Integer zoneId, String storeId);
    void saveZone(Integer taskId, Integer zoneId, SaveZoneReq req, String storeId);
    void itemSave(Integer taskId, Integer zoneId, ItemSaveReq req, String storeId);
    Map<String, Object> addZone(Integer taskId, AddZoneReq req, String storeId);
    void deleteZone(Integer taskId, Integer zoneId, String storeId);
    void sortZones(Integer taskId, SortReq req, String storeId);
    void updateZoneName(Integer taskId, Integer zoneId, String zoneName, String storeId);
}
