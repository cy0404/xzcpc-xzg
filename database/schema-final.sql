-- ============================================================
-- 盘点工具 1.0 — 最终版建库建表
-- 包含：统一自增ID + 业务编码 + 逻辑删除 + 乐观锁
-- ============================================================

CREATE DATABASE IF NOT EXISTS store_Inventory
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE store_Inventory;

-- ============================================================
-- 1. 物料主数据
-- ============================================================
CREATE TABLE material (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    biz_code        VARCHAR(50)   NOT NULL DEFAULT '' COMMENT '业务编码',
    material_id     INT           DEFAULT 0 COMMENT '物料ID（旧主键，保留兼容）',
    material_name   VARCHAR(200)  NOT NULL COMMENT '物料名称',
    spec            VARCHAR(100)  NOT NULL DEFAULT '' COMMENT '规格',
    unit            VARCHAR(50)   NOT NULL COMMENT '最小规格单位',
    inventory_unit  VARCHAR(50)   NOT NULL COMMENT '盘点单位',
    status          TINYINT       NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag        INT           DEFAULT 0 COMMENT '删除标记 0正常 1删除',
    version         INT           DEFAULT 0 COMMENT '乐观锁版本号'
) COMMENT '物料主数据';

-- ============================================================
-- 2. 标准模板
-- ============================================================
CREATE TABLE template (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    biz_code        VARCHAR(50)   NOT NULL DEFAULT '' COMMENT '业务编码',
    template_id     INT           DEFAULT 0 COMMENT '模板ID（旧主键）',
    template_name   VARCHAR(200)  NOT NULL COMMENT '模板名称',
    status          TINYINT       NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag        INT           DEFAULT 0 COMMENT '删除标记 0正常 1删除',
    version         INT           DEFAULT 0 COMMENT '乐观锁版本号'
) COMMENT '标准分区模板';

-- ============================================================
-- 3. 模板分区
-- ============================================================
CREATE TABLE template_zone (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    biz_code        VARCHAR(50)   NOT NULL DEFAULT '' COMMENT '业务编码',
    zone_id         INT           DEFAULT 0 COMMENT '分区ID（旧主键）',
    template_id     INT           NOT NULL COMMENT '关联模板ID',
    zone_name       VARCHAR(100)  NOT NULL COMMENT '分区名称',
    sort_no         INT           DEFAULT NULL COMMENT '排序号',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    del_flag        INT           DEFAULT 0 COMMENT '删除标记 0正常 1删除',
    version         INT           DEFAULT 0 COMMENT '乐观锁版本号',
    FOREIGN KEY (template_id) REFERENCES template(id)
) COMMENT '模板分区';

-- ============================================================
-- 4. 模板分区物料关系
-- ============================================================
CREATE TABLE template_zone_material (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    biz_code        VARCHAR(50)   NOT NULL DEFAULT '' COMMENT '业务编码',
    zone_id         INT           NOT NULL COMMENT '关联分区ID',
    material_id     VARCHAR(50)   NOT NULL COMMENT '关联物料ID',
    material_name   VARCHAR(200)  NOT NULL DEFAULT '' COMMENT '物料名称快照',
    spec            VARCHAR(100)  NOT NULL DEFAULT '' COMMENT '规格快照',
    inventory_unit  VARCHAR(50)   NOT NULL DEFAULT '' COMMENT '盘点单位快照',
    sort_no         INT           DEFAULT NULL COMMENT '排序号',
    del_flag        INT           DEFAULT 0 COMMENT '删除标记 0正常 1删除',
    version         INT           DEFAULT 0 COMMENT '乐观锁版本号',
    FOREIGN KEY (zone_id) REFERENCES template_zone(id),
    UNIQUE KEY uk_zone_material (zone_id, material_id)
) COMMENT '模板分区物料关系';

-- ============================================================
-- 5. 门店默认分区物料清单
-- ============================================================
CREATE TABLE store_zone_material (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    biz_code        VARCHAR(50)   NOT NULL DEFAULT '' COMMENT '业务编码',
    store_id        VARCHAR(50)   NOT NULL COMMENT '门店ID',
    zone_name       VARCHAR(100)  NOT NULL COMMENT '分区名称',
    material_id     INT           NOT NULL COMMENT '物料ID（关联 material.id）',
    sort_no         INT           DEFAULT NULL COMMENT '排序号',
    status          TINYINT       NOT NULL DEFAULT 1 COMMENT '1有效 0已移除',
    source_type     VARCHAR(50)   NOT NULL DEFAULT '' COMMENT '来源类型',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag        INT           DEFAULT 0 COMMENT '删除标记 0正常 1删除',
    version         INT           DEFAULT 0 COMMENT '乐观锁版本号',
    FOREIGN KEY (material_id) REFERENCES material(id),
    UNIQUE KEY uk_store_zone_material (store_id, zone_name, material_id)
) COMMENT '门店默认分区物料清单，任务提交后自动更新';

-- ============================================================
-- 6. 月盘任务
-- ============================================================
CREATE TABLE task (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    biz_code        VARCHAR(50)   NOT NULL DEFAULT '' COMMENT '业务编码',
    task_id         INT           DEFAULT 0 COMMENT '任务ID（旧主键）',
    store_id        VARCHAR(50)   NOT NULL COMMENT '门店ID',
    store_name      VARCHAR(200)  DEFAULT NULL COMMENT '门店名称快照',
    store_code      VARCHAR(50)   DEFAULT NULL COMMENT '门店编码快照',
    xiaochengxuid   VARCHAR(100)  DEFAULT NULL COMMENT '小程序UID快照',
    warehouse_code  VARCHAR(50)   DEFAULT NULL COMMENT '仓库编码快照',
    template_id     INT           DEFAULT NULL COMMENT '关联模板ID',
    task_name       VARCHAR(200)  NOT NULL COMMENT '任务名称',
    task_month      VARCHAR(20)   NOT NULL COMMENT '盘点月份',
    deadline        DATETIME      NOT NULL COMMENT '截止时间',
    status          VARCHAR(20)   NOT NULL DEFAULT 'not_started' COMMENT 'not_started|in_progress|submitted',
    created_by      VARCHAR(100)  NOT NULL DEFAULT '' COMMENT '创建人',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_by    VARCHAR(100)  DEFAULT NULL COMMENT '提交人',
    submitted_at    DATETIME      DEFAULT NULL COMMENT '提交时间',
    del_flag        INT           DEFAULT 0 COMMENT '删除标记 0正常 1删除',
    version         INT           DEFAULT 0 COMMENT '乐观锁版本号'
) COMMENT '月盘任务';

-- ============================================================
-- 7. 任务分区快照
-- ============================================================
CREATE TABLE task_zone (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    biz_code        VARCHAR(50)   NOT NULL DEFAULT '' COMMENT '业务编码',
    task_zone_id    INT           DEFAULT 0 COMMENT '分区ID（旧主键）',
    task_id         INT           NOT NULL COMMENT '关联任务ID',
    zone_name       VARCHAR(100)  NOT NULL COMMENT '分区名称快照',
    sort_no         INT           DEFAULT NULL COMMENT '排序号',
    source_type     VARCHAR(50)   NOT NULL DEFAULT '' COMMENT '来源类型',
    zone_saved      TINYINT       NOT NULL DEFAULT 0 COMMENT '0未保存 1已保存',
    del_flag        INT           DEFAULT 0 COMMENT '删除标记 0正常 1删除',
    version         INT           DEFAULT 0 COMMENT '乐观锁版本号',
    FOREIGN KEY (task_id) REFERENCES task(id)
) COMMENT '任务分区快照';

-- ============================================================
-- 8. 任务分区物料快照
-- ============================================================
CREATE TABLE task_zone_material (
    id                          INT AUTO_INCREMENT PRIMARY KEY,
    biz_code                    VARCHAR(50)   NOT NULL DEFAULT '' COMMENT '业务编码',
    task_zone_material_id       INT           DEFAULT 0 COMMENT '快照ID（旧主键）',
    task_id                     INT           NOT NULL COMMENT '关联任务ID',
    task_zone_id                INT           NOT NULL COMMENT '关联任务分区ID',
    material_id                 VARCHAR(50)   NOT NULL COMMENT '物料ID',
    material_name_snapshot      VARCHAR(200)  NOT NULL COMMENT '物料名称快照',
    spec_snapshot               VARCHAR(100)  NOT NULL DEFAULT '' COMMENT '规格快照',
    unit_snapshot               VARCHAR(50)   NOT NULL COMMENT '单位快照',
    inventory_unit_snapshot     VARCHAR(50)   NOT NULL DEFAULT '' COMMENT '盘点单位快照',
    sort_no                     INT           DEFAULT NULL COMMENT '排序号',
    input_qty                   DECIMAL(10,2) DEFAULT NULL COMMENT '录入数量',
    remark                      VARCHAR(500)  DEFAULT NULL COMMENT '备注',
    input_status                VARCHAR(20)   NOT NULL DEFAULT 'not_entered' COMMENT 'not_entered|entered|zero_entered',
    del_flag                    INT           DEFAULT 0 COMMENT '删除标记 0正常 1删除',
    version                     INT           DEFAULT 0 COMMENT '乐观锁版本号',
    FOREIGN KEY (task_id) REFERENCES task(id),
    FOREIGN KEY (task_zone_id) REFERENCES task_zone(id),
    UNIQUE KEY uk_task_zone_material (task_zone_id, material_id)
) COMMENT '任务分区物料快照';

-- ============================================================
-- 9. 任务物料汇总
-- ============================================================
CREATE TABLE task_material_summary (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    biz_code        VARCHAR(50)   NOT NULL DEFAULT '' COMMENT '业务编码',
    task_id         INT           NOT NULL COMMENT '关联任务ID',
    material_id     VARCHAR(50)   NOT NULL COMMENT '物料ID',
    total_qty       DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '跨分区汇总数量',
    del_flag        INT           DEFAULT 0 COMMENT '删除标记 0正常 1删除',
    version         INT           DEFAULT 0 COMMENT '乐观锁版本号',
    FOREIGN KEY (task_id) REFERENCES task(id),
    UNIQUE KEY uk_task_material (task_id, material_id)
) COMMENT '任务物料汇总';

-- ============================================================
-- 10. 小程序店长会话
-- ============================================================
CREATE TABLE store_manager_session (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    biz_code        VARCHAR(50)   NOT NULL DEFAULT '' COMMENT '业务编码',
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
    del_flag        INT           DEFAULT 0 COMMENT '删除标记 0正常 1删除',
    UNIQUE KEY uk_openid (openid),
    INDEX idx_store_id (store_id)
) COMMENT '小程序店长会话';

-- ============================================================
-- 11. 总部端飞书用户权限
-- ============================================================
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
