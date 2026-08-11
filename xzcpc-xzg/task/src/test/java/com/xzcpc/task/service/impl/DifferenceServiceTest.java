package com.xzcpc.task.service.impl;

import com.xzcpc.common.exception.BusinessException;
import com.xzcpc.task.entity.DifferenceProcessLog;
import com.xzcpc.task.entity.InventoryDifference;
import com.xzcpc.task.entity.TaskMaterialSummary;
import com.xzcpc.task.mapper.DifferenceProcessLogMapper;
import com.xzcpc.task.mapper.InventoryDifferenceMapper;
import com.xzcpc.task.mapper.TaskMaterialSummaryMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 差异处理测试（状态机 + 终态校验）
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("差异处理")
class DifferenceServiceTest {

    @Mock
    private InventoryDifferenceMapper diffMapper;
    @Mock
    private DifferenceProcessLogMapper logMapper;
    @Mock
    private TaskMaterialSummaryMapper summaryMapper;

    @InjectMocks
    private DifferenceServiceImpl diffService;

    // ===== generateDifferences（已废弃，返回空列表）=====

    @Test
    @DisplayName("旧版 generateDifferences 已废弃，始终返回空列表")
    void generateDifferencesReturnsEmpty() {
        Map<String, BigDecimal> bookMap = Map.of("MAT001", new BigDecimal("100"));
        List<InventoryDifference> diffs = diffService.generateDifferences(1, bookMap);
        assertTrue(diffs.isEmpty());
    }

    // ===== 状态机测试 =====

    @Test
    @DisplayName("pending → adjust → adjusted（终态）")
    void pendingToAdjusted() {
        InventoryDifference diff = buildDiff(1L, "pending");
        when(diffMapper.selectById(1L)).thenReturn(diff);
        when(diffMapper.updateById(any())).thenReturn(1);
        when(logMapper.insert(any())).thenReturn(1);

        assertDoesNotThrow(() -> diffService.adjust(1L, "admin", "已调整库存"));
        assertEquals("adjusted", diff.getStatus());
    }

    @Test
    @DisplayName("pending → close → closed（终态）")
    void pendingToClosed() {
        InventoryDifference diff = buildDiff(2L, "pending");
        when(diffMapper.selectById(2L)).thenReturn(diff);
        when(diffMapper.updateById(any())).thenReturn(1);
        when(logMapper.insert(any())).thenReturn(1);

        assertDoesNotThrow(() -> diffService.close(2L, "admin", "差异在允许范围内"));
        assertEquals("closed", diff.getStatus());
    }

    @Test
    @DisplayName("终态 adjusted 不可再操作")
    void adjustedTerminalShouldReject() {
        InventoryDifference diff = buildDiff(4L, "adjusted");
        when(diffMapper.selectById(4L)).thenReturn(diff);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> diffService.adjust(4L, "admin", "再次调整"));
        assertTrue(ex.getMessage().contains("终态"));
    }

    @Test
    @DisplayName("终态 closed 不可再操作")
    void closedTerminalShouldReject() {
        InventoryDifference diff = buildDiff(5L, "closed");
        when(diffMapper.selectById(5L)).thenReturn(diff);

        assertThrows(BusinessException.class,
                () -> diffService.close(5L, "admin", "再次关闭"));
    }

    @Test
    @DisplayName("不存在的差异项 → 抛出异常")
    void notFoundShouldThrow() {
        when(diffMapper.selectById(999L)).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> diffService.adjust(999L, "admin", "调整"));
    }

    // ===== 辅助方法 =====

    private InventoryDifference buildDiff(Long id, String status) {
        InventoryDifference d = new InventoryDifference();
        d.setId(id);
        d.setTaskId(1);
        d.setMaterialId("MAT001");
        d.setMaterialName("测试物料");
        d.setTheoreticalQty(new BigDecimal("100"));
        d.setActualQty(new BigDecimal("105"));
        d.setDiffQty(new BigDecimal("5"));
        d.setStatus(status);
        return d;
    }
}
