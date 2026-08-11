-- ============================================================
-- 督导拜访 — P2 数据库迁移
-- 新增表：supervisor_visit（拜访单）、supervisor_visit_action（行动计划）
-- 依赖：P0 supervisor_name 字段已存在于 store_info
-- ============================================================

-- 1) 督导拜访单
CREATE TABLE IF NOT EXISTS supervisor_visit (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    visit_no            VARCHAR(50)   NOT NULL COMMENT '拜访单编号（系统生成，如 SV202607210001）',
    store_id            VARCHAR(50)   NOT NULL COMMENT '拜访门店ID',
    store_name          VARCHAR(200)  DEFAULT NULL COMMENT '门店名称（冗余快照）',
    supervisor_name     VARCHAR(50)   NOT NULL COMMENT '督导姓名',
    visit_date          DATE          NOT NULL COMMENT '拜访日期',
    confirm_person_type VARCHAR(30)   NOT NULL DEFAULT 'store_manager' COMMENT '确认人类型：store_manager(店长) / owner(老板) / both(同时发送)',
    visit_status        VARCHAR(30)   NOT NULL DEFAULT 'draft' COMMENT '拜访状态：draft(草稿) / pending_confirm(待确认) / objection(异议待处理) / in_progress(跟进中) / completed(已完成)',
    confirm_status      VARCHAR(30)   NOT NULL DEFAULT 'unsent' COMMENT '确认状态：unsent(未发送) / pending(待确认) / confirmed(已确认) / objected(已提出异议)',
    confirmed_by        VARCHAR(100)  DEFAULT NULL COMMENT '实际确认人 openid',
    confirmed_at        DATETIME      DEFAULT NULL COMMENT '确认时间',
    objection_reason    VARCHAR(1000) DEFAULT NULL COMMENT '异议说明',
    -- 四段沟通记录
    last_issue_review   TEXT          DEFAULT NULL COMMENT '回顾上次访店问题与行动追踪',
    current_focus       TEXT          DEFAULT NULL COMMENT '门店当下重点关注',
    improvement_focus   TEXT          DEFAULT NULL COMMENT '本月提升重点沟通记录',
    store_feedback      TEXT          DEFAULT NULL COMMENT '门店 & 加盟商反馈与所需支持',
    -- 经营数据快照（创建拜访单时写入静态数据，后续接外部接口后改为实时获取或按需刷新）
    biz_data_snapshot   JSON          DEFAULT NULL COMMENT '门店经营数据快照：GMV/目标达成率/实收率/客单价/EBITDA/人效/QSC分数/差评率/最近3次门店评级',
    created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag            INT           DEFAULT 0 COMMENT '删除标记',
    version             INT           DEFAULT 0 COMMENT '乐观锁',
    UNIQUE INDEX idx_visit_no (visit_no),
    INDEX idx_store (store_id),
    INDEX idx_supervisor (supervisor_name),
    INDEX idx_visit_status (visit_status),
    INDEX idx_confirm_status (confirm_status),
    INDEX idx_visit_date (visit_date)
) COMMENT '督导拜访单' ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2) 督导拜访行动计划（确认后拆分为小程序待处理任务）
CREATE TABLE IF NOT EXISTS supervisor_visit_action (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    visit_id            BIGINT        NOT NULL COMMENT '关联拜访单ID',
    action_name         VARCHAR(200)  NOT NULL COMMENT '任务名称',
    target_value        VARCHAR(200)  NOT NULL COMMENT '目标值',
    specific_action     VARCHAR(1000) NOT NULL COMMENT '具体动作',
    tracking_time       DATE          NOT NULL COMMENT '追踪时间（任务截止时间，退回时督导可调整）',
    responsible_person  VARCHAR(100)  NOT NULL COMMENT '负责人 openid',
    responsible_role    VARCHAR(30)   NOT NULL COMMENT '负责人角色：store_manager / owner',
    task_status         VARCHAR(30)   NOT NULL DEFAULT 'pending' COMMENT '任务状态：pending(待执行) / overdue(已逾期) / pending_review(待审核) / returned(已退回) / completed(已完成)',
    complete_note       VARCHAR(1000) DEFAULT NULL COMMENT '完成说明',
    complete_images     TEXT          DEFAULT NULL COMMENT '完成图片URL（逗号分隔）',
    submitted_at        DATETIME      DEFAULT NULL COMMENT '完成反馈提交时间',
    review_result       VARCHAR(30)   DEFAULT NULL COMMENT '审核结果：approved(通过) / returned(退回)',
    return_reason       VARCHAR(500)  DEFAULT NULL COMMENT '退回原因',
    reviewed_by         VARCHAR(100)  DEFAULT NULL COMMENT '审核人 openid',
    reviewed_at         DATETIME      DEFAULT NULL COMMENT '审核时间',
    created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag            INT           DEFAULT 0 COMMENT '删除标记',
    version             INT           DEFAULT 0 COMMENT '乐观锁',
    INDEX idx_visit (visit_id),
    INDEX idx_task_status (task_status),
    INDEX idx_responsible (responsible_person),
    INDEX idx_tracking_time (tracking_time)
) COMMENT '督导拜访行动计划' ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 3) 校验
-- SELECT 'supervisor_visit + supervisor_visit_action created' AS status;
-- SHOW CREATE TABLE supervisor_visit;
-- SHOW CREATE TABLE supervisor_visit_action;
