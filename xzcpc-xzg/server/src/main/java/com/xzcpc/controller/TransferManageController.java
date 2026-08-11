package com.xzcpc.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.common.response.R;
import com.xzcpc.mp.entity.TransferOrder;
import com.xzcpc.mp.entity.TransferOrderItem;
import com.xzcpc.mp.entity.TransferReturnRecord;
import com.xzcpc.mp.mapper.TransferOrderItemMapper;
import com.xzcpc.mp.mapper.TransferReturnRecordMapper;
import com.xzcpc.mp.service.TransferOrderService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.bind.annotation.*;

import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/transfer")
@RequiredArgsConstructor
public class TransferManageController {

    private final TransferOrderService transferOrderService;
    private final TransferOrderItemMapper transferOrderItemMapper;
    private final TransferReturnRecordMapper returnRecordMapper;

    /** 总部只读台账 */
    @GetMapping("/list")
    public R<Page<TransferOrder>> list(
            @RequestParam(required = false) String fromStoreId,
            @RequestParam(required = false) String toStoreId,
            @RequestParam(required = false) String supervisorName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String handoff,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(transferOrderService.pageAll(fromStoreId, toStoreId, supervisorName, status, handoff, keyword,
                startDate, endDate, pageNum, pageSize));
    }

    /** 调货单详情（含物料明细、归还记录） */
    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        TransferOrder order = transferOrderService.detail(id, null);
        var items = transferOrderService.getItems(id);
        var records = returnRecordMapper.selectList(
                new LambdaQueryWrapper<TransferReturnRecord>()
                        .eq(TransferReturnRecord::getTransferId, id));
        return R.ok(Map.of("order", order, "items", items, "returnRecords", records));
    }

    /** 导出 Excel */
    @GetMapping("/export")
    public void export(
            @RequestParam(required = false) String fromStoreId,
            @RequestParam(required = false) String toStoreId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            HttpServletResponse response) throws Exception {

        List<TransferOrder> list = transferOrderService.pageAll(fromStoreId, toStoreId, null, status, null, keyword,
                startDate, endDate, 1, 99999).getRecords();

        // 批量获取所有明细
        Map<Long, List<TransferOrderItem>> itemsMap = Map.of();
        if (!list.isEmpty()) {
            List<Long> orderIds = list.stream().map(TransferOrder::getId).toList();
            List<TransferOrderItem> allItems = transferOrderItemMapper.selectList(
                    new LambdaQueryWrapper<TransferOrderItem>()
                            .in(TransferOrderItem::getTransferId, orderIds));
            itemsMap = allItems.stream()
                    .collect(Collectors.groupingBy(TransferOrderItem::getTransferId));
        }

        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("调货台账");
        Row header = sheet.createRow(0);
        String[] heads = {"调货编号", "调出门店", "调入门店", "物品", "数量", "状态", "发起人", "更新时间", "备注"};
        CellStyle headStyle = wb.createCellStyle();
        Font headFont = wb.createFont(); headFont.setBold(true); headStyle.setFont(headFont);
        for (int i = 0; i < heads.length; i++) { Cell c = header.createCell(i); c.setCellValue(heads[i]); c.setCellStyle(headStyle); }

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        for (int i = 0; i < list.size(); i++) {
            TransferOrder o = list.get(i);
            Row row = sheet.createRow(i + 1);
            int col = 0;

            // 拼装物品名称
            List<TransferOrderItem> items = itemsMap.get(o.getId());
            String itemStr = "";
            if (items != null && !items.isEmpty()) {
                itemStr = items.stream()
                        .map(it -> it.getMaterialName() + " " + it.getTransferQty().stripTrailingZeros().toPlainString() + it.getUnit())
                        .collect(Collectors.joining("; "));
            }

            row.createCell(col++).setCellValue(o.getBizCode() != null ? o.getBizCode() : "");
            row.createCell(col++).setCellValue(o.getFromStoreName() != null ? o.getFromStoreName() : "");
            row.createCell(col++).setCellValue(o.getToStoreName() != null ? o.getToStoreName() : "");
            row.createCell(col++).setCellValue(itemStr);
            row.createCell(col++).setCellValue(o.getTotalQty() != null ? o.getTotalQty().stripTrailingZeros().toPlainString() : "");
            row.createCell(col++).setCellValue(statusLabel(o.getStatus()));
            row.createCell(col++).setCellValue(o.getCreatedByName() != null ? o.getCreatedByName() : (o.getCreatedBy() != null ? o.getCreatedBy() : ""));
            row.createCell(col++).setCellValue(o.getUpdatedAt() != null ? o.getUpdatedAt().format(dtf) : "");
            row.createCell(col++).setCellValue(o.getRemark() != null ? o.getRemark() : "");
        }

        String filename = "调货台账_" + LocalDate.now() + ".xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + URLEncoder.encode(filename, StandardCharsets.UTF_8));
        try (OutputStream os = response.getOutputStream()) { wb.write(os); }
        wb.close();
    }

    private String statusLabel(String s) {
        return switch (s) {
            case "pending_confirm" -> "待确认";
            case "confirmed" -> "待交接";
            case "pending_ship" -> "待收货";
            case "pending_receive" -> "已收货";
            case "completed" -> "已完成";
            case "cancelled" -> "已取消";
            case "rejected" -> "已拒绝";
            default -> s;
        };
    }
}
