package com.xzcpc.mp.dto;

import com.xzcpc.mp.entity.SupervisorVisit;
import com.xzcpc.mp.entity.SupervisorVisitAction;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 督导拜访单 — 详情响应（含门店经营数据、历史记录、行动计划）
 */
@Data
public class SupervisorVisitResp {

    /** 拜访单基本信息 */
    private SupervisorVisit visit;

    /** 行动计划列表 */
    private List<SupervisorVisitAction> actions;

    /** 门店经营数据快照 */
    private Map<String, Object> bizData;

    /** 最近 3 次历史拜访记录 */
    private List<SupervisorVisit> historyVisits;
}
