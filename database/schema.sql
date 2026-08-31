-- ============================================================
-- 象掌柜 盘点工具 1.0 — 汇总建库建表
-- ============================================================
-- 用途：全新部署时执行此脚本即可创建完整数据库结构
-- 目标：MySQL 8.0
-- 编码：utf8mb4
-- ============================================================
-- 变更记录见 CHANGELOG.md 及 migration/ 目录（V2–V7 增量已整合）
-- 生成日期：2026-06-29（2026-07-13 整合 P0/A1/B1/B2 变更）
-- ============================================================

CREATE DATABASE IF NOT EXISTS store_inventory
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE store_inventory;

-- ============================================================
-- 一、物料模块（Material）
-- ============================================================

-- 1.1 物料主数据
CREATE TABLE material (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    material_id     VARCHAR(50)   DEFAULT NULL COMMENT '物料ID（外部系统编码）',
    qm_code         VARCHAR(100)  DEFAULT NULL COMMENT '企迈编码（外部API同步）',
    parent_category VARCHAR(100)  DEFAULT NULL COMMENT '父分类',
    category        VARCHAR(100)  DEFAULT NULL COMMENT '分类',
    material_name   VARCHAR(200)  NOT NULL COMMENT '物料名称',
    spec            VARCHAR(100)  NOT NULL DEFAULT '' COMMENT '规格',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag        INT           DEFAULT 0 COMMENT '删除标记 0正常 1删除',
    qr_code         VARCHAR(500)  DEFAULT NULL COMMENT '物料二维码图片路径',
    loss_visible    TINYINT       DEFAULT 0 COMMENT '报损可见 0否 1是',
    INDEX idx_material_material_id (material_id),
    INDEX idx_material_del_name (del_flag, material_name)
) COMMENT '物料主数据';

-- 1.2 物料盘点规则（基础单位、盘点单位串、单价）
CREATE TABLE material_inventory_rule (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_id         VARCHAR(50)   NOT NULL COMMENT '规则业务ID',
    material_id     VARCHAR(50)   NOT NULL COMMENT '关联物料ID',
    base_unit       VARCHAR(50)   NOT NULL COMMENT '基础盘点单位',
    inventory_units VARCHAR(500)  DEFAULT NULL COMMENT '盘点单位串（逗号分隔）',
    stock_unit      VARCHAR(50)   DEFAULT NULL COMMENT '库存单位',
    unit_price      DECIMAL(10,2) DEFAULT NULL COMMENT '单价',
    purchase_price  DECIMAL(12,4) DEFAULT NULL COMMENT '采购单价（元，按采购单位，xinfo purchasePrice 同步）',
    purchase_unit   VARCHAR(50)   DEFAULT NULL COMMENT '采购单位（xinfo purchaseUnit 同步）',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag        INT           DEFAULT 0 COMMENT '删除标记 0正常 1删除',
    INDEX idx_rule_material_id (material_id)
) COMMENT '物料盘点规则';

-- 1.3 物料换算关系（单位换算 / 称重换算）
CREATE TABLE material_conversion_rule (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_id         VARCHAR(50)     NOT NULL COMMENT '规则业务ID',
    conversion_type VARCHAR(20)     NOT NULL COMMENT '换算类型：unit 单位换算 / weight 称重换算',
    from_quantity   DECIMAL(18,4)   NOT NULL COMMENT '从数量',
    from_unit       VARCHAR(50)     NOT NULL COMMENT '从单位',
    to_quantity     DECIMAL(18,4)   NOT NULL COMMENT '到数量',
    to_unit         VARCHAR(50)     NOT NULL COMMENT '到单位',
    sort_no         INT             DEFAULT NULL COMMENT '排序号（多级换算链）',
    del_flag        INT             DEFAULT 0 COMMENT '删除标记 0正常 1删除',
    INDEX idx_rule_id (rule_id)
) COMMENT '物料换算关系';

-- ============================================================
-- 二、模板模块（Template）
-- ============================================================

-- 2.1 标准分区模板
CREATE TABLE template (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    biz_code        VARCHAR(50)   NOT NULL DEFAULT '' COMMENT '业务编码',
    template_name   VARCHAR(200)  NOT NULL COMMENT '模板名称',
    status          TINYINT       NOT NULL DEFAULT 1 COMMENT '1启用 0停用 2草稿',
    template_type   VARCHAR(20)   NOT NULL DEFAULT 'monthly' COMMENT '模板类型: monthly月盘|weekly周盘',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag        INT           DEFAULT 0 COMMENT '删除标记 0正常 1删除',
    version         INT           DEFAULT 0 COMMENT '乐观锁版本号'
) COMMENT '标准分区模板';

-- 2.2 模板分区
CREATE TABLE template_zone (
    id              INT AUTO_INCREMENT PRIMARY KEY COMMENT '分区ID（主键）',
    template_id     INT           NOT NULL COMMENT '关联模板ID',
    zone_name       VARCHAR(100)  NOT NULL COMMENT '分区名称',
    sort_no         INT           DEFAULT NULL COMMENT '排序号',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    del_flag        INT           DEFAULT 0 COMMENT '删除标记 0正常 1删除',
    biz_code        VARCHAR(50)   NOT NULL DEFAULT '' COMMENT '业务编码',
    version         INT           DEFAULT 0 COMMENT '乐观锁版本号',
    FOREIGN KEY (template_id) REFERENCES template(id)
) COMMENT '模板分区';

-- 2.3 模板分区物料关系
CREATE TABLE template_zone_material (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    zone_id         INT           NOT NULL COMMENT '关联分区ID',
    material_id     VARCHAR(50)   NOT NULL COMMENT '关联物料ID',
    material_name   VARCHAR(200)  NOT NULL DEFAULT '' COMMENT '物料名称快照',
    spec            VARCHAR(100)  NOT NULL DEFAULT '' COMMENT '规格快照',
    inventory_unit  VARCHAR(50)   NOT NULL DEFAULT '' COMMENT '盘点单位快照',
    sort_no         INT           DEFAULT NULL COMMENT '排序号',
    del_flag        INT           DEFAULT 0 COMMENT '删除标记 0正常 1删除',
    biz_code        VARCHAR(50)   NOT NULL DEFAULT '' COMMENT '业务编码',
    version         INT           DEFAULT 0 COMMENT '乐观锁版本号',
    FOREIGN KEY (zone_id) REFERENCES template_zone(id),
    UNIQUE KEY uk_zone_material (zone_id, material_id),
    INDEX idx_zone_del_sort (zone_id, del_flag, sort_no)
) COMMENT '模板分区物料关系';

-- ============================================================
-- 三、任务模块（Task）
-- ============================================================

-- 3.1 盘点任务（月盘/周盘）
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
    task_month      VARCHAR(20)   NOT NULL COMMENT '盘点月份（周盘=周起始日所在月）',
    task_type       VARCHAR(20)   NOT NULL DEFAULT 'monthly' COMMENT '任务类型: monthly月盘|weekly周盘',
    task_week       VARCHAR(20)   DEFAULT NULL COMMENT '盘点周 YYYY-Www(仅周盘)',
    deadline        DATETIME      NOT NULL COMMENT '截止时间（周盘=门店配置盘点日当天23:59:59）',
    status          VARCHAR(20)   NOT NULL DEFAULT 'not_started' COMMENT 'not_started|in_progress|pending_submit|submitted|overdue',
    created_by      VARCHAR(100)  NOT NULL DEFAULT '' COMMENT '创建人',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_by    VARCHAR(100)  DEFAULT NULL COMMENT '提交人',
    submitted_at    DATETIME      DEFAULT NULL COMMENT '提交时间',
    del_flag        INT           DEFAULT 0 COMMENT '删除标记 0正常 1删除',
    version         INT           DEFAULT 0 COMMENT '乐观锁版本号',
    INDEX idx_task_store_id (store_id),
    INDEX idx_task_type (task_type)
) COMMENT '盘点任务（月盘/周盘）' ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 3.2 任务分区快照
CREATE TABLE task_zone (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    biz_code        VARCHAR(50)   NOT NULL DEFAULT '' COMMENT '业务编码',
    task_id         INT           NOT NULL COMMENT '关联任务ID',
    zone_name       VARCHAR(100)  NOT NULL COMMENT '分区名称快照',
    sort_no         INT           DEFAULT NULL COMMENT '排序号',
    source_type     VARCHAR(50)   NOT NULL DEFAULT '' COMMENT '来源类型',
    zone_saved      TINYINT       NOT NULL DEFAULT 0 COMMENT '0未保存 1已保存（店长点过保存本分区）',
    saved_at        DATETIME      DEFAULT NULL COMMENT '分区保存时间',
    del_flag        INT           DEFAULT 0 COMMENT '删除标记 0正常 1删除',
    version         INT           DEFAULT 0 COMMENT '乐观锁版本号',
    FOREIGN KEY (task_id) REFERENCES task(id)
) COMMENT '任务分区快照';

-- 3.3 任务分区物料快照
CREATE TABLE task_zone_material (
    id                  INT AUTO_INCREMENT PRIMARY KEY,
    biz_code            VARCHAR(50)     NOT NULL DEFAULT '' COMMENT '业务编码',
    task_id             INT             NOT NULL COMMENT '关联任务ID',
    task_zone_id        INT             NOT NULL COMMENT '关联任务分区ID',
    material_id         VARCHAR(50)     NOT NULL COMMENT '物料ID',
    material_name       VARCHAR(200)    NOT NULL COMMENT '物料名称',
    spec                VARCHAR(100)    NOT NULL DEFAULT '' COMMENT '规格',
    unit                VARCHAR(50)     NOT NULL COMMENT '单位',
    inventory_unit      VARCHAR(50)     NOT NULL DEFAULT '' COMMENT '盘点单位',
    sort_no             INT             DEFAULT NULL COMMENT '排序号',
    -- 录入数量
    input_qty               DECIMAL(10,2) DEFAULT NULL COMMENT '录入数量',
    remark                  VARCHAR(500)  DEFAULT NULL COMMENT '备注',
    input_status            VARCHAR(20)   NOT NULL DEFAULT 'not_entered' COMMENT 'not_entered|entered|zero_entered',
    -- 多单位录入
    input_mode              VARCHAR(20)    DEFAULT NULL COMMENT '录入模式：unit按单位录入 weight称重录入',
    input_original_qty      DECIMAL(18,4)  DEFAULT NULL COMMENT '用户原始录入数量',
    input_original_unit     VARCHAR(50)    DEFAULT NULL COMMENT '用户原始录入单位；称重时为重量单位',
    unit_inputs             TEXT           DEFAULT NULL COMMENT '各单位输入拆分JSON，如{"箱":"1","瓶":"2"}',
    -- 换算快照
    base_unit_snapshot      VARCHAR(50)    DEFAULT NULL COMMENT '基础盘点单位快照',
    rule_id_snapshot        VARCHAR(50)    DEFAULT NULL COMMENT '物料盘点规则ID快照',
    base_qty                DECIMAL(18,4)  DEFAULT NULL COMMENT '折算后的基础单位数量',
    conversion_snapshot     TEXT           DEFAULT NULL COMMENT '本次使用的换算规则JSON快照',
    unit_price_snapshot     DECIMAL(10,2)  DEFAULT NULL COMMENT '盘点单价快照（录入时固化）',
    -- 时间戳
    entered_at              DATETIME       DEFAULT NULL COMMENT '录入时间',
    -- 通用
    del_flag            INT             DEFAULT 0 COMMENT '删除标记 0正常 1删除',
    version             INT             DEFAULT 0 COMMENT '乐观锁版本号',
    FOREIGN KEY (task_id) REFERENCES task(id),
    FOREIGN KEY (task_zone_id) REFERENCES task_zone(id),
    UNIQUE KEY uk_task_zone_material (task_zone_id, material_id),
    INDEX idx_tzm_task_id (task_id)
) COMMENT '任务分区物料快照';

-- 3.4 任务物料跨分区汇总
CREATE TABLE task_material_summary (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    biz_code        VARCHAR(50)     NOT NULL DEFAULT '' COMMENT '业务编码',
    task_id         INT             NOT NULL COMMENT '关联任务ID',
    material_id     VARCHAR(50)     NOT NULL COMMENT '物料ID',
    material_name   VARCHAR(200)    DEFAULT NULL COMMENT '物料名称',
    spec            VARCHAR(100)    DEFAULT NULL COMMENT '规格',
    base_unit       VARCHAR(50)     DEFAULT NULL COMMENT '基础单位',
    total_qty       DECIMAL(10,2)   NOT NULL DEFAULT 0 COMMENT '跨分区汇总数量',
    zone_count      INT             DEFAULT NULL COMMENT '涉及分区数',
    unit_breakdown  VARCHAR(500)    DEFAULT NULL COMMENT '各单位数量明细',
    del_flag        INT             DEFAULT 0 COMMENT '删除标记 0正常 1删除',
    version         INT             DEFAULT 0 COMMENT '乐观锁版本号',
    FOREIGN KEY (task_id) REFERENCES task(id),
    UNIQUE KEY uk_task_material (task_id, material_id)
) COMMENT '任务物料汇总';

-- 3.5 门店默认分区物料清单
CREATE TABLE store_zone_material (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    biz_code        VARCHAR(50)   NOT NULL DEFAULT '' COMMENT '业务编码',
    store_id        VARCHAR(50)   NOT NULL COMMENT '门店ID',
    zone_name       VARCHAR(100)  NOT NULL COMMENT '分区名称',
    material_id     BIGINT        NOT NULL COMMENT '物料ID（关联 material.id）',
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
-- 四、门店信息（Store）
-- ============================================================

CREATE TABLE store_info (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    store_id        VARCHAR(64)   NOT NULL COMMENT '外部API门店ID',
    store_name      VARCHAR(128)  NOT NULL COMMENT '门店名称',
    store_code      VARCHAR(64)   DEFAULT NULL COMMENT '门店编码',
    store_type      VARCHAR(20)   NOT NULL DEFAULT 'franchise' COMMENT '门店类型: direct直营|franchise加盟',
    xiaochengxuid   VARCHAR(64)   DEFAULT NULL COMMENT '小程序号',
    cangkuid        VARCHAR(64)   DEFAULT NULL COMMENT '仓库ID（来自外部API）',
    qr_code         VARCHAR(512)  DEFAULT NULL COMMENT '门店二维码（本地维护）',
    owner_name      VARCHAR(50)   DEFAULT NULL COMMENT '老板姓名（本地维护，用于绑定匹配）',
    owner_phone     VARCHAR(20)   DEFAULT NULL COMMENT '老板手机号（本地维护，用于绑定匹配）',
    owner_openid    VARCHAR(128)  DEFAULT NULL COMMENT '绑定的微信openid',
    chat_id         VARCHAR(128)  DEFAULT NULL COMMENT '外部问题表单系统门店标识（chat_id），用于上报页URL映射',
    supervisor_name VARCHAR(50)   DEFAULT NULL COMMENT '督导姓名（本地维护，用于月度区域划分）',
    qmai_store_id   BIGINT        DEFAULT NULL COMMENT '企迈门店ID（本地维护，用于调用企迈API）',
    warehouse_id    VARCHAR(50)   DEFAULT NULL COMMENT '企迈控制台仓库ID（本地维护，用于查询入库单）',
    weekly_inventory_day TINYINT  DEFAULT NULL COMMENT '周盘点日(1周一-7周日, 仅周盘用, NULL=不参与周盘)',
    weekly_paused        TINYINT  NOT NULL DEFAULT 0 COMMENT '周盘暂停: 0参与 1暂停(暂停后自动生成跳过该店)',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag        TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常 1删除',
    UNIQUE KEY uk_store_id (store_id),
    INDEX idx_store_name (store_name),
    INDEX idx_store_del_updated (del_flag, updated_at)
) COMMENT '门店信息本地表';

-- 门店订货周期配置（周盘按订货周期下发：订货日前一天自动生成周盘任务 → 周盘提交后自动生成智能订货单）
CREATE TABLE store_order_cycle (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id    VARCHAR(64) NOT NULL COMMENT '门店ID',
    order_days  VARCHAR(20) NOT NULL COMMENT '订货日(1-7逗号分隔,1=周一)',
    paused      TINYINT DEFAULT 0 COMMENT '暂停周盘 0参与 1暂停',
    version     INT DEFAULT 0 COMMENT '乐观锁',
    del_flag    TINYINT DEFAULT 0 COMMENT '逻辑删除 0正常 1删除',
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_store (store_id)
) COMMENT '门店订货周期配置';

-- ============================================================
-- 五、认证与会话
-- ============================================================

-- 5.1 小程序店长会话
CREATE TABLE store_manager_session (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    openid          VARCHAR(100)  NOT NULL COMMENT '微信 openid',
    unionid         VARCHAR(100)  DEFAULT NULL COMMENT '微信 unionid（可选）',
    session_key     VARCHAR(200)  DEFAULT NULL COMMENT '微信 session_key',
    wx_nickname     VARCHAR(100)  DEFAULT NULL COMMENT '微信昵称',
    store_id        VARCHAR(50)   DEFAULT NULL COMMENT '已绑定的门店ID',
    store_name      VARCHAR(200)  DEFAULT NULL COMMENT '已绑定的门店名称（冗余便于展示）',
    role            VARCHAR(20)   DEFAULT NULL COMMENT '角色：store_manager|owner|staff（登录时缓存）',
    token           VARCHAR(500)  DEFAULT NULL COMMENT '当前有效的 JWT',
    last_login_at   DATETIME      DEFAULT NULL COMMENT '最近登录时间',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag        INT           DEFAULT 0 COMMENT '删除标记 0正常 1删除',
    UNIQUE KEY uk_openid (openid),
    INDEX idx_store_id (store_id)
) COMMENT '小程序店长会话';

-- 5.2 总部端飞书用户权限
CREATE TABLE admin_permission (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    open_id         VARCHAR(100)  NOT NULL COMMENT '飞书 open_id',
    name            VARCHAR(100)  NOT NULL DEFAULT '' COMMENT '飞书姓名',
    avatar_url      VARCHAR(500)  NOT NULL DEFAULT '' COMMENT '头像',
    email           VARCHAR(200)  NOT NULL DEFAULT '' COMMENT '邮箱',
    mobile          VARCHAR(50)   NOT NULL DEFAULT '' COMMENT '手机号',
    role            VARCHAR(500)  NOT NULL DEFAULT 'normal_user' COMMENT '角色，逗号分隔如 headquarters_admin,finance_admin,hr_admin,operation_admin',
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

-- 5.3 老板微信绑定申请
CREATE TABLE owner_bind_application (
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

-- ============================================================
-- 六、支出模块（Expense）
-- ============================================================

-- 6.1 支出类型（一级分类）
CREATE TABLE expense_type (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    type_id         VARCHAR(32)   NOT NULL COMMENT '类型业务ID',
    name            VARCHAR(100)  NOT NULL COMMENT '类型名称',
    first_type_id   VARCHAR(32)   DEFAULT NULL COMMENT '一级分类ID',
    first_type_name VARCHAR(100)  DEFAULT NULL COMMENT '一级分类名称',
    description     VARCHAR(500)  DEFAULT NULL COMMENT '描述',
    status          VARCHAR(20)   DEFAULT 'enabled' COMMENT 'enabled|disabled',
    sort_no         INT           DEFAULT NULL COMMENT '排序号，越小越靠前，NULL 排最后',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag        INT           DEFAULT 0 COMMENT '删除标记 0正常 1删除',
    UNIQUE KEY uk_type_id (type_id)
) COMMENT '支出类型';

-- 6.2 支出项目（二级分类）
CREATE TABLE expense_item (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    item_id         VARCHAR(32)   NOT NULL COMMENT '项目业务ID',
    type_id         VARCHAR(32)   NOT NULL COMMENT '关联支出类型ID',
    name            VARCHAR(100)  NOT NULL COMMENT '项目名称',
    description     VARCHAR(500)  DEFAULT NULL COMMENT '描述',
    status          VARCHAR(20)   DEFAULT 'enabled' COMMENT 'enabled|disabled',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag        INT           DEFAULT 0 COMMENT '删除标记 0正常 1删除',
    INDEX idx_type_id (type_id)
) COMMENT '支出项目';

-- 6.3 支出记录
CREATE TABLE expense_record (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    expense_id          VARCHAR(32)     NOT NULL COMMENT '支出业务ID',
    store_id            VARCHAR(50)     NOT NULL COMMENT '门店ID',
    store_miniapp_no    VARCHAR(100)    DEFAULT NULL COMMENT '门店小程序号',
    store_name          VARCHAR(200)    DEFAULT NULL COMMENT '门店名称',
    warehouse_code      VARCHAR(50)     DEFAULT NULL COMMENT '仓库编码快照',
    type_id             VARCHAR(32)     DEFAULT NULL COMMENT '支出类型ID',
    type_name           VARCHAR(100)    DEFAULT NULL COMMENT '支出类型名称',
    first_type_id       VARCHAR(32)     DEFAULT NULL COMMENT '一级分类ID',
    first_type_name     VARCHAR(100)    DEFAULT NULL COMMENT '一级分类名称',
    item_id             VARCHAR(32)     DEFAULT NULL COMMENT '支出项目ID',
    item_name           VARCHAR(100)    DEFAULT NULL COMMENT '支出项目名称',
    amount              DECIMAL(10,2)   NOT NULL COMMENT '金额',
    occurred_date       DATE            DEFAULT NULL COMMENT '发生日期',
    handler_name        VARCHAR(100)    DEFAULT NULL COMMENT '经手人',
    voucher_url         VARCHAR(500)    DEFAULT NULL COMMENT '凭证图片URL',
    remark              VARCHAR(500)    DEFAULT NULL COMMENT '备注',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag            INT             DEFAULT 0 COMMENT '删除标记 0正常 1删除',
    INDEX idx_store_id (store_id),
    INDEX idx_type_id (type_id),
    INDEX idx_occurred_date (occurred_date)
) COMMENT '支出记录';

-- ============================================================
-- 七、人员模块（People）
-- ============================================================

-- 7.1 员工主数据
CREATE TABLE employee (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id             VARCHAR(32)   NOT NULL COMMENT '员工业务ID',
    name                    VARCHAR(64)   NOT NULL COMMENT '姓名',
    mobile                  VARCHAR(32)   DEFAULT NULL COMMENT '手机号',
    openid                  VARCHAR(64)   DEFAULT NULL COMMENT '绑定微信 openid',
    gender                  VARCHAR(16)   DEFAULT NULL COMMENT '性别',
    birthday                DATE          DEFAULT NULL COMMENT '出生日期',
    store_id                VARCHAR(50)   NOT NULL COMMENT '所属门店ID',
    store_miniapp_no        VARCHAR(100)  DEFAULT NULL COMMENT '门店小程序号',
    store_name              VARCHAR(200)  DEFAULT NULL COMMENT '门店名称',
    role                    VARCHAR(64)   DEFAULT NULL COMMENT '岗位角色',
    employment_type         VARCHAR(32)   DEFAULT NULL COMMENT '用工类型',
    entry_date              DATE          DEFAULT NULL COMMENT '入职日期',
    leave_date              DATE          DEFAULT NULL COMMENT '离职日期',
    emergency_contact_name  VARCHAR(64)   DEFAULT NULL COMMENT '紧急联系人姓名',
    emergency_contact_phone VARCHAR(32)   DEFAULT NULL COMMENT '紧急联系人电话',
    remark                  VARCHAR(500)  DEFAULT NULL COMMENT '备注',
    status                  VARCHAR(20)   DEFAULT 'active' COMMENT 'active|inactive',
    created_at              DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag                INT           DEFAULT 0 COMMENT '删除标记 0正常 1删除',
    UNIQUE KEY uk_employee_store_openid (store_id, openid),
    INDEX idx_employee_openid_status (openid, status),
    INDEX idx_employee_store_id (store_id),
    INDEX idx_employee_mobile (mobile)
) COMMENT '员工主数据';

-- 7.2 员工登记申请表
CREATE TABLE employee_registration_application (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    application_id          VARCHAR(32)   NOT NULL COMMENT '登记申请业务ID',
    openid                  VARCHAR(64)   DEFAULT NULL COMMENT '申请人微信 openid',
    store_id                VARCHAR(32)   NOT NULL COMMENT '申请门店ID',
    store_name              VARCHAR(128)  NOT NULL COMMENT '申请门店名称快照',
    name                    VARCHAR(64)   NOT NULL COMMENT '姓名',
    mobile                  VARCHAR(32)   NOT NULL COMMENT '手机号',
    gender                  VARCHAR(16)   DEFAULT NULL COMMENT '性别',
    birthday                DATE          DEFAULT NULL COMMENT '出生日期',
    expected_role           VARCHAR(64)   NOT NULL COMMENT '期望岗位',
    employment_type         VARCHAR(32)   NOT NULL COMMENT '用工类型',
    entry_date              DATE          NOT NULL COMMENT '入职日期',
    emergency_contact_name  VARCHAR(64)   DEFAULT NULL COMMENT '紧急联系人姓名',
    emergency_contact_phone VARCHAR(32)   DEFAULT NULL COMMENT '紧急联系人电话',
    remark                  VARCHAR(500)  DEFAULT NULL COMMENT '补充说明',
    status                  VARCHAR(20)   NOT NULL DEFAULT 'pending' COMMENT '状态：pending待审批 approved已通过 rejected已驳回',
    reject_reason           VARCHAR(500)  DEFAULT NULL COMMENT '驳回原因',
    approver_openid         VARCHAR(64)   DEFAULT NULL COMMENT '审批人 openid',
    approved_at             DATETIME      DEFAULT NULL COMMENT '审批时间',
    employee_id             VARCHAR(32)   DEFAULT NULL COMMENT '审批通过后生成的员工业务ID',
    created_at              DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag                TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    UNIQUE KEY uk_application_id (application_id),
    INDEX idx_staff_app_store_status (store_id, status),
    INDEX idx_staff_app_openid (openid),
    INDEX idx_staff_app_mobile (mobile)
) COMMENT '员工登记申请表';

-- 7.3 老板扫码登记表
CREATE TABLE owner_registration (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    openid          VARCHAR(128)  NOT NULL COMMENT '微信openid',
    bind_code       VARCHAR(64)   DEFAULT NULL COMMENT '二维码绑定码',
    name            VARCHAR(50)   NOT NULL COMMENT '老板姓名',
    phone           VARCHAR(20)   NOT NULL COMMENT '手机号',
    store_id        VARCHAR(50)   NOT NULL COMMENT '门店ID',
    store_name      VARCHAR(200)  NOT NULL COMMENT '门店名称',
    role            VARCHAR(20)   DEFAULT NULL COMMENT '角色：店长/老板',
    status          VARCHAR(20)   NOT NULL DEFAULT '未关联' COMMENT '已绑定/未关联',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_openid (openid),
    INDEX idx_store (store_id)
) COMMENT '老板扫码登记表';

-- 7.4 门店联系人信息
CREATE TABLE store_contact (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id        VARCHAR(50)   NOT NULL COMMENT '门店ID',
    store_name      VARCHAR(200)  NOT NULL COMMENT '门店名称',
    contact_name    VARCHAR(50)   NOT NULL COMMENT '联系人姓名',
    contact_phone   VARCHAR(20)   NOT NULL COMMENT '联系人手机号',
    del_flag        TINYINT       NOT NULL DEFAULT 0 COMMENT '0正常 1已删除',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_store_id (store_id),
    INDEX idx_phone (contact_phone)
) COMMENT '门店联系人信息';

-- ============================================================
-- 八、门店工时（Work Hours）
-- ============================================================

CREATE TABLE store_work_hours (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_id       VARCHAR(32)   NOT NULL COMMENT '业务ID',
    store_id        VARCHAR(50)   NOT NULL COMMENT '门店ID',
    store_name      VARCHAR(200)  DEFAULT NULL COMMENT '门店名称',
    record_time     VARCHAR(7)    NOT NULL COMMENT '年月 YYYY-MM',
    hours           DECIMAL(10,2) NOT NULL COMMENT '工时（小时）',
    employee_id     VARCHAR(32)   NOT NULL COMMENT '录入人ID',
    employee_name   VARCHAR(100)  NOT NULL COMMENT '录入人姓名',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag        INT           DEFAULT 0 COMMENT '删除标记 0正常 1删除',
    INDEX idx_store_id (store_id),
    INDEX idx_record_time (record_time),
    INDEX idx_store_month (store_id, record_time)
) COMMENT '门店月度工时';

-- ============================================================
-- 九、日志（Log）
-- ============================================================

-- 9.1 操作日志
CREATE TABLE operation_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         VARCHAR(128)  DEFAULT NULL COMMENT '操作人ID（小程序端为微信 openid，总部端为飞书 open_id）',
    source          VARCHAR(50)   DEFAULT NULL COMMENT '来源：mp小程序/admin总部',
    username        VARCHAR(100)  NOT NULL DEFAULT '' COMMENT '操作人标识（总部端飞书姓名，小程序端员工姓名）',
    module          VARCHAR(50)   NOT NULL DEFAULT '' COMMENT '模块：物料/模板/任务',
    operation       VARCHAR(50)   NOT NULL DEFAULT '' COMMENT '操作：新增/编辑/删除/启停/提交',
    description     VARCHAR(500)  DEFAULT '' COMMENT '操作描述',
    request_ip      VARCHAR(50)   DEFAULT '' COMMENT '请求IP',
    status          TINYINT       NOT NULL DEFAULT 1 COMMENT '1成功 0失败',
    error_msg       VARCHAR(1000) DEFAULT '' COMMENT '失败时的错误信息',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    INDEX idx_username (username),
    INDEX idx_module (module),
    INDEX idx_created_at (created_at)
) COMMENT '操作日志';

-- 9.2 登录日志
CREATE TABLE login_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT        DEFAULT NULL COMMENT '用户ID',
    openid          VARCHAR(128)  DEFAULT NULL COMMENT '微信 openid',
    username        VARCHAR(100)  NOT NULL DEFAULT '' COMMENT '用户名/微信昵称',
    login_type      VARCHAR(30)   NOT NULL DEFAULT '' COMMENT '登录类型：login/logout/bind_store',
    status          TINYINT       NOT NULL DEFAULT 1 COMMENT '1成功 0失败',
    fail_reason     VARCHAR(200)  DEFAULT '' COMMENT '失败原因',
    request_ip      VARCHAR(50)   DEFAULT '' COMMENT '请求IP',
    user_agent      VARCHAR(500)  DEFAULT '' COMMENT '客户端UA',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    INDEX idx_username (username),
    INDEX idx_login_type (login_type),
    INDEX idx_created_at (created_at)
) COMMENT '登录日志';

-- ============================================================
-- 十、自购物料采购（Self Purchase Material）
-- ============================================================

CREATE TABLE self_purchase_material (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY       COMMENT '主键',
    biz_code         VARCHAR(50)   DEFAULT NULL              COMMENT '业务ID，如 SPM_xxx',
    store_id         VARCHAR(50)   NOT NULL                  COMMENT '门店ID，关联 store_info.store_id',
    store_name       VARCHAR(200)  DEFAULT NULL              COMMENT '门店名称快照',
    store_miniapp_no VARCHAR(100)  DEFAULT NULL              COMMENT '小程序ID',
    material_id      VARCHAR(50)   DEFAULT NULL              COMMENT '物料ID',
    material_code    VARCHAR(100)  DEFAULT NULL              COMMENT '物料编码，自由文本',
    parent_category  VARCHAR(100)  NOT NULL                  COMMENT '父级分类，自由文本',
    category         VARCHAR(100)  NOT NULL                  COMMENT '分类，自由文本',
    material_name    VARCHAR(200)  NOT NULL                  COMMENT '物料名称，自由文本',
    unit             VARCHAR(20)   NOT NULL DEFAULT ''       COMMENT '最小单位，如斤/个/包/瓶',
    purchase_month   VARCHAR(7)    NOT NULL                  COMMENT '采购月份，如 2026-07',
    purchase_date    DATE          DEFAULT NULL              COMMENT '采购日期',
    purchase_qty     DECIMAL(10,2) NOT NULL DEFAULT 0        COMMENT '采购数量',
    unit_price       DECIMAL(10,2) DEFAULT NULL              COMMENT '单价',
    total_amount     DECIMAL(10,2) DEFAULT NULL              COMMENT '合计',
    handler_name     VARCHAR(50)   DEFAULT NULL              COMMENT '经手人',
    voucher_url      VARCHAR(500)  DEFAULT NULL              COMMENT '凭证图片URL',
    remark           VARCHAR(500)  DEFAULT NULL              COMMENT '说明',
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag         INT           DEFAULT 0                 COMMENT '删除标记 0正常 1删除',
    UNIQUE KEY uk_spm_biz_code (biz_code),
    INDEX idx_spm_store_id (store_id),
    INDEX idx_spm_store_month (store_id, purchase_month),
    INDEX idx_spm_parent_category (parent_category),
    INDEX idx_spm_category (category),
    INDEX idx_spm_purchase_month (purchase_month)
) COMMENT '自购物料采购表，门店自行采购的水果+食材物料记录';

-- ============================================================
-- P0 调货管理（A3, A4）
-- ============================================================

-- 调货单主表
CREATE TABLE transfer_order (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    biz_code        VARCHAR(50)   NOT NULL              COMMENT '业务编码',
    from_store_id   VARCHAR(50)   NOT NULL              COMMENT '调出门店ID',
    from_store_name VARCHAR(200)  DEFAULT NULL          COMMENT '调出门店名称',
    to_store_id     VARCHAR(50)   NOT NULL              COMMENT '调入门店ID',
    to_store_name   VARCHAR(200)  DEFAULT NULL          COMMENT '调入门店名称',
    status          VARCHAR(30)   NOT NULL DEFAULT 'pending_confirm'
        COMMENT 'pending_confirm|confirmed|pending_ship|pending_receive|completed|cancelled|rejected',
    total_qty       DECIMAL(12,4) NOT NULL DEFAULT 0    COMMENT '调货总数量',
    creator_store_id VARCHAR(50)  DEFAULT NULL          COMMENT '发起门店ID',
    handoff         VARCHAR(30)   DEFAULT 'pending_return'     COMMENT '还货状态：pending_return(待还)|returned(已还)',
    remark          VARCHAR(500)  DEFAULT NULL          COMMENT '备注',
    created_by      VARCHAR(100)  DEFAULT NULL          COMMENT '发起人 openid',
    confirmed_by    VARCHAR(100)  DEFAULT NULL          COMMENT '确认人 openid',
    shipped_by      VARCHAR(100)  DEFAULT NULL          COMMENT '发货人 openid',
    received_by     VARCHAR(100)  DEFAULT NULL          COMMENT '收货人 openid',
    confirmed_at    DATETIME      DEFAULT NULL          COMMENT '确认时间',
    shipped_at      DATETIME      DEFAULT NULL          COMMENT '发货时间',
    received_at     DATETIME      DEFAULT NULL          COMMENT '收货时间',
    completed_at    DATETIME      DEFAULT NULL          COMMENT '完成时间',
    cancelled_at    DATETIME      DEFAULT NULL          COMMENT '取消时间',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag        INT           DEFAULT 0             COMMENT '删除标记',
    version         INT           DEFAULT 0             COMMENT '乐观锁',
    INDEX idx_from_store (from_store_id),
    INDEX idx_to_store (to_store_id),
    INDEX idx_status (status),
    INDEX idx_biz_code (biz_code),
    INDEX idx_creator_store (creator_store_id)
) COMMENT '调货单' ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 调货单明细表
CREATE TABLE transfer_order_item (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    transfer_id     BIGINT        NOT NULL              COMMENT '关联调货单ID',
    material_name   VARCHAR(200)  NOT NULL              COMMENT '物料名称',
    spec            VARCHAR(100)  DEFAULT ''            COMMENT '规格',
    unit            VARCHAR(50)   NOT NULL              COMMENT '单位',
    transfer_qty    DECIMAL(12,4) NOT NULL              COMMENT '调货数量',
    base_unit       VARCHAR(50)   DEFAULT NULL          COMMENT '最小盘点单位',
    base_qty        DECIMAL(12,4) DEFAULT NULL          COMMENT '最小单位数量',
    input_unit      VARCHAR(50)   DEFAULT NULL          COMMENT '录入单位',
    input_qty       DECIMAL(12,4) DEFAULT NULL          COMMENT '录入数量',
    unit_price      DECIMAL(10,2) DEFAULT NULL          COMMENT '单价（快照）',
    remark          VARCHAR(200)  DEFAULT NULL          COMMENT '备注',
    return_status   VARCHAR(20)   DEFAULT NULL          COMMENT '归还状态: returned_goods|returned_money',
    returned_qty    DECIMAL(12,4) DEFAULT 0             COMMENT '已归还数量',
    return_amount   DECIMAL(10,2) DEFAULT 0             COMMENT '已还金额',
    del_flag        INT           DEFAULT 0             COMMENT '删除标记',
    FOREIGN KEY (transfer_id) REFERENCES transfer_order(id),
    INDEX idx_transfer (transfer_id)
) COMMENT '调货单明细' ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 调货归还记录表
CREATE TABLE transfer_return_record (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_id       VARCHAR(50)   NOT NULL              COMMENT '业务ID',
    transfer_id     BIGINT        NOT NULL              COMMENT '关联调货单ID',
    item_id         BIGINT        NOT NULL              COMMENT '关联物料明细ID',
    return_type     VARCHAR(20)   NOT NULL              COMMENT 'goods|money',
    return_qty      DECIMAL(10,2) DEFAULT 0             COMMENT '归还数量',
    return_amount   DECIMAL(10,2) DEFAULT 0             COMMENT '归还金额',
    unit_price      DECIMAL(10,2) DEFAULT NULL          COMMENT '单价快照',
    remark          VARCHAR(500)  DEFAULT NULL          COMMENT '备注',
    handler_name    VARCHAR(100)  DEFAULT NULL          COMMENT '经手人',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_trr_transfer (transfer_id),
    INDEX idx_trr_item (item_id)
) COMMENT '调货归还记录' ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 物流记录表
CREATE TABLE logistics_record (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    transfer_id     BIGINT        DEFAULT NULL          COMMENT '关联调货单ID',
    store_id        VARCHAR(50)   NOT NULL              COMMENT '关联门店ID',
    store_name      VARCHAR(200)  DEFAULT NULL          COMMENT '门店名称',
    tracking_no     VARCHAR(100)  NOT NULL              COMMENT '运单号',
    carrier         VARCHAR(100)  DEFAULT NULL          COMMENT '承运商',
    status          VARCHAR(30)   NOT NULL DEFAULT 'pending_shipment'
        COMMENT 'pending_shipment|in_transit|delivering|signed|abnormal|query_failed',
    tracking_data   TEXT          DEFAULT NULL          COMMENT '轨迹数据JSON',
    created_by      VARCHAR(100)  DEFAULT NULL          COMMENT '录入人',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag        INT           DEFAULT 0             COMMENT '删除标记',
    UNIQUE KEY uk_tracking_no (tracking_no),
    INDEX idx_store (store_id),
    INDEX idx_transfer (transfer_id),
    INDEX idx_status (status)
) COMMENT '物流记录' ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 物料报损可见标记：见 material 表 loss_visible 字段（已内联）

-- ============================================================
-- P0 门店报损（A2）
-- ============================================================

CREATE TABLE loss_report (
  id bigint NOT NULL AUTO_INCREMENT,
  biz_code varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '业务编码',
  store_id varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  store_name varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  loss_type varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'daily|arrival',
  loss_object varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'finished' COMMENT 'finished|semi_finished',
  material_id varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  material_name varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '物料名称（多物料报损为空）',
  spec varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT '',
  input_unit varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '填报单位',
  input_qty decimal(12,4) DEFAULT NULL COMMENT '填报数量（多物料报损为空）',
  unit_price decimal(10,2) DEFAULT NULL COMMENT '单价',
  total_amount decimal(10,2) DEFAULT NULL COMMENT '金额（数量×单价）',
  base_unit varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最小单位',
  base_qty decimal(12,4) DEFAULT NULL COMMENT '换算到最小单位的数量',
  qimai_order_no varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '企迈单号',
  gross_weight decimal(10,2) DEFAULT NULL COMMENT '含容器重量(g)（多物料报损为空）',
  container_id bigint DEFAULT NULL COMMENT '容器ID（多物料报损为空）',
  container_name varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '容器名称(快照)（多物料报损为空）',
  container_weight decimal(10,2) DEFAULT NULL COMMENT '容器皮重(g,快照)（多物料报损为空）',
  net_weight decimal(10,2) DEFAULT NULL COMMENT '净重(g)（多物料报损为空）',
  reason varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '报损原因',
  occurred_date date NOT NULL,
  handler_name varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '登记人',
  voucher_url varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '凭证图片URL（逗号分隔）',
  remark varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  status varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'pending' COMMENT 'pending_approval|pending|confirmed_resend|rejected|completed|received|not_received|closed',
  reject_reason varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '拒绝原因',
  submitted_by varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '提交人openid',
  confirmed_by varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '确认人',
  confirmed_at datetime DEFAULT NULL COMMENT '确认时间',
  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  urgent int DEFAULT 0 COMMENT '0=否 1=加急',
  item_count int DEFAULT 0 COMMENT '明细条数：0=扁平单物料/到货，>0=多物料走明细表',
  del_flag int DEFAULT 0,
  version int DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_store (store_id),
  KEY idx_type (loss_type),
  KEY idx_status (status),
  KEY idx_date (occurred_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='门店报损记录';

CREATE TABLE loss_report_log (
  id bigint NOT NULL AUTO_INCREMENT,
  report_id bigint NOT NULL COMMENT '报损记录ID',
  action varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作类型: submit|approve|reject_approval|confirm|reject|receive|not_receive',
  operator varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作人',
  remark varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注说明',
  created_at datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  INDEX idx_report (report_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='报损操作日志';

CREATE TABLE loss_report_item (
  id bigint NOT NULL AUTO_INCREMENT,
  report_id bigint NOT NULL COMMENT '关联 loss_report.id',
  loss_object varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'semi_finished' COMMENT 'finished|semi_finished',
  material_id varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '物料ID',
  material_name varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物料名称',
  spec varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '规格',
  input_unit varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '填报单位',
  input_qty decimal(12,4) DEFAULT NULL COMMENT '填报数量',
  unit_price decimal(10,2) DEFAULT NULL COMMENT '单价',
  total_amount decimal(10,2) DEFAULT NULL COMMENT '金额（数量×单价）',
  base_unit varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '基础单位',
  base_qty decimal(12,4) DEFAULT NULL COMMENT '基础单位数量',
  gross_weight decimal(10,2) DEFAULT NULL COMMENT '含容器总重(克)',
  container_id bigint DEFAULT NULL COMMENT '容器ID',
  container_name varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '容器名称快照',
  container_weight decimal(10,2) DEFAULT NULL COMMENT '容器皮重快照(克)',
  net_weight decimal(10,2) DEFAULT NULL COMMENT '净重(克)',
  sort_no int DEFAULT 0 COMMENT '展示顺序',
  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag int DEFAULT 0 COMMENT '删除标记',
  PRIMARY KEY (id),
  INDEX idx_report (report_id),
  INDEX idx_material (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='报损明细（日常报损多物料用）';

CREATE TABLE container_config (
  id bigint NOT NULL AUTO_INCREMENT,
  container_name varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  alias varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '别称，胶囊展示用',
  tare_weight decimal(10,2) NOT NULL COMMENT '皮重(g)',
  image varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '容器图片URL',
  store_id varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  status tinyint NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag int DEFAULT 0,
  PRIMARY KEY (id),
  INDEX idx_store (store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='容器配置';

CREATE TABLE material_loss_notify_config (
  id bigint NOT NULL AUTO_INCREMENT,
  category varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '物料分类(逗号分隔，如:水果蔬菜,茶叶)，其中"其他类"为兜底',
  feishu_user_id varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '飞书user_id/@的人',
  chat_id varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '群chat_id',
  status tinyint DEFAULT 1 COMMENT '1启用 0停用 2停用',
  created_at datetime DEFAULT CURRENT_TIMESTAMP,
  updated_at datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='物料报损飞书通知配置';

CREATE TABLE sys_config (
  id bigint NOT NULL AUTO_INCREMENT,
  config_key varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置键',
  config_value varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '配置值',
  description varchar(300) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '说明',
  created_at datetime DEFAULT CURRENT_TIMESTAMP,
  updated_at datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_key (config_key),
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置';

-- ============================================================
-- 十二、A1 盘点优化
-- ============================================================

-- 条码补充申请表
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

-- 盘点差异表
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

-- 差异处理日志
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

-- 盘点模板推荐表
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
-- 十三、B1 问题处理
-- ============================================================

CREATE TABLE issue (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    biz_code        VARCHAR(50)   NOT NULL COMMENT '业务编码',
    store_id        VARCHAR(50)   NOT NULL COMMENT '提交门店ID',
    store_name      VARCHAR(200)  DEFAULT NULL COMMENT '门店名称',
    title           VARCHAR(200)  NOT NULL COMMENT '问题标题',
    issue_type      VARCHAR(50)   NOT NULL COMMENT '问题类型',
    sub_type        VARCHAR(50)   DEFAULT NULL COMMENT '子类型（如设备问题下的制冰机/净水器）',
    urgency         VARCHAR(50)   NOT NULL COMMENT '紧急程度（MD原文）：严重-影响营业或有客诉|一般-影响效率和体验|轻微-期望优化',
    description     VARCHAR(2000) DEFAULT '' COMMENT '问题描述',
    contact_name    VARCHAR(50)   DEFAULT '' COMMENT '联系人',
    contact_phone   VARCHAR(20)   DEFAULT '' COMMENT '联系电话',
    images          TEXT          DEFAULT NULL COMMENT '图片URL（逗号分隔）',
    xiangmu_id      VARCHAR(100)  DEFAULT NULL COMMENT '象目经理同步单号（去重比对键）',
    sync_status     VARCHAR(20)   DEFAULT NULL COMMENT '象目经理同步状态：pending|synced|failed',
    status          VARCHAR(30)   NOT NULL DEFAULT 'IN_PROGRESS' COMMENT 'IN_PROGRESS|pending|processing|pending_acceptance|resolved|closed',
    source          VARCHAR(30)   DEFAULT NULL COMMENT '来源渠道：MINI_PROGRAM(小程序)|FEISHU_GROUP(飞书群H5)|HQ(总部上报)|null(其他旧数据)',
    process_result    VARCHAR(1000) DEFAULT NULL COMMENT '处理结果/最新进度（象目经理同步回来）',
    acceptance_remark VARCHAR(500)  DEFAULT NULL COMMENT '解决原因',
    accepted_by       VARCHAR(100)  DEFAULT NULL COMMENT '验收人 openid',
    accepted_at       DATETIME      DEFAULT NULL COMMENT '验收时间',
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
    INDEX idx_biz_code (biz_code),
    INDEX idx_source (source)
) COMMENT '问题处理单' ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================
-- 十四、B2 企微通知
-- ============================================================

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
-- 十五、P2 督导拜访
-- ============================================================

CREATE TABLE supervisor_visit (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    visit_no            VARCHAR(50)   NOT NULL COMMENT '拜访单编号（系统生成，如 SV202607210001）',
    store_id            VARCHAR(50)   NOT NULL COMMENT '拜访门店ID',
    store_name          VARCHAR(200)  DEFAULT NULL COMMENT '门店名称（冗余快照）',
    supervisor_name     VARCHAR(50)   NOT NULL COMMENT '督导姓名',
    visit_date          DATE          NOT NULL COMMENT '拜访日期',
    confirm_person_type   VARCHAR(30)   NOT NULL DEFAULT 'store_manager' COMMENT '确认人类型：store_manager(店长) / owner(老板) / both(同时发送)',
    confirm_manager_openid VARCHAR(100) DEFAULT NULL COMMENT '选中的店长确认人 openid',
    confirm_manager_name   VARCHAR(100) DEFAULT NULL COMMENT '选中的店长确认人姓名',
    confirm_owner_openid   VARCHAR(100) DEFAULT NULL COMMENT '选中的加盟商老板确认人 openid',
    confirm_owner_name     VARCHAR(100) DEFAULT NULL COMMENT '选中的加盟商老板确认人姓名',
    visit_status          VARCHAR(30)   NOT NULL DEFAULT 'draft' COMMENT '拜访状态：draft(草稿) / pending_confirm(待确认) / objection(异议待处理) / in_progress(跟进中) / completed(已完成)',
    confirm_status      VARCHAR(30)   NOT NULL DEFAULT 'unsent' COMMENT '确认状态：unsent(未发送) / pending(待确认) / confirmed(已确认) / objected(已提出异议)',
    confirmed_by        VARCHAR(100)  DEFAULT NULL COMMENT '实际确认人 openid',
    confirmed_at        DATETIME      DEFAULT NULL COMMENT '确认时间',
    objection_reason    VARCHAR(1000) DEFAULT NULL COMMENT '异议说明',
    last_issue_review   TEXT          DEFAULT NULL COMMENT '回顾上次访店问题与行动追踪',
    current_focus       TEXT          DEFAULT NULL COMMENT '门店当下重点关注',
    improvement_focus   TEXT          DEFAULT NULL COMMENT '本月提升重点沟通记录',
    store_feedback      TEXT          DEFAULT NULL COMMENT '门店 & 加盟商反馈与所需支持',
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

CREATE TABLE supervisor_visit_action (
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

-- ============================================================
-- 十六、P2 督导门店访问权限映射
-- ============================================================

CREATE TABLE IF NOT EXISTS supervisor_store_access (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    open_id     VARCHAR(100)  NOT NULL COMMENT '关联 admin_permission.open_id',
    admin_name  VARCHAR(100)  NOT NULL COMMENT '督导/管理员姓名（冗余）',
    store_id    VARCHAR(50)   NOT NULL COMMENT '可访问的门店ID',
    store_name  VARCHAR(200)  DEFAULT NULL COMMENT '门店名称（冗余）',
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    del_flag    INT           DEFAULT 0 COMMENT '删除标记',
    UNIQUE INDEX uk_openid_store (open_id, store_id),
    INDEX idx_open_id (open_id),
    INDEX idx_store_id (store_id)
) COMMENT '督导门店访问权限映射' ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================
-- 十三、智能订货（V17）
-- ============================================================

-- 建议订货单
CREATE TABLE smart_order (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  biz_code        VARCHAR(50)   NOT NULL COMMENT '业务编码',
  store_id        VARCHAR(50)   NOT NULL COMMENT '门店ID',
  store_name      VARCHAR(200)  DEFAULT NULL COMMENT '门店名称快照',
  week_start_date DATE          NOT NULL COMMENT '订货周周一(ISO周)',
  week_label      VARCHAR(20)   NOT NULL COMMENT '周标签,如 2026-W34',
  task_id         BIGINT        DEFAULT NULL COMMENT '来源周盘任务ID',
  status          VARCHAR(20)   NOT NULL DEFAULT 'pending' COMMENT 'pending待确认|syncing同步中|success同步成功|submit_failed提交失败',
  item_count      INT           NOT NULL DEFAULT 0 COMMENT '建议品项数',
  total_qty       DECIMAL(12,4) NOT NULL DEFAULT 0 COMMENT '建议数量合计(订货单位)',
  suggest_amount  DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '建议金额合计(元)',
  deadline        DATETIME      DEFAULT NULL COMMENT '截止时间(仅展示,不强制)',
  generated_at    DATETIME      DEFAULT NULL COMMENT '生成时间',
  confirmed_by    VARCHAR(100)  DEFAULT NULL COMMENT '确认人openid',
  confirmed_at    DATETIME      DEFAULT NULL COMMENT '确认时间',
  qmai_declare_no VARCHAR(100)  DEFAULT NULL COMMENT '企迈报货单号',
  submit_error    VARCHAR(1000) DEFAULT NULL COMMENT '企迈提交错误信息',
  sync_attempts   INT           NOT NULL DEFAULT 0 COMMENT '提交尝试次数',
  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag        INT           DEFAULT 0 COMMENT '删除标记',
  version         INT           DEFAULT 0 COMMENT '乐观锁',
  UNIQUE KEY uk_store_week (store_id, week_start_date),
  UNIQUE KEY uk_store_task (store_id, task_id),
  INDEX idx_status (status),
  INDEX idx_biz_code (biz_code)
) COMMENT '智能订货建议单' ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 建议订货单明细（生成时快照物料信息）
CREATE TABLE smart_order_item (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id          BIGINT        NOT NULL COMMENT '关联订货单ID',
  material_id       BIGINT        DEFAULT NULL COMMENT '物料ID(关联material.id)',
  material_name     VARCHAR(200)  NOT NULL COMMENT '物料名称快照',
  spec              VARCHAR(100)  DEFAULT '' COMMENT '规格快照',
  category          VARCHAR(100)  DEFAULT NULL COMMENT '分类快照',
  qm_code           VARCHAR(100)  DEFAULT NULL COMMENT '企迈编码快照(productCode)',
  stock_unit        VARCHAR(50)   NOT NULL COMMENT '订货单位(库存单位)',
  base_unit         VARCHAR(50)   DEFAULT NULL COMMENT '基础盘点单位',
  unit_price        DECIMAL(10,2) DEFAULT NULL COMMENT '单价快照(元)',
  current_inventory DECIMAL(18,4) DEFAULT NULL COMMENT '当前库存(基础单位)',
  daily_use         DECIMAL(18,4) DEFAULT NULL COMMENT '预测日均消耗(基础单位/天, 去年同期×趋势或降级值)',
  last_year_qty     DECIMAL(12,4) DEFAULT NULL COMMENT '去年同周销量快照(基础单位)',
  trend_factor      DECIMAL(6,3)  DEFAULT NULL COMMENT '趋势系数(近4周÷去年同4周, clamp 0.5~2.0)',
  loss_qty          DECIMAL(12,4) DEFAULT NULL COMMENT '损耗修正(基础单位, 近4周报损周均)',
  transfer_qty      DECIMAL(12,4) DEFAULT NULL COMMENT '调货净值修正(基础单位, 近4周周均, 净调出为正)',
  return_qty        DECIMAL(12,4) DEFAULT NULL COMMENT '还货净值修正(基础单位, 近4周周均, 仅还货品goods, 净还出为正; 还钱不影响实物不计)',
  in_transit_qty    DECIMAL(12,4) DEFAULT NULL COMMENT '在途量(基础单位, 累计订货-累计到货差值法)',
  self_purchase_qty DECIMAL(12,4) DEFAULT NULL COMMENT '自购食材修正(基础单位, 近4周自购周均, 减项)',
  cycle_days        INT           DEFAULT NULL COMMENT '配送周期天数',
  safety_days       INT           DEFAULT NULL COMMENT '安全天数',
  suggest_qty       DECIMAL(12,4) NOT NULL COMMENT '建议数量(订货单位)',
  confirmed_qty     DECIMAL(12,4) DEFAULT NULL COMMENT '确认数量(订货单位)',
  support_days      DECIMAL(10,2) DEFAULT NULL COMMENT '当前库存可支撑天数',
  reason            VARCHAR(500)  DEFAULT NULL COMMENT '建议依据',
  sort_no           INT           DEFAULT NULL COMMENT '排序号',
  created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag          INT           DEFAULT 0 COMMENT '删除标记',
  version           INT           DEFAULT 0 COMMENT '乐观锁',
  FOREIGN KEY (order_id) REFERENCES smart_order(id),
  INDEX idx_order (order_id)
) COMMENT '智能订货建议单明细' ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 智能订货配置（一期总部统一配置，后续配送周期/安全天数改为企迈按店拉取）
INSERT INTO sys_config (config_key, config_value, description)
VALUES ('smart_order_delivery_cycle_days', '7', '智能订货配送周期天数(后续改为企迈按店)'),
       ('smart_order_safety_days', '3', '智能订货安全天数(后续改为企迈按店)'),
       ('smart_order_default_daily_use', '0.5', '智能订货默认日均消耗(基础单位/天,无两次盘点数据时兜底)'),
       ('smart_order_online_pay', '0', '智能订货企迈支付方式 0线下 1线上'),
       ('smart_order_deadline_time', '18:00:00', '智能订货截止时间(仅展示不强制)')
ON DUPLICATE KEY UPDATE config_key = config_key;

-- ============================================================
-- 建表完成
-- ============================================================
SELECT 'schema.sql executed successfully' AS status;
