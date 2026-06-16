package com.xzcpc.mp.service;

import com.xzcpc.mp.dto.AddMaterialReq;
import com.xzcpc.mp.dto.SortReq;

import java.util.List;
import java.util.Map;

public interface MpMaterialService {
    List<Map<String, Object>> getCandidates(Integer taskId, Integer zoneId, String keyword, String storeId);
    void addMaterial(Integer taskId, Integer zoneId, AddMaterialReq req, String storeId);
    void removeMaterial(Integer taskId, Integer zoneId, String materialId, String storeId);
    void sortMaterials(Integer taskId, Integer zoneId, SortReq req, String storeId);
}
