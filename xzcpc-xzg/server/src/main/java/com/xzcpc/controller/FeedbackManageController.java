package com.xzcpc.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.common.response.R;
import com.xzcpc.entity.IssueFeedback;
import com.xzcpc.service.IssueFeedbackService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 总部端扫码问题反馈台账（admin 登录后访问，AdminLoginInterceptor 自动保护）。
 */
@RestController
@RequestMapping("/api/admin/feedback")
@RequiredArgsConstructor
public class FeedbackManageController {

    private final IssueFeedbackService issueFeedbackService;

    /** 反馈类型选项（sys_config 可配，供筛选下拉） */
    @GetMapping("/options")
    public R<List<String>> options() {
        return R.ok(issueFeedbackService.getTypeOptions());
    }

    /** 反馈台账分页列表 */
    @GetMapping("/list")
    public R<Page<IssueFeedback>> list(
            @RequestParam(required = false) String feedbackType,
            @RequestParam(required = false) String storeId,
            @RequestParam(required = false) String storeName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(issueFeedbackService.adminPage(feedbackType, storeId, storeName, status,
                keyword, startDate, endDate, pageNum, pageSize));
    }

    /** 反馈详情 */
    @GetMapping("/{id}")
    public R<IssueFeedback> detail(@PathVariable Long id) {
        return R.ok(issueFeedbackService.detailForAdmin(id));
    }

    /** 更新处理状态（pending→processing→done/closed，自动记时间戳；processNote 为处理说明，顾客可见） */
    @PostMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        issueFeedbackService.updateStatus(id, body.get("status"), body.get("processNote"));
        return R.ok();
    }

    /** 导出 Excel */
    @GetMapping("/export")
    public void export(
            @RequestParam(required = false) String feedbackType,
            @RequestParam(required = false) String storeId,
            @RequestParam(required = false) String storeName,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            HttpServletResponse response) throws Exception {

        List<IssueFeedback> list = issueFeedbackService.adminPage(feedbackType, storeId, storeName, null,
                keyword, startDate, endDate, 1, 99999).getRecords();

        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("问题反馈");
        Row header = sheet.createRow(0);
        String[] heads = {"反馈类型", "门店", "手机号", "状态", "反馈内容", "图片", "提交时间"};
        CellStyle headStyle = wb.createCellStyle();
        Font headFont = wb.createFont();
        headFont.setBold(true);
        headStyle.setFont(headFont);
        for (int i = 0; i < heads.length; i++) {
            Cell c = header.createCell(i);
            c.setCellValue(heads[i]);
            c.setCellStyle(headStyle);
        }

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        for (int i = 0; i < list.size(); i++) {
            IssueFeedback f = list.get(i);
            Row row = sheet.createRow(i + 1);
            int col = 0;
            row.createCell(col++).setCellValue(nvl(f.getFeedbackType()));
            row.createCell(col++).setCellValue(nvl(f.getStoreName()));
            row.createCell(col++).setCellValue(nvl(f.getPhone()));
            row.createCell(col++).setCellValue(statusText(f.getStatus()));
            row.createCell(col++).setCellValue(nvl(f.getContent()));
            row.createCell(col++).setCellValue(nvl(f.getImages()));
            row.createCell(col++).setCellValue(f.getCreatedAt() != null ? f.getCreatedAt().format(dtf) : "");
        }
        for (int i = 0; i < heads.length; i++) {
            sheet.setColumnWidth(i, i == 4 ? 6000 : 4000);
        }

        String fileName = URLEncoder.encode("问题反馈.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);
        wb.write(response.getOutputStream());
        wb.close();
    }

    private static String nvl(String s) {
        return s != null ? s : "";
    }

    /** 状态码转中文（导出用） */
    private static String statusText(String status) {
        return switch (status == null ? "" : status) {
            case "processing" -> "处理中";
            case "done" -> "已处理";
            case "closed" -> "已关闭";
            case "pending" -> "待处理";
            default -> nvl(status);
        };
    }
}
