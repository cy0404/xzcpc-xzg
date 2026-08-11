-- V6: 报损操作日志表

CREATE TABLE IF NOT EXISTS loss_report_log (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  report_id  BIGINT NOT NULL COMMENT '报损记录ID',
  action     VARCHAR(50) NOT NULL COMMENT '操作类型',
  operator   VARCHAR(100) DEFAULT NULL COMMENT '操作人',
  remark     VARCHAR(500) DEFAULT NULL COMMENT '备注说明',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_report (report_id)
) COMMENT='报损操作日志';
