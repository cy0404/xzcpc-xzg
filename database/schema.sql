-- 盘点工具 1.0 — 建库建表

CREATE DATABASE IF NOT EXISTS store_inventory
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE store_inventory;

-- 1. 物料主数据
CREATE TABLE material (
    material_id    INT AUTO_INCREMENT PRIMARY KEY,
    material_name  VARCHAR(200)  NOT NULL COMMENT '物料名称',
    spec           VARCHAR(100)  NOT NULL DEFAULT '' COMMENT '规格',
    unit           VARCHAR(50)   NOT NULL COMMENT '最小规格单位',
    inventory_unit VARCHAR(50)   NOT NULL COMMENT '盘点单位',
    status         TINYINT       NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '物料主数据';

-- 2. 标准模板
CREATE TABLE template (
    template_id    INT AUTO_INCREMENT PRIMARY KEY,
    template_name  VARCHAR(200)  NOT NULL COMMENT '模板名称',
    status         TINYINT       NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '标准分区模板';

-- 3. 模板分区
CREATE TABLE template_zone (
    zone_id        INT AUTO_INCREMENT PRIMARY KEY,
    template_id    INT           NOT NULL COMMENT '关联模板',
    zone_name      VARCHAR(100)  NOT NULL COMMENT '分区名称',
    sort_no        INT           DEFAULT NULL COMMENT '排序号',
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (template_id) REFERENCES template(template_id)
) COMMENT '模板分区';

-- 4. 模板分区物料关系
CREATE TABLE template_zone_material (
    id             INT AUTO_INCREMENT PRIMARY KEY,
    zone_id        INT           NOT NULL COMMENT '关联分区',
    material_id    VARCHAR(50)   NOT NULL COMMENT '关联物料',
    material_name  VARCHAR(200)  NOT NULL DEFAULT '' COMMENT '物料名称快照',
    spec           VARCHAR(100)  NOT NULL DEFAULT '' COMMENT '规格快照',
    inventory_unit VARCHAR(50)   NOT NULL DEFAULT '' COMMENT '盘点单位快照',
    sort_no        INT           DEFAULT NULL COMMENT '排序号',
    FOREIGN KEY (zone_id) REFERENCES template_zone(zone_id),
    UNIQUE KEY uk_zone_material (zone_id, material_id)
) COMMENT '模板分区物料关系';

-- 5. 门店默认分区物料清单
CREATE TABLE store_zone_material (
    id                INT AUTO_INCREMENT PRIMARY KEY,
    store_id          VARCHAR(50)   NOT NULL COMMENT '门店ID',
    zone_name         VARCHAR(100)  NOT NULL COMMENT '分区名称',
    material_id       INT           NOT NULL COMMENT '物料ID',
    sort_no           INT           DEFAULT NULL COMMENT '排序号',
    status            TINYINT       NOT NULL DEFAULT 1 COMMENT '1有效 0已移除',
    source_type       VARCHAR(50)   NOT NULL DEFAULT '' COMMENT '来源类型',
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (material_id) REFERENCES material(material_id),
    UNIQUE KEY uk_store_zone_material (store_id, zone_name, material_id)
) COMMENT '门店默认分区物料清单，任务提交后自动更新';

-- 6. 月盘任务
CREATE TABLE task (
    task_id        INT AUTO_INCREMENT PRIMARY KEY,
    store_id       VARCHAR(50)   NOT NULL COMMENT '门店ID',
    store_name     VARCHAR(200)  DEFAULT NULL COMMENT '门店名称快照',
    store_code     VARCHAR(50)   DEFAULT NULL COMMENT '门店编码快照',
    xiaochengxuid  VARCHAR(100)  DEFAULT NULL COMMENT '小程序UID快照',
    warehouse_code VARCHAR(50)   DEFAULT NULL COMMENT '仓库编码快照',
    template_id    INT           DEFAULT NULL COMMENT '关联模板ID',
    task_name      VARCHAR(200)  NOT NULL COMMENT '任务名称',
    task_month     VARCHAR(20)   NOT NULL COMMENT '盘点月份',
    deadline       DATETIME      NOT NULL COMMENT '截止时间',
    status         VARCHAR(20)   NOT NULL DEFAULT 'not_started' COMMENT 'not_started|in_progress|submitted',
    created_by     VARCHAR(100)  NOT NULL DEFAULT '' COMMENT '创建人',
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_by   VARCHAR(100)  DEFAULT NULL COMMENT '提交人',
    submitted_at   DATETIME      DEFAULT NULL COMMENT '提交时间'
) COMMENT '月盘任务';

-- 7. 任务分区快照
CREATE TABLE task_zone (
    task_zone_id   INT AUTO_INCREMENT PRIMARY KEY,
    task_id        INT           NOT NULL COMMENT '关联任务',
    zone_name      VARCHAR(100)  NOT NULL COMMENT '分区名称快照',
    sort_no        INT           DEFAULT NULL COMMENT '排序号',
    source_type    VARCHAR(50)   NOT NULL DEFAULT '' COMMENT '来源类型',
    zone_saved     TINYINT       NOT NULL DEFAULT 0 COMMENT '0未保存 1已保存（店长点过保存本分区）',
    FOREIGN KEY (task_id) REFERENCES task(task_id)
) COMMENT '任务分区快照';

-- 8. 任务分区物料快照
CREATE TABLE task_zone_material (
    task_zone_material_id  INT AUTO_INCREMENT PRIMARY KEY,
    task_id                INT           NOT NULL COMMENT '关联任务',
    task_zone_id           INT           NOT NULL COMMENT '关联任务分区',
    material_id            VARCHAR(50)   NOT NULL COMMENT '物料ID',
    material_name  VARCHAR(200)  NOT NULL COMMENT '物料名称',
    spec           VARCHAR(100)  NOT NULL DEFAULT '' COMMENT '规格',
    unit           VARCHAR(50)   NOT NULL COMMENT '单位',
    inventory_unit VARCHAR(50)   NOT NULL DEFAULT '' COMMENT '盘点单位',
    sort_no                 INT           DEFAULT NULL COMMENT '排序号',
    input_qty              DECIMAL(10,2) DEFAULT NULL COMMENT '录入数量',
    remark                 VARCHAR(500)  DEFAULT NULL COMMENT '备注',
    input_status           VARCHAR(20)   NOT NULL DEFAULT 'not_entered' COMMENT 'not_entered|entered|zero_entered',
    FOREIGN KEY (task_id) REFERENCES task(task_id),
    FOREIGN KEY (task_zone_id) REFERENCES task_zone(task_zone_id),
    UNIQUE KEY uk_task_zone_material (task_zone_id, material_id)
) COMMENT '任务分区物料快照';

-- 9. 任务物料汇总
CREATE TABLE task_material_summary (
    id             INT AUTO_INCREMENT PRIMARY KEY,
    task_id        INT           NOT NULL COMMENT '关联任务',
    material_id    VARCHAR(50)   NOT NULL COMMENT '物料ID',
    total_qty      DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '跨分区汇总数量',
    FOREIGN KEY (task_id) REFERENCES task(task_id),
    UNIQUE KEY uk_task_material (task_id, material_id)
) COMMENT '任务物料汇总';

-- 10. 小程序店长会话（微信授权后落库，登录不再校验手机号）
CREATE TABLE store_manager_session (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    openid          VARCHAR(100)  NOT NULL COMMENT '微信 openid',
    unionid         VARCHAR(100)  DEFAULT NULL COMMENT '微信 unionid（可选）',
    session_key     VARCHAR(200)  DEFAULT NULL COMMENT '微信 session_key',
    wx_nickname     VARCHAR(100)  DEFAULT NULL COMMENT '微信昵称',
    store_id        VARCHAR(50)   DEFAULT NULL COMMENT '已绑定的门店ID',
    store_name      VARCHAR(200)  DEFAULT NULL COMMENT '已绑定的门店名称（冗余便于展示）',
    token           VARCHAR(500)  DEFAULT NULL COMMENT '当前有效的 JWT',
    last_login_at   DATETIME      DEFAULT NULL COMMENT '最近登录时间',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_openid (openid),
    INDEX idx_store_id (store_id)
) COMMENT '小程序店长会话';

-- 11. 总部端飞书用户权限
CREATE TABLE admin_permission (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    open_id         VARCHAR(100)  NOT NULL COMMENT '飞书 open_id',
    name            VARCHAR(100)  NOT NULL DEFAULT '' COMMENT '飞书姓名',
    avatar_url      VARCHAR(500)  NOT NULL DEFAULT '' COMMENT '头像',
    email           VARCHAR(200)  NOT NULL DEFAULT '' COMMENT '邮箱',
    mobile          VARCHAR(50)   NOT NULL DEFAULT '' COMMENT '手机号',
    role            VARCHAR(50)   NOT NULL DEFAULT 'normal_user' COMMENT 'headquarters_admin|finance_admin|hr_admin|operation_admin|normal_user',
    authorized_at   DATETIME      DEFAULT NULL COMMENT '最近授权时间',
    last_login_at   DATETIME      DEFAULT NULL COMMENT '最近登录时间',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag        INT           DEFAULT 0 COMMENT '删除标记 0正常 1删除',
    version         INT           DEFAULT 0 COMMENT '乐观锁版本号',
    UNIQUE KEY uk_admin_permission_open_id (open_id),
    INDEX idx_admin_permission_name (name),
    INDEX idx_admin_permission_role (role)
) COMMENT '总部端飞书用户权限';

-- 12. 门店表扩展字段（如 store_info 表已存在则执行 ALTER）
-- ALTER TABLE store_info ADD COLUMN owner_phone VARCHAR(20) DEFAULT NULL COMMENT '老板手机号';
-- ALTER TABLE store_info ADD COLUMN owner_birthday DATE DEFAULT NULL COMMENT '老板生日';
-- ALTER TABLE store_info ADD COLUMN owner_openid VARCHAR(128) DEFAULT NULL COMMENT '绑定的微信openid';

-- 13. 老板微信绑定申请
CREATE TABLE IF NOT EXISTS owner_bind_application (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    bind_code       VARCHAR(64)   NOT NULL COMMENT '一次性绑定码（二维码携带）',
    wechat_openid   VARCHAR(128)  DEFAULT NULL COMMENT '微信openid（提交时回填）',
    name            VARCHAR(50)   DEFAULT NULL COMMENT '老板姓名（提交时回填）',
    phone           VARCHAR(20)   DEFAULT NULL COMMENT '老板手机号（提交时回填）',
    birthday        DATE          DEFAULT NULL COMMENT '老板生日（提交时回填）',
    match_store_ids VARCHAR(500)  DEFAULT NULL COMMENT '自动匹配到的门店ID（逗号分隔，为空则需人工审核）',
    bind_status     VARCHAR(20)   NOT NULL DEFAULT 'pending' COMMENT 'pending|auto_bound|approved|rejected',
    auto_bound      TINYINT       NOT NULL DEFAULT 0 COMMENT '0人工审核 1自动绑定',
    approved_by     BIGINT        DEFAULT NULL COMMENT '总部审核人ID',
    approved_at     DATETIME      DEFAULT NULL COMMENT '审核时间',
    reject_reason   VARCHAR(500)  DEFAULT NULL COMMENT '拒绝原因',
    expire_at       DATETIME      NOT NULL COMMENT '二维码过期时间（24小时）',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_bind_code (bind_code),
    INDEX idx_wechat_openid (wechat_openid),
    INDEX idx_bind_status (bind_status),
    INDEX idx_phone_birthday (phone, birthday)
) COMMENT '老板微信绑定申请';

-- 14. 支出项目（二级分类）
CREATE TABLE IF NOT EXISTS expense_item (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    item_id    VARCHAR(32)  NOT NULL COMMENT '项目ID',
    type_id    VARCHAR(32)  NOT NULL COMMENT '关联支出类型ID',
    name       VARCHAR(100) NOT NULL COMMENT '项目名称',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag   INT          DEFAULT 0 COMMENT '删除标记',
    INDEX idx_type_id (type_id)
) COMMENT '支出项目';

-- 15. 支出记录补充项目字段（如果表已存在）
-- ALTER TABLE expense_record ADD COLUMN item_id VARCHAR(32) DEFAULT NULL AFTER type_name;
-- ALTER TABLE expense_record ADD COLUMN item_name VARCHAR(100) DEFAULT NULL AFTER item_id;
