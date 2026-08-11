-- ============================================================
-- 象掌柜 v2.0 P0 数据库迁移
-- 基于 V1 schema.sql 增量变更
-- ============================================================

USE store_inventory;

-- ============================================================
-- 一、现有表修改
-- ============================================================

-- 1.1 store_manager_session 加 role 字段
ALTER TABLE store_manager_session
    ADD COLUMN role VARCHAR(20) DEFAULT NULL COMMENT '角色：store_manager|owner|staff（P0登录时缓存）'
    AFTER store_name;

-- 1.2 task 状态扩展（支持 pending_submit + overdue）
ALTER TABLE task
    MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'not_started'
        COMMENT 'not_started|in_progress|pending_submit|submitted|overdue';

-- ============================================================
-- 二、A1 盘点优化 — 新增表
-- ============================================================

-- 2.1 条码补充申请表
CREATE TABLE barcode_supplement (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    barcode         VARCHAR(100)  NOT NULL COMMENT '待补充的条码',
    material_name   VARCHAR(200)  DEFAULT NULL COMMENT '物料名称（如有）',
    store_id        VARCHAR(50)   NOT NULL COMMENT '提交门店ID',
    submitted_by    VARCHAR(100)  DEFAULT NULL COMMENT '提交人 openid',
    status          VARCHAR(20)   NOT NULL DEFAULT 'pending' COMMENT 'pending|processed|rejected',
    remark          VARCHAR(500)  DEFAULT NULL COMMENT '备注',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag        INT           DEFAULT 0 COMMENT '删除标记',
    INDEX idx_barcode (barcode),
    INDEX idx_store (store_id)
) COMMENT '条码补充申请' ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2.2 容器配置表（称重去皮用）
CREATE TABLE container_config (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    container_name  VARCHAR(100)  NOT NULL COMMENT '容器名称（如大盆/小盆/周转箱）',
    tare_weight     DECIMAL(10,2) NOT NULL COMMENT '皮重（克）',
    store_id        VARCHAR(50)   DEFAULT NULL COMMENT '门店ID（NULL=全局共享）',
    status          TINYINT       NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag        INT           DEFAULT 0 COMMENT '删除标记',
    INDEX idx_store (store_id)
) COMMENT '容器配置（称重去皮）' ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2.3 盘点差异表
CREATE TABLE inventory_difference (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id         INT           NOT NULL COMMENT '关联任务ID',
    material_id     VARCHAR(50)   NOT NULL COMMENT '物料ID',
    material_name   VARCHAR(200)  NOT NULL COMMENT '物料名称',
    spec            VARCHAR(100)  DEFAULT '' COMMENT '规格',
    book_qty        DECIMAL(12,4) NOT NULL DEFAULT 0 COMMENT '账面数量',
    actual_qty      DECIMAL(12,4) NOT NULL DEFAULT 0 COMMENT '实盘数量',
    diff_qty        DECIMAL(12,4) NOT NULL DEFAULT 0 COMMENT '差异数量（实盘-账面）',
    diff_amount     DECIMAL(12,2) DEFAULT NULL COMMENT '差异金额',
    unit_price      DECIMAL(10,2) DEFAULT NULL COMMENT '盘点单价快照',
    diff_type       VARCHAR(20)   NOT NULL DEFAULT 'surplus' COMMENT 'surplus盘盈|shortage盘亏',
    status          VARCHAR(20)   NOT NULL DEFAULT 'pending' COMMENT 'pending|processing|adjusted|closed|converted',
    handler         VARCHAR(100)  DEFAULT NULL COMMENT '处理人',
    handled_at      DATETIME      DEFAULT NULL COMMENT '处理时间',
    remark          VARCHAR(500)  DEFAULT NULL COMMENT '备注',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag        INT           DEFAULT 0 COMMENT '删除标记',
    version         INT           DEFAULT 0 COMMENT '乐观锁',
    FOREIGN KEY (task_id) REFERENCES task(id),
    INDEX idx_task (task_id),
    INDEX idx_status (status),
    UNIQUE KEY uk_task_material (task_id, material_id)
) COMMENT '盘点差异项' ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2.4 差异处理日志
CREATE TABLE difference_process_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    diff_id         BIGINT        NOT NULL COMMENT '关联差异项ID',
    action          VARCHAR(50)   NOT NULL COMMENT '操作：processing|adjust|close|convert',
    operator        VARCHAR(100)  DEFAULT NULL COMMENT '操作人',
    remark          VARCHAR(500)  DEFAULT NULL COMMENT '备注',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (diff_id) REFERENCES inventory_difference(id),
    INDEX idx_diff (diff_id)
) COMMENT '差异处理日志' ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2.5 盘点模板推荐表
CREATE TABLE inventory_template_recommendation (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id        VARCHAR(50)   NOT NULL COMMENT '门店ID',
    template_id     INT           NOT NULL COMMENT '推荐模板ID',
    score           DECIMAL(5,2)  DEFAULT 0 COMMENT '推荐分值',
    reason          VARCHAR(200)  DEFAULT NULL COMMENT '推荐原因',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag        INT           DEFAULT 0 COMMENT '删除标记',
    UNIQUE KEY uk_store_template (store_id, template_id),
    INDEX idx_store (store_id)
) COMMENT '盘点模板推荐' ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================
-- 三、A2 门店报损 — 新增表
-- ============================================================

-- 3.1 报损记录表
CREATE TABLE loss_report (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id        VARCHAR(50)   NOT NULL COMMENT '门店ID',
    store_name      VARCHAR(200)  DEFAULT NULL COMMENT '门店名称',
    loss_type       VARCHAR(30)   NOT NULL COMMENT '报损类型：daily日常报损|arrival到货验收报损',
    loss_object     VARCHAR(30)   NOT NULL DEFAULT 'finished' COMMENT '报损对象：finished成品|semi_finished半成品',
    material_id     VARCHAR(50)   DEFAULT NULL COMMENT '物料ID（成品报损）',
    material_name   VARCHAR(200)  NOT NULL COMMENT '物料/半成品名称',
    spec            VARCHAR(100)  DEFAULT '' COMMENT '规格',
    unit            VARCHAR(50)   DEFAULT NULL COMMENT '单位',
    loss_qty        DECIMAL(12,4) DEFAULT NULL COMMENT '报损数量（成品报损用）',
    -- 半成品称重去皮字段
    gross_weight    DECIMAL(10,2) DEFAULT NULL COMMENT '含容器总重（克）',
    container_id    BIGINT        DEFAULT NULL COMMENT '容器ID',
    container_name  VARCHAR(100)  DEFAULT NULL COMMENT '容器名称快照',
    container_weight DECIMAL(10,2) DEFAULT NULL COMMENT '容器皮重快照（克）',
    net_weight      DECIMAL(10,2) DEFAULT NULL COMMENT '净重（克）',
    -- 通用
    occurred_date   DATE          NOT NULL COMMENT '发生日期',
    handler_name    VARCHAR(100)  DEFAULT NULL COMMENT '经手人',
    voucher_url     VARCHAR(500)  DEFAULT NULL COMMENT '凭证图片',
    remark          VARCHAR(500)  DEFAULT NULL COMMENT '备注',
    status          VARCHAR(30)   NOT NULL DEFAULT 'pending' COMMENT 'pending|confirmed_resend|rejected|closed',
    submitted_by    VARCHAR(100)  DEFAULT NULL COMMENT '提交人 openid',
    confirmed_by    VARCHAR(100)  DEFAULT NULL COMMENT '确认人',
    confirmed_at    DATETIME      DEFAULT NULL COMMENT '确认时间',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag        INT           DEFAULT 0 COMMENT '删除标记',
    version         INT           DEFAULT 0 COMMENT '乐观锁',
    INDEX idx_store (store_id),
    INDEX idx_type (loss_type),
    INDEX idx_status (status),
    INDEX idx_date (occurred_date)
) COMMENT '门店报损记录' ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================
-- 四、A3 调货管理 — 新增表
-- ============================================================

-- 4.1 调货单主表
CREATE TABLE transfer_order (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    biz_code        VARCHAR(50)   NOT NULL COMMENT '业务编码',
    creator_store_id VARCHAR(50)  DEFAULT NULL COMMENT '发起门店ID（调入方）',
    from_store_id   VARCHAR(50)   NOT NULL COMMENT '调出门店ID',
    from_store_name VARCHAR(200)  DEFAULT NULL COMMENT '调出门店名称',
    to_store_id     VARCHAR(50)   NOT NULL COMMENT '调入门店ID',
    to_store_name   VARCHAR(200)  DEFAULT NULL COMMENT '调入门店名称',
    status          VARCHAR(30)   NOT NULL DEFAULT 'pending_confirm' COMMENT '状态：pending_confirm|confirmed|pending_ship|pending_receive|completed|cancelled|rejected',
    total_qty       DECIMAL(12,4) NOT NULL DEFAULT 0 COMMENT '调货总数量',
    handoff         VARCHAR(50)   DEFAULT NULL COMMENT '交接方式：门店自取|对方自取|员工带货|第三方物流',
    remark          VARCHAR(500)  DEFAULT NULL COMMENT '备注/调货原因',
    created_by      VARCHAR(100)  DEFAULT NULL COMMENT '发起人 openid',
    confirmed_by    VARCHAR(100)  DEFAULT NULL COMMENT '确认人 openid（调出方）',
    shipped_by      VARCHAR(100)  DEFAULT NULL COMMENT '发货人 openid',
    received_by     VARCHAR(100)  DEFAULT NULL COMMENT '收货人 openid（调入方）',
    confirmed_at    DATETIME      DEFAULT NULL COMMENT '确认时间',
    shipped_at      DATETIME      DEFAULT NULL COMMENT '发货时间',
    received_at     DATETIME      DEFAULT NULL COMMENT '收货时间',
    completed_at    DATETIME      DEFAULT NULL COMMENT '完成时间',
    cancelled_at    DATETIME      DEFAULT NULL COMMENT '取消时间',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    del_flag        INT           DEFAULT 0 COMMENT '删除标记',
    version         INT           DEFAULT 0 COMMENT '乐观锁',
    INDEX idx_from_store (from_store_id),
    INDEX idx_to_store (to_store_id),
    INDEX idx_status (status),
    INDEX idx_biz_code (biz_code)
) COMMENT '调货单' ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 4.2 调货单明细表
CREATE TABLE transfer_order_item (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    transfer_id     BIGINT        NOT NULL COMMENT '关联调货单ID',
    material_name   VARCHAR(200)  NOT NULL COMMENT '物料名称',
    spec            VARCHAR(100)  DEFAULT '' COMMENT '规格',
    unit            VARCHAR(50)   NOT NULL COMMENT '单位',
    transfer_qty    DECIMAL(12,4) NOT NULL COMMENT '调货数量',
    base_unit       VARCHAR(50)   DEFAULT NULL COMMENT '基础单位（最小盘点单位）',
    base_qty        DECIMAL(12,4) DEFAULT NULL COMMENT '基础单位数量（换算后用于跨分区汇总）',
    input_unit      VARCHAR(50)   DEFAULT NULL COMMENT '录入单位',
    input_qty       DECIMAL(12,4) DEFAULT NULL COMMENT '录入单位数量',
    unit_price      DECIMAL(10,2) DEFAULT NULL COMMENT '物料单价',
    remark          VARCHAR(200)  DEFAULT NULL COMMENT '备注',
    del_flag        INT           DEFAULT 0 COMMENT '删除标记',
    FOREIGN KEY (transfer_id) REFERENCES transfer_order(id),
    INDEX idx_transfer (transfer_id)
) COMMENT '调货单明细' ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================
-- 五、A4 物流信息 — 新增表
-- ============================================================

-- 5.1 物流记录表
CREATE TABLE logistics_record (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    transfer_id     BIGINT        DEFAULT NULL COMMENT '关联调货单ID（可为空，独立录入）',
    store_id        VARCHAR(50)   NOT NULL COMMENT '关联门店ID',
    store_name      VARCHAR(200)  DEFAULT NULL COMMENT '门店名称',
    tracking_no     VARCHAR(100)  NOT NULL COMMENT '运单号',
    carrier         VARCHAR(100)  DEFAULT NULL COMMENT '承运商',
    status          VARCHAR(30)   NOT NULL DEFAULT 'pending_shipment'
        COMMENT 'pending_shipment|in_transit|delivering|signed|abnormal|query_failed',
    tracking_data   TEXT          DEFAULT NULL COMMENT '轨迹数据JSON',
    created_by      VARCHAR(100)  DEFAULT NULL COMMENT '录入人',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag        INT           DEFAULT 0 COMMENT '删除标记',
    UNIQUE KEY uk_tracking_no (tracking_no),
    INDEX idx_store (store_id),
    INDEX idx_transfer (transfer_id),
    INDEX idx_status (status)
) COMMENT '物流记录' ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================
-- 六、B1 问题处理 — 新增表
-- ============================================================

-- 6.1 问题单表
CREATE TABLE issue (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    biz_code        VARCHAR(50)   NOT NULL COMMENT '业务编码',
    store_id        VARCHAR(50)   NOT NULL COMMENT '提交门店ID',
    store_name      VARCHAR(200)  DEFAULT NULL COMMENT '门店名称',
    title           VARCHAR(200)  NOT NULL COMMENT '问题标题',
    issue_type      VARCHAR(50)   NOT NULL COMMENT '问题类型',
    urgency         VARCHAR(20)   NOT NULL COMMENT '紧急程度：urgent|normal|low',
    description     VARCHAR(2000) NOT NULL COMMENT '问题描述',
    contact_name    VARCHAR(50)   NOT NULL COMMENT '联系人',
    contact_phone   VARCHAR(20)   NOT NULL COMMENT '联系电话',
    images          TEXT          DEFAULT NULL COMMENT '图片URL（逗号分隔）',
    xiangmu_id      VARCHAR(100)  DEFAULT NULL COMMENT '象目经理同步单号',
    sync_status     VARCHAR(20)   DEFAULT NULL COMMENT '象目经理同步状态：pending|synced|failed',
    status          VARCHAR(30)   NOT NULL DEFAULT 'pending' COMMENT 'pending|processing|resolved|closed',
    submitted_by    VARCHAR(100)  DEFAULT NULL COMMENT '提交人 openid',
    processed_by    VARCHAR(100)  DEFAULT NULL COMMENT '处理人',
    processed_at    DATETIME      DEFAULT NULL COMMENT '处理时间',
    resolved_at     DATETIME      DEFAULT NULL COMMENT '解决时间',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag        INT           DEFAULT 0 COMMENT '删除标记',
    version         INT           DEFAULT 0 COMMENT '乐观锁',
    INDEX idx_store (store_id),
    INDEX idx_type (issue_type),
    INDEX idx_status (status),
    INDEX idx_urgency (urgency),
    INDEX idx_biz_code (biz_code)
) COMMENT '问题处理单' ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================
-- 七、B2 企微通知 — 新增表
-- ============================================================

-- 7.1 通知日志表
CREATE TABLE notification_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type      VARCHAR(50)   NOT NULL COMMENT '事件类型：issue_processing|issue_resolved|logistics_abnormal|transfer_pending|transfer_receiving|order_confirmation',
    store_id        VARCHAR(50)   NOT NULL COMMENT '目标门店ID',
    target_openid   VARCHAR(128)  DEFAULT NULL COMMENT '目标用户openid',
    title           VARCHAR(200)  NOT NULL COMMENT '通知标题',
    content         VARCHAR(1000) NOT NULL COMMENT '通知内容',
    source_id       VARCHAR(100)  DEFAULT NULL COMMENT '来源业务ID（如issue.id, transfer.id）',
    status          TINYINT       NOT NULL DEFAULT 0 COMMENT '0待发送 1成功 2失败',
    fail_reason     VARCHAR(500)  DEFAULT NULL COMMENT '失败原因',
    retry_count     INT           DEFAULT 0 COMMENT '重试次数',
    sent_at         DATETIME      DEFAULT NULL COMMENT '发送时间',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_store (store_id),
    INDEX idx_event (event_type),
    INDEX idx_status (status),
    INDEX idx_source (source_id)
) COMMENT '企微通知日志' ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================
-- 迁移完成
-- ============================================================
SELECT 'V2__P0_schema.sql executed successfully' AS status;
