package com.xzcpc.job;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 补发后 4 天门店未点击收货 → 自动确认收货
 */
@Slf4j
@Component
public class LossReportAutoReceiveJob {

    private final JdbcTemplate jdbcTemplate;

    public LossReportAutoReceiveJob(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(cron = "0 0 3 * * ?")  // 每天凌晨 3 点
    public void autoReceive() {
        log.info("开始自动收货检查...");
        try {
            List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "SELECT id, store_name, material_name FROM loss_report " +
                "WHERE status='confirmed_resend' AND confirmed_at IS NOT NULL " +
                "AND confirmed_at <= DATE_SUB(NOW(), INTERVAL 4 DAY) " +
                "AND del_flag=0");

            if (list.isEmpty()) { log.info("无待自动收货记录"); return; }

            for (Map<String, Object> r : list) {
                long id = ((Number) r.get("id")).longValue();
                jdbcTemplate.update(
                    "UPDATE loss_report SET status='received', completed_at=NOW() WHERE id=?", id);
                jdbcTemplate.update(
                    "INSERT INTO loss_report_log (report_id, action, operator, remark, created_at) " +
                    "VALUES (?, 'auto_receive', '系统', '补发后4天门店未收货，系统自动确认', NOW())", id);
                log.info("自动收货 id={} store={} material={}",
                    id, r.get("store_name"), r.get("material_name"));
            }
            log.info("自动收货完成，共 {} 条", list.size());
        } catch (Exception e) {
            log.error("自动收货失败", e);
        }
    }
}
