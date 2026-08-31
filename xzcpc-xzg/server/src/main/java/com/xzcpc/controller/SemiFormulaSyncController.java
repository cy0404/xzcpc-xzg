package com.xzcpc.controller;

import com.xzcpc.common.response.R;
import com.xzcpc.template.service.SemiFormulaSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 半成品成本卡（BOM 配方）同步手动触发（总部端）。
 * 上线验证阶段手动补跑用：POST /api/admin/semi-formula/sync
 */
@RestController
@RequestMapping("/api/admin/semi-formula")
@RequiredArgsConstructor
public class SemiFormulaSyncController {

    private final SemiFormulaSyncService semiFormulaSyncService;

    /** 手动触发半成品成本卡全量同步 */
    @GetMapping("/sync")
    public R<Map<String, Object>> sync() {
        int count = semiFormulaSyncService.sync();
        return R.ok(Map.of("count", count, "msg", "半成品成本卡同步完成，处理 " + count + " 个半成品"));
    }
}
