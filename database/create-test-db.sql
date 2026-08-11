-- ============================================================
-- 象掌柜 测试库 — 一键建库
-- 执行方式: mysql -h 162.14.122.80 -P 3306 -u root -p < create-test-db.sql
-- ============================================================

-- 1. 建测试库
CREATE DATABASE IF NOT EXISTS store_inventory_test
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

-- 2. 授权（用 root 执行）
GRANT ALL PRIVILEGES ON store_inventory_test.* TO 'store_inventory'@'%';
FLUSH PRIVILEGES;

USE store_inventory_test;

-- ============================================================
-- 以下从 schema.sql + V2__P0_schema.sql 合并
-- ============================================================

-- ============================================================
-- 一、物料模块
-- ============================================================

CREATE TABLE material (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    material_id     VARCHAR(50)   DEFAULT NULL COMMENT '物料ID（外部系统编码）',
    qm_code         VARCHAR(100)  DEFAULT NULL COMMENT '企迈编码',
    parent_category VARCHAR(100)  DEFAULT NULL COMMENT '父分类',
    category        VARCHAR(100)  DEFAULT NULL COMMENT '分类',
    material_name   VARCHAR(200)  NOT NULL COMMENT '物料名称',
    spec            VARCHAR(100)  NOT NULL DEFAULT '' COMMENT '规格',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag        INT           DEFAULT 0 COMMENT '删除标记 0正常 1删除',
    qr_code         VARCHAR(500)  DEFAULT NULL COMMENT '物料二维码图片路径',
    INDEX idx_material_material_id (material_id)
) COMMENT '物料主数据';

CREATE TABLE material_inventory_rule (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_id         VARCHAR(50)   NOT NULL COMMENT '规则业务ID',
    material_id     VARCHAR(50)   NOT NULL COMMENT '关联物料ID',
    base_unit       VARCHAR(50)   NOT NULL COMMENT '基础盘点单位',
    inventory_units VARCHAR(500)  DEFAULT NULL COMMENT '盘点单位串',
    stock_unit      VARCHAR(50)   DEFAULT NULL COMMENT '库存单位',
    unit_price      DECIMAL(10,2) DEFAULT NULL COMMENT '单价',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag        INT           DEFAULT 0 COMMENT '删除标记',
    INDEX idx_rule_material_id (material_id)
) COMMENT '物料盘点规则';

CREATE TABLE material_conversion_rule (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_id         VARCHAR(50)     NOT NULL COMMENT '规则业务ID',
    conversion_type VARCHAR(20)     NOT NULL COMMENT '换算类型：unit 单位换算 / weight 称重换算',
    from_quantity   DECIMAL(18,4)   NOT NULL COMMENT '从数量',
    from_unit       VARCHAR(50)     NOT NULL COMMENT '从单位',
    to_quantity     DECIMAL(18,4)   NOT NULL COMMENT '到数量',
    to_unit         VARCHAR(50)     NOT NULL COMMENT '到单位',
    sort_no         INT             DEFAULT NULL COMMENT '排序号',
    del_flag        INT             DEFAULT 0 COMMENT '删除标记',
    INDEX idx_rule_id (rule_id)
) COMMENT '物料换算关系';

-- ============================================================
-- 二、模板模块
-- ============================================================

CREATE TABLE template (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    biz_code        VARCHAR(50)   NOT NULL DEFAULT '' COMMENT '业务编码',
    template_name   VARCHAR(200)  NOT NULL COMMENT '模板名称',
    status          TINYINT       NOT NULL DEFAULT 1 COMMENT '1启用 0停用 2草稿',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag        INT           DEFAULT 0 COMMENT '删除标记 0正常 1删除',
    version         INT           DEFAULT 0 COMMENT '乐观锁版本号'
) COMMENT '标准分区模板';

CREATE TABLE template_zone (
    id              INT AUTO_INCREMENT PRIMARY KEY COMMENT '分区ID（主键）',
    template_id     INT           NOT NULL COMMENT '关联模板ID',
    zone_name       VARCHAR(100)  NOT NULL COMMENT '分区名称',
    sort_no         INT           DEFAULT NULL COMMENT '排序号',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    del_flag        INT           DEFAULT 0 COMMENT '删除标记',
    biz_code        VARCHAR(50)   NOT NULL DEFAULT '' COMMENT '业务编码',
    version         INT           DEFAULT 0 COMMENT '乐观锁版本号',
    FOREIGN KEY (template_id) REFERENCES template(id)
) COMMENT '模板分区';

CREATE TABLE template_zone_material (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    zone_id         INT           NOT NULL COMMENT '关联分区ID',
    material_id     VARCHAR(50)   NOT NULL COMMENT '关联物料ID',
    material_name   VARCHAR(200)  NOT NULL DEFAULT '' COMMENT '物料名称快照',
    spec            VARCHAR(100)  NOT NULL DEFAULT '' COMMENT '规格快照',
    inventory_unit  VARCHAR(50)   NOT NULL DEFAULT '' COMMENT '盘点单位快照',
    sort_no         INT           DEFAULT NULL COMMENT '排序号',
    del_flag        INT           DEFAULT 0 COMMENT '删除标记',
    biz_code        VARCHAR(50)   NOT NULL DEFAULT '' COMMENT '业务编码',
    version         INT           DEFAULT 0 COMMENT '乐观锁版本号',
    FOREIGN KEY (zone_id) REFERENCES template_zone(id),
    UNIQUE KEY uk_zone_material (zone_id, material_id),
    INDEX idx_zone_del_sort (zone_id, del_flag, sort_no)
) COMMENT '模板分区物料关系';

-- ============================================================
-- 三、任务模块
-- ============================================================

CREATE TABLE task (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    biz_code        VARCHAR(50)   NOT NULL DEFAULT '' COMMENT '业务编码',
    store_id        VARCHAR(50)   NOT NULL COMMENT '门店ID',
    store_name      VARCHAR(200)  DEFAULT NULL COMMENT '门店名称快照',
    store_code      VARCHAR(50)   DEFAULT NULL COMMENT '门店编码快照',
    xiaochengxuid   VARCHAR(100)  DEFAULT NULL COMMENT '小程序UID快照',
    warehouse_code  VARCHAR(50)   DEFAULT NULL COMMENT '仓库编码快照',
    template_id     INT           DEFAULT NULL COMMENT '关联模板ID',
    task_name       VARCHAR(200)  NOT NULL COMMENT '任务名称',
    task_month      VARCHAR(20)   NOT NULL COMMENT '盘点月份',
    deadline        DATETIME      NOT NULL COMMENT '截止时间',
    status          VARCHAR(20)   NOT NULL DEFAULT 'not_started' COMMENT 'not_started|in_progress|pending_submit|submitted|overdue',
    created_by      VARCHAR(100)  NOT NULL DEFAULT '' COMMENT '创建人',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_by    VARCHAR(100)  DEFAULT NULL COMMENT '提交人',
    submitted_at    DATETIME      DEFAULT NULL COMMENT '提交时间',
    del_flag        INT           DEFAULT 0 COMMENT '删除标记',
    version         INT           DEFAULT 0 COMMENT '乐观锁版本号',
    INDEX idx_task_store_id (store_id)
) COMMENT '月盘任务';

CREATE TABLE task_zone (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    biz_code        VARCHAR(50)   NOT NULL DEFAULT '' COMMENT '业务编码',
    task_id         INT           NOT NULL COMMENT '关联任务ID',
    zone_name       VARCHAR(100)  NOT NULL COMMENT '分区名称快照',
    sort_no         INT           DEFAULT NULL COMMENT '排序号',
    source_type     VARCHAR(50)   NOT NULL DEFAULT '' COMMENT '来源类型',
    zone_saved      TINYINT       NOT NULL DEFAULT 0 COMMENT '0未保存 1已保存',
    saved_at        DATETIME      DEFAULT NULL COMMENT '分区保存时间',
    del_flag        INT           DEFAULT 0 COMMENT '删除标记',
    version         INT           DEFAULT 0 COMMENT '乐观锁版本号',
    FOREIGN KEY (task_id) REFERENCES task(id)
) COMMENT '任务分区快照';

CREATE TABLE task_zone_material (
    id                  INT AUTO_INCREMENT PRIMARY KEY,
    biz_code            VARCHAR(50)     NOT NULL DEFAULT '',
    task_id             INT             NOT NULL,
    task_zone_id        INT             NOT NULL,
    material_id         VARCHAR(50)     NOT NULL,
    material_name       VARCHAR(200)    NOT NULL,
    spec                VARCHAR(100)    NOT NULL DEFAULT '',
    unit                VARCHAR(50)     NOT NULL,
    inventory_unit      VARCHAR(50)     NOT NULL DEFAULT '',
    sort_no             INT             DEFAULT NULL,
    input_qty               DECIMAL(10,2) DEFAULT NULL,
    remark                  VARCHAR(500)  DEFAULT NULL,
    input_status            VARCHAR(20)   NOT NULL DEFAULT 'not_entered',
    input_mode              VARCHAR(20)    DEFAULT NULL,
    input_original_qty      DECIMAL(18,4)  DEFAULT NULL,
    input_original_unit     VARCHAR(50)    DEFAULT NULL,
    unit_inputs             TEXT           DEFAULT NULL,
    base_unit_snapshot      VARCHAR(50)    DEFAULT NULL,
    rule_id_snapshot        VARCHAR(50)    DEFAULT NULL,
    base_qty                DECIMAL(18,4)  DEFAULT NULL,
    conversion_snapshot     TEXT           DEFAULT NULL,
    unit_price_snapshot     DECIMAL(10,2)  DEFAULT NULL,
    entered_at              DATETIME       DEFAULT NULL,
    del_flag            INT             DEFAULT 0,
    version             INT             DEFAULT 0,
    FOREIGN KEY (task_id) REFERENCES task(id),
    FOREIGN KEY (task_zone_id) REFERENCES task_zone(id),
    UNIQUE KEY uk_task_zone_material (task_zone_id, material_id),
    INDEX idx_tzm_task_id (task_id)
) COMMENT '任务分区物料快照';

CREATE TABLE task_material_summary (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    biz_code        VARCHAR(50)     NOT NULL DEFAULT '',
    task_id         INT             NOT NULL,
    material_id     VARCHAR(50)     NOT NULL,
    material_name   VARCHAR(200)    DEFAULT NULL,
    spec            VARCHAR(100)    DEFAULT NULL,
    base_unit       VARCHAR(50)     DEFAULT NULL,
    total_qty       DECIMAL(10,2)   NOT NULL DEFAULT 0,
    zone_count      INT             DEFAULT NULL,
    unit_breakdown  VARCHAR(500)    DEFAULT NULL,
    del_flag        INT             DEFAULT 0,
    version         INT             DEFAULT 0,
    FOREIGN KEY (task_id) REFERENCES task(id),
    UNIQUE KEY uk_task_material (task_id, material_id)
) COMMENT '任务物料汇总';

CREATE TABLE store_zone_material (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    biz_code        VARCHAR(50)   NOT NULL DEFAULT '',
    store_id        VARCHAR(50)   NOT NULL COMMENT '门店ID',
    zone_name       VARCHAR(100)  NOT NULL COMMENT '分区名称',
    material_id     BIGINT        NOT NULL COMMENT '物料ID',
    sort_no         INT           DEFAULT NULL,
    status          TINYINT       NOT NULL DEFAULT 1 COMMENT '1有效 0已移除',
    source_type     VARCHAR(50)   NOT NULL DEFAULT '',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag        INT           DEFAULT 0,
    version         INT           DEFAULT 0,
    FOREIGN KEY (material_id) REFERENCES material(id),
    UNIQUE KEY uk_store_zone_material (store_id, zone_name, material_id)
) COMMENT '门店默认分区物料清单';

-- ============================================================
-- 四、门店信息
-- ============================================================

CREATE TABLE store_info (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    store_id        VARCHAR(64)   NOT NULL COMMENT '外部API门店ID',
    store_name      VARCHAR(128)  NOT NULL COMMENT '门店名称',
    store_code      VARCHAR(64)   DEFAULT NULL COMMENT '门店编码',
    xiaochengxuid   VARCHAR(64)   DEFAULT NULL COMMENT '小程序号',
    cangkuid        VARCHAR(64)   DEFAULT NULL COMMENT '仓库ID',
    qr_code         VARCHAR(512)  DEFAULT NULL COMMENT '门店二维码',
    owner_name      VARCHAR(50)   DEFAULT NULL COMMENT '老板姓名',
    owner_phone     VARCHAR(20)   DEFAULT NULL COMMENT '老板手机号',
    owner_openid    VARCHAR(128)  DEFAULT NULL COMMENT '绑定的微信openid',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag        TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY uk_store_id (store_id),
    INDEX idx_store_name (store_name)
) COMMENT '门店信息本地表';

-- ============================================================
-- 五、认证与会话
-- ============================================================

CREATE TABLE store_manager_session (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    openid          VARCHAR(100)  NOT NULL COMMENT '微信 openid',
    unionid         VARCHAR(100)  DEFAULT NULL,
    session_key     VARCHAR(200)  DEFAULT NULL,
    wx_nickname     VARCHAR(100)  DEFAULT NULL,
    store_id        VARCHAR(50)   DEFAULT NULL,
    store_name      VARCHAR(200)  DEFAULT NULL,
    role            VARCHAR(20)   DEFAULT NULL COMMENT 'P0: store_manager|owner|staff',
    token           VARCHAR(500)  DEFAULT NULL COMMENT '当前JWT',
    last_login_at   DATETIME      DEFAULT NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag        INT           DEFAULT 0,
    UNIQUE KEY uk_openid (openid),
    INDEX idx_store_id (store_id)
) COMMENT '小程序店长会话';

CREATE TABLE admin_permission (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    open_id         VARCHAR(100)  NOT NULL COMMENT '飞书 open_id',
    name            VARCHAR(100)  NOT NULL DEFAULT '',
    avatar_url      VARCHAR(500)  NOT NULL DEFAULT '',
    email           VARCHAR(200)  NOT NULL DEFAULT '',
    mobile          VARCHAR(50)   NOT NULL DEFAULT '',
    role            VARCHAR(500)  NOT NULL DEFAULT 'normal_user',
    authorized_at   DATETIME      DEFAULT NULL,
    last_login_at   DATETIME      DEFAULT NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag        INT           DEFAULT 0,
    version         INT           DEFAULT 0,
    UNIQUE KEY uk_admin_permission_open_id (open_id),
    INDEX idx_admin_permission_name (name),
    INDEX idx_admin_permission_role (role)
) COMMENT '总部端飞书用户权限';

CREATE TABLE owner_bind_application (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    bind_code       VARCHAR(64)   NOT NULL COMMENT '一次性绑定码',
    wechat_openid   VARCHAR(128)  DEFAULT NULL,
    name            VARCHAR(50)   DEFAULT NULL,
    phone           VARCHAR(20)   DEFAULT NULL,
    birthday        DATE          DEFAULT NULL,
    match_store_ids VARCHAR(500)  DEFAULT NULL,
    bind_status     VARCHAR(20)   NOT NULL DEFAULT 'pending' COMMENT 'pending|auto_bound|approved|rejected',
    auto_bound      TINYINT       NOT NULL DEFAULT 0,
    approved_by     BIGINT        DEFAULT NULL,
    approved_at     DATETIME      DEFAULT NULL,
    reject_reason   VARCHAR(500)  DEFAULT NULL,
    expire_at       DATETIME      NOT NULL COMMENT '二维码过期时间',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_bind_code (bind_code),
    INDEX idx_wechat_openid (wechat_openid),
    INDEX idx_bind_status (bind_status),
    INDEX idx_phone_birthday (phone, birthday)
) COMMENT '老板微信绑定申请';

-- ============================================================
-- 六、支出模块
-- ============================================================

CREATE TABLE expense_type (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    type_id         VARCHAR(32)   NOT NULL COMMENT '类型业务ID',
    name            VARCHAR(100)  NOT NULL COMMENT '类型名称',
    first_type_id   VARCHAR(32)   DEFAULT NULL COMMENT '一级分类ID',
    first_type_name VARCHAR(100)  DEFAULT NULL COMMENT '一级分类名称',
    description     VARCHAR(500)  DEFAULT NULL,
    status          VARCHAR(20)   DEFAULT 'enabled',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag        INT           DEFAULT 0,
    UNIQUE KEY uk_type_id (type_id)
) COMMENT '支出类型';

CREATE TABLE expense_item (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    item_id         VARCHAR(32)   NOT NULL,
    type_id         VARCHAR(32)   NOT NULL,
    name            VARCHAR(100)  NOT NULL,
    description     VARCHAR(500)  DEFAULT NULL,
    status          VARCHAR(20)   DEFAULT 'enabled',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag        INT           DEFAULT 0,
    INDEX idx_type_id (type_id)
) COMMENT '支出项目';

CREATE TABLE expense_record (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    expense_id          VARCHAR(32)     NOT NULL,
    store_id            VARCHAR(50)     NOT NULL,
    store_miniapp_no    VARCHAR(100)    DEFAULT NULL,
    store_name          VARCHAR(200)    DEFAULT NULL,
    warehouse_code      VARCHAR(50)     DEFAULT NULL,
    type_id             VARCHAR(32)     DEFAULT NULL,
    type_name           VARCHAR(100)    DEFAULT NULL,
    first_type_id       VARCHAR(32)     DEFAULT NULL,
    first_type_name     VARCHAR(100)    DEFAULT NULL,
    item_id             VARCHAR(32)     DEFAULT NULL,
    item_name           VARCHAR(100)    DEFAULT NULL,
    amount              DECIMAL(10,2)   NOT NULL,
    occurred_date       DATE            DEFAULT NULL,
    handler_name        VARCHAR(100)    DEFAULT NULL,
    voucher_url         VARCHAR(500)    DEFAULT NULL,
    remark              VARCHAR(500)    DEFAULT NULL,
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag            INT             DEFAULT 0,
    INDEX idx_store_id (store_id),
    INDEX idx_type_id (type_id),
    INDEX idx_occurred_date (occurred_date)
) COMMENT '支出记录';

-- ============================================================
-- 七、人员模块
-- ============================================================

CREATE TABLE employee (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id             VARCHAR(32)   NOT NULL,
    name                    VARCHAR(64)   NOT NULL,
    mobile                  VARCHAR(32)   DEFAULT NULL,
    openid                  VARCHAR(64)   DEFAULT NULL,
    gender                  VARCHAR(16)   DEFAULT NULL,
    birthday                DATE          DEFAULT NULL,
    store_id                VARCHAR(50)   NOT NULL,
    store_miniapp_no        VARCHAR(100)  DEFAULT NULL,
    store_name              VARCHAR(200)  DEFAULT NULL,
    role                    VARCHAR(64)   DEFAULT NULL,
    employment_type         VARCHAR(32)   DEFAULT NULL,
    entry_date              DATE          DEFAULT NULL,
    leave_date              DATE          DEFAULT NULL,
    emergency_contact_name  VARCHAR(64)   DEFAULT NULL,
    emergency_contact_phone VARCHAR(32)   DEFAULT NULL,
    remark                  VARCHAR(500)  DEFAULT NULL,
    status                  VARCHAR(20)   DEFAULT 'active',
    created_at              DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag                INT           DEFAULT 0,
    UNIQUE KEY uk_employee_store_openid (store_id, openid),
    INDEX idx_employee_openid_status (openid, status),
    INDEX idx_employee_store_id (store_id),
    INDEX idx_employee_mobile (mobile)
) COMMENT '员工主数据';

CREATE TABLE employee_registration_application (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    application_id          VARCHAR(32)   NOT NULL,
    openid                  VARCHAR(64)   DEFAULT NULL,
    store_id                VARCHAR(32)   NOT NULL,
    store_name              VARCHAR(128)  NOT NULL,
    name                    VARCHAR(64)   NOT NULL,
    mobile                  VARCHAR(32)   NOT NULL,
    gender                  VARCHAR(16)   DEFAULT NULL,
    birthday                DATE          DEFAULT NULL,
    expected_role           VARCHAR(64)   NOT NULL,
    employment_type         VARCHAR(32)   NOT NULL,
    entry_date              DATE          NOT NULL,
    emergency_contact_name  VARCHAR(64)   DEFAULT NULL,
    emergency_contact_phone VARCHAR(32)   DEFAULT NULL,
    remark                  VARCHAR(500)  DEFAULT NULL,
    status                  VARCHAR(20)   NOT NULL DEFAULT 'pending',
    reject_reason           VARCHAR(500)  DEFAULT NULL,
    approver_openid         VARCHAR(64)   DEFAULT NULL,
    approved_at             DATETIME      DEFAULT NULL,
    employee_id             VARCHAR(32)   DEFAULT NULL,
    created_at              DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag                TINYINT       NOT NULL DEFAULT 0,
    UNIQUE KEY uk_application_id (application_id),
    INDEX idx_staff_app_store_status (store_id, status),
    INDEX idx_staff_app_openid (openid),
    INDEX idx_staff_app_mobile (mobile)
) COMMENT '员工登记申请表';

CREATE TABLE owner_registration (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    openid          VARCHAR(128)  NOT NULL,
    bind_code       VARCHAR(64)   DEFAULT NULL,
    name            VARCHAR(50)   NOT NULL,
    phone           VARCHAR(20)   NOT NULL,
    store_id        VARCHAR(50)   NOT NULL,
    store_name      VARCHAR(200)  NOT NULL,
    role            VARCHAR(20)   DEFAULT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT '未关联' COMMENT '已绑定/未关联',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_openid (openid),
    INDEX idx_store (store_id)
) COMMENT '老板扫码登记表';

CREATE TABLE store_contact (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id        VARCHAR(50)   NOT NULL,
    store_name      VARCHAR(200)  NOT NULL,
    contact_name    VARCHAR(50)   NOT NULL,
    contact_phone   VARCHAR(20)   NOT NULL,
    del_flag        TINYINT       NOT NULL DEFAULT 0,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_store_id (store_id),
    INDEX idx_phone (contact_phone)
) COMMENT '门店联系人信息';

-- ============================================================
-- 八、门店工时
-- ============================================================

CREATE TABLE store_work_hours (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_id       VARCHAR(32)   NOT NULL,
    store_id        VARCHAR(50)   NOT NULL,
    store_name      VARCHAR(200)  DEFAULT NULL,
    record_time     VARCHAR(7)    NOT NULL COMMENT 'YYYY-MM',
    hours           DECIMAL(10,2) NOT NULL,
    employee_id     VARCHAR(32)   NOT NULL,
    employee_name   VARCHAR(100)  NOT NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag        INT           DEFAULT 0,
    INDEX idx_store_id (store_id),
    INDEX idx_record_time (record_time),
    INDEX idx_store_month (store_id, record_time)
) COMMENT '门店月度工时';

-- ============================================================
-- 九、日志
-- ============================================================

CREATE TABLE operation_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         VARCHAR(128)  DEFAULT NULL,
    source          VARCHAR(50)   DEFAULT NULL COMMENT '来源：mp小程序/admin总部',
    username        VARCHAR(100)  NOT NULL DEFAULT '',
    module          VARCHAR(50)   NOT NULL DEFAULT '',
    operation       VARCHAR(50)   NOT NULL DEFAULT '',
    description     VARCHAR(500)  DEFAULT '',
    request_ip      VARCHAR(50)   DEFAULT '',
    status          TINYINT       NOT NULL DEFAULT 1,
    error_msg       VARCHAR(1000) DEFAULT '',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_username (username),
    INDEX idx_module (module),
    INDEX idx_created_at (created_at)
) COMMENT '操作日志';

CREATE TABLE login_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT        DEFAULT NULL,
    openid          VARCHAR(128)  DEFAULT NULL,
    username        VARCHAR(100)  NOT NULL DEFAULT '',
    login_type      VARCHAR(30)   NOT NULL DEFAULT '',
    status          TINYINT       NOT NULL DEFAULT 1,
    fail_reason     VARCHAR(200)  DEFAULT '',
    request_ip      VARCHAR(50)   DEFAULT '',
    user_agent      VARCHAR(500)  DEFAULT '',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_username (username),
    INDEX idx_login_type (login_type),
    INDEX idx_created_at (created_at)
) COMMENT '登录日志';

-- ============================================================
-- 十、自购物料采购
-- ============================================================

CREATE TABLE self_purchase_material (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    biz_code         VARCHAR(50)   DEFAULT NULL,
    store_id         VARCHAR(50)   NOT NULL,
    store_name       VARCHAR(200)  DEFAULT NULL,
    store_miniapp_no VARCHAR(100)  DEFAULT NULL,
    material_id      VARCHAR(50)   DEFAULT NULL,
    material_code    VARCHAR(100)  DEFAULT NULL,
    parent_category  VARCHAR(100)  NOT NULL,
    category         VARCHAR(100)  NOT NULL,
    material_name    VARCHAR(200)  NOT NULL,
    unit             VARCHAR(20)   NOT NULL DEFAULT '',
    purchase_month   VARCHAR(7)    NOT NULL,
    purchase_date    DATE          DEFAULT NULL,
    purchase_qty     DECIMAL(10,2) NOT NULL DEFAULT 0,
    unit_price       DECIMAL(10,2) DEFAULT NULL,
    total_amount     DECIMAL(10,2) DEFAULT NULL,
    handler_name     VARCHAR(50)   DEFAULT NULL,
    voucher_url      VARCHAR(500)  DEFAULT NULL,
    remark           VARCHAR(500)  DEFAULT NULL,
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag         INT           DEFAULT 0,
    UNIQUE KEY uk_spm_biz_code (biz_code),
    INDEX idx_spm_store_id (store_id),
    INDEX idx_spm_store_month (store_id, purchase_month),
    INDEX idx_spm_parent_category (parent_category),
    INDEX idx_spm_category (category),
    INDEX idx_spm_purchase_month (purchase_month)
) COMMENT '自购物料采购表';

-- ============================================================
-- 十一、P0 新增表（V2 迁移）
-- ============================================================

-- P0: store_manager_session 已包含 role 字段（见上面建表语句）

-- A1 条码补充申请
CREATE TABLE barcode_supplement (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    barcode         VARCHAR(100)  NOT NULL,
    material_name   VARCHAR(200)  DEFAULT NULL,
    store_id        VARCHAR(50)   NOT NULL,
    submitted_by    VARCHAR(100)  DEFAULT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'pending',
    remark          VARCHAR(500)  DEFAULT NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag        INT           DEFAULT 0,
    INDEX idx_barcode (barcode),
    INDEX idx_store (store_id)
) COMMENT '条码补充申请';

-- A1 容器配置
CREATE TABLE container_config (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    container_name  VARCHAR(100)  NOT NULL,
    tare_weight     DECIMAL(10,2) NOT NULL COMMENT '皮重（克）',
    store_id        VARCHAR(50)   DEFAULT NULL COMMENT 'NULL=全局共享',
    status          TINYINT       NOT NULL DEFAULT 1,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag        INT           DEFAULT 0,
    INDEX idx_store (store_id)
) COMMENT '容器配置';

-- A1 盘点差异
CREATE TABLE inventory_difference (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id         INT           NOT NULL,
    material_id     VARCHAR(50)   NOT NULL,
    material_name   VARCHAR(200)  NOT NULL,
    spec            VARCHAR(100)  DEFAULT '',
    book_qty        DECIMAL(12,4) NOT NULL DEFAULT 0,
    actual_qty      DECIMAL(12,4) NOT NULL DEFAULT 0,
    diff_qty        DECIMAL(12,4) NOT NULL DEFAULT 0,
    diff_amount     DECIMAL(12,2) DEFAULT NULL,
    unit_price      DECIMAL(10,2) DEFAULT NULL,
    diff_type       VARCHAR(20)   NOT NULL DEFAULT 'surplus',
    status          VARCHAR(20)   NOT NULL DEFAULT 'pending',
    handler         VARCHAR(100)  DEFAULT NULL,
    handled_at      DATETIME      DEFAULT NULL,
    remark          VARCHAR(500)  DEFAULT NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag        INT           DEFAULT 0,
    version         INT           DEFAULT 0,
    FOREIGN KEY (task_id) REFERENCES task(id),
    INDEX idx_task (task_id),
    INDEX idx_status (status),
    UNIQUE KEY uk_task_material (task_id, material_id)
) COMMENT '盘点差异项';

-- A1 差异处理日志
CREATE TABLE difference_process_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    diff_id         BIGINT        NOT NULL,
    action          VARCHAR(50)   NOT NULL,
    operator        VARCHAR(100)  DEFAULT NULL,
    remark          VARCHAR(500)  DEFAULT NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (diff_id) REFERENCES inventory_difference(id),
    INDEX idx_diff (diff_id)
) COMMENT '差异处理日志';

-- A1 模板推荐
CREATE TABLE inventory_template_recommendation (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id        VARCHAR(50)   NOT NULL,
    template_id     INT           NOT NULL,
    score           DECIMAL(5,2)  DEFAULT 0,
    reason          VARCHAR(200)  DEFAULT NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag        INT           DEFAULT 0,
    UNIQUE KEY uk_store_template (store_id, template_id),
    INDEX idx_store (store_id)
) COMMENT '盘点模板推荐';

-- A2 报损记录
CREATE TABLE loss_report (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id        VARCHAR(50)   NOT NULL,
    store_name      VARCHAR(200)  DEFAULT NULL,
    loss_type       VARCHAR(30)   NOT NULL COMMENT 'daily|arrival',
    loss_object     VARCHAR(30)   NOT NULL DEFAULT 'finished',
    material_id     VARCHAR(50)   DEFAULT NULL,
    material_name   VARCHAR(200)  NOT NULL,
    spec            VARCHAR(100)  DEFAULT '',
    unit            VARCHAR(50)   DEFAULT NULL,
    loss_qty        DECIMAL(12,4) DEFAULT NULL,
    gross_weight    DECIMAL(10,2) DEFAULT NULL,
    container_id    BIGINT        DEFAULT NULL,
    container_name  VARCHAR(100)  DEFAULT NULL,
    container_weight DECIMAL(10,2) DEFAULT NULL,
    net_weight      DECIMAL(10,2) DEFAULT NULL,
    occurred_date   DATE          NOT NULL,
    handler_name    VARCHAR(100)  DEFAULT NULL,
    voucher_url     VARCHAR(500)  DEFAULT NULL,
    remark          VARCHAR(500)  DEFAULT NULL,
    status          VARCHAR(30)   NOT NULL DEFAULT 'pending' COMMENT 'pending|confirmed_resend|rejected|closed',
    submitted_by    VARCHAR(100)  DEFAULT NULL,
    confirmed_by    VARCHAR(100)  DEFAULT NULL,
    confirmed_at    DATETIME      DEFAULT NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag        INT           DEFAULT 0,
    version         INT           DEFAULT 0,
    INDEX idx_store (store_id),
    INDEX idx_type (loss_type),
    INDEX idx_status (status),
    INDEX idx_date (occurred_date)
) COMMENT '门店报损记录';

-- A3 调货单
CREATE TABLE transfer_order (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    biz_code        VARCHAR(50)   NOT NULL,
    from_store_id   VARCHAR(50)   NOT NULL,
    from_store_name VARCHAR(200)  DEFAULT NULL,
    to_store_id     VARCHAR(50)   NOT NULL,
    to_store_name   VARCHAR(200)  DEFAULT NULL,
    status          VARCHAR(30)   NOT NULL DEFAULT 'pending_confirm'
        COMMENT 'pending_confirm|confirmed|pending_ship|pending_receive|completed|cancelled|rejected',
    total_qty       DECIMAL(12,4) NOT NULL DEFAULT 0,
    remark          VARCHAR(500)  DEFAULT NULL,
    created_by      VARCHAR(100)  DEFAULT NULL,
    confirmed_by    VARCHAR(100)  DEFAULT NULL,
    shipped_by      VARCHAR(100)  DEFAULT NULL,
    received_by     VARCHAR(100)  DEFAULT NULL,
    confirmed_at    DATETIME      DEFAULT NULL,
    shipped_at      DATETIME      DEFAULT NULL,
    received_at     DATETIME      DEFAULT NULL,
    completed_at    DATETIME      DEFAULT NULL,
    cancelled_at    DATETIME      DEFAULT NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag        INT           DEFAULT 0,
    version         INT           DEFAULT 0,
    INDEX idx_from_store (from_store_id),
    INDEX idx_to_store (to_store_id),
    INDEX idx_status (status),
    INDEX idx_biz_code (biz_code)
) COMMENT '调货单';

-- A3 调货单明细
CREATE TABLE transfer_order_item (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    transfer_id     BIGINT        NOT NULL,
    material_name   VARCHAR(200)  NOT NULL,
    spec            VARCHAR(100)  DEFAULT '',
    unit            VARCHAR(50)   NOT NULL,
    transfer_qty    DECIMAL(12,4) NOT NULL,
    remark          VARCHAR(200)  DEFAULT NULL,
    del_flag        INT           DEFAULT 0,
    FOREIGN KEY (transfer_id) REFERENCES transfer_order(id),
    INDEX idx_transfer (transfer_id)
) COMMENT '调货单明细';

-- A4 物流记录
CREATE TABLE logistics_record (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    transfer_id     BIGINT        DEFAULT NULL,
    store_id        VARCHAR(50)   NOT NULL,
    store_name      VARCHAR(200)  DEFAULT NULL,
    tracking_no     VARCHAR(100)  NOT NULL,
    carrier         VARCHAR(100)  DEFAULT NULL,
    status          VARCHAR(30)   NOT NULL DEFAULT 'pending_shipment'
        COMMENT 'pending_shipment|in_transit|delivering|signed|abnormal|query_failed',
    tracking_data   TEXT          DEFAULT NULL,
    created_by      VARCHAR(100)  DEFAULT NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag        INT           DEFAULT 0,
    UNIQUE KEY uk_tracking_no (tracking_no),
    INDEX idx_store (store_id),
    INDEX idx_transfer (transfer_id),
    INDEX idx_status (status)
) COMMENT '物流记录';

-- B1 问题处理单
CREATE TABLE issue (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    biz_code        VARCHAR(50)   NOT NULL,
    store_id        VARCHAR(50)   NOT NULL,
    store_name      VARCHAR(200)  DEFAULT NULL,
    title           VARCHAR(200)  NOT NULL,
    issue_type      VARCHAR(50)   NOT NULL,
    urgency         VARCHAR(20)   NOT NULL COMMENT 'urgent|normal|low',
    description     VARCHAR(2000) NOT NULL,
    contact_name    VARCHAR(50)   NOT NULL,
    contact_phone   VARCHAR(20)   NOT NULL,
    images          TEXT          DEFAULT NULL,
    xiangmu_id      VARCHAR(100)  DEFAULT NULL,
    sync_status     VARCHAR(20)   DEFAULT NULL COMMENT 'pending|synced|failed',
    status          VARCHAR(30)   NOT NULL DEFAULT 'pending' COMMENT 'pending|processing|resolved|closed',
    submitted_by    VARCHAR(100)  DEFAULT NULL,
    processed_by    VARCHAR(100)  DEFAULT NULL,
    processed_at    DATETIME      DEFAULT NULL,
    resolved_at     DATETIME      DEFAULT NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag        INT           DEFAULT 0,
    version         INT           DEFAULT 0,
    INDEX idx_store (store_id),
    INDEX idx_type (issue_type),
    INDEX idx_status (status),
    INDEX idx_urgency (urgency),
    INDEX idx_biz_code (biz_code)
) COMMENT '问题处理单';

-- B2 企微通知日志
CREATE TABLE notification_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type      VARCHAR(50)   NOT NULL,
    store_id        VARCHAR(50)   NOT NULL,
    target_openid   VARCHAR(128)  DEFAULT NULL,
    title           VARCHAR(200)  NOT NULL,
    content         VARCHAR(1000) NOT NULL,
    source_id       VARCHAR(100)  DEFAULT NULL,
    status          TINYINT       NOT NULL DEFAULT 0 COMMENT '0待发送 1成功 2失败',
    fail_reason     VARCHAR(500)  DEFAULT NULL,
    retry_count     INT           DEFAULT 0,
    sent_at         DATETIME      DEFAULT NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_store (store_id),
    INDEX idx_event (event_type),
    INDEX idx_status (status),
    INDEX idx_source (source_id)
) COMMENT '企微通知日志';

-- ============================================================
-- 完成
-- ============================================================
SELECT 'store_inventory_test 测试库创建成功' AS status;
