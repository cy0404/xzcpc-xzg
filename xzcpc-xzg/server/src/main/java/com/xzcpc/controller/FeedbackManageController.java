package com.xzcpc.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xzcpc.common.context.AdminContextHolder;
import com.xzcpc.common.context.AdminUser;
import com.xzcpc.common.response.R;
import com.xzcpc.common.service.StoreAccessService;
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
 * 督导数据范围：普通督导（supervisor_admin）仅可查看/处理自己负责门店（supervisor_store_access 映射）的反馈。
 */
@RestController
@RequestMapping("/api/admin/feedback")
@RequiredArgsConstructor
public class FeedbackManageController {

    private final IssueFeedbackService issueFeedbackService;
    private final StoreAccessService storeAccessService;

    /** 督导数据范围：普通督导返回其负责的门店ID列表；总部/运营（全量角色）返回 null 表示不过滤 */
    private List<String> storeScope() {
        AdminUser admin = AdminContextHolder.get();
        if (storeAccessService.isSupervisorOnly(admin)) {
            return storeAccessService.getAccessibleStoreIds(admin.getOpenId());
        }
        return null;
    }

    /** 反馈类型选项（sys_config 可配，供筛选下拉） */
    @GetMapping("/options")
    public R<List<String>> options() {
        return R.ok(issueFeedbackService.getTypeOptions());
    }

    /** 反馈台账分页列表 */
    @GetMapping("/list")
    public R<Page<IssueFeedback>> list(
            @RequestParam(required = false) String feedbackType,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String storeId,
            @RequestParam(required = false) String storeName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        List<String> scope = storeScope();
        if (scope != null && scope.isEmpty()) {
            return R.ok(new Page<>(pageNum, pageSize)); // 督导未配置负责门店，返回空页
        }
        return R.ok(issueFeedbackService.adminPage(feedbackType, channel, storeId, storeName, status,
                keyword, startDate, endDate, pageNum, pageSize, scope));
    }

    /** 反馈详情 */
    @GetMapping("/{id}")
    public R<IssueFeedback> detail(@PathVariable Long id) {
        return R.ok(issueFeedbackService.detailForAdmin(id, storeScope()));
    }

    /** 更新处理状态（pending→processing→done/closed，自动记时间戳；processNote 为处理说明，顾客可见） */
    @PostMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        issueFeedbackService.updateStatus(id, body.get("status"), body.get("processNote"), storeScope());
        return R.ok();
    }

    /** 导出 Excel */
    @GetMapping("/export")
    public void export(
            @RequestParam(required = false) String feedbackType,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String storeId,
            @RequestParam(required = false) String storeName,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            HttpServletResponse response) throws Exception {

        List<String> scope = storeScope();
        if (scope != null && scope.isEmpty()) {
            scope = List.of("__no_accessible_store__"); // 督导未配置门店时导出空表（in 无匹配）
        }
        List<IssueFeedback> list = issueFeedbackService.adminPage(feedbackType, channel, storeId, storeName, null,
                keyword, startDate, endDate, 1, 99999, scope).getRecords();

        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("评价管理");
        Row header = sheet.createRow(0);
        String[] heads = {"渠道", "反馈类型", "门店", "手机号", "状态", "反馈内容", "图片", "提交时间"};
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
            row.createCell(col++).setCellValue(channelText(f.getChannel()));
            row.createCell(col++).setCellValue(nvl(f.getFeedbackType()));
            row.createCell(col++).setCellValue(nvl(f.getStoreName()));
            row.createCell(col++).setCellValue(nvl(f.getPhone()));
            row.createCell(col++).setCellValue(statusText(f.getStatus()));
            row.createCell(col++).setCellValue(nvl(f.getContent()));
            row.createCell(col++).setCellValue(nvl(f.getImages()));
            row.createCell(col++).setCellValue(f.getCreatedAt() != null ? f.getCreatedAt().format(dtf) : "");
        }
        for (int i = 0; i < heads.length; i++) {
            sheet.setColumnWidth(i, i == 5 ? 6000 : 4000);
        }

        String fileName = URLEncoder.encode("评价管理.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);
        wb.write(response.getOutputStream());
        wb.close();
    }

    private static String nvl(String s) {
        return s != null ? s : "";
    }

    /** 渠道码转中文（导出用） */
    private static String channelText(String channel) {
        return switch (channel == null ? "" : channel) {
            case "scan" -> "扫码反馈";
            case "meituan" -> "美团";
            case "xiaohongshu" -> "小红书";
            default -> nvl(channel);
        };
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
