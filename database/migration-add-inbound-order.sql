-- ============================================================
-- 入库管理：入库单 + 入库单商品明细
-- 数据来源：企迈控制台 API (inapi.qmai.cn) + OpenAPI 报货单匹配
--
-- 注意：本脚本可重复执行（含 DROP）。旧版本表结构（declare_no 列）
-- 与当前实体（inbound_no 列）不兼容，必须 DROP 重建。
-- ⚠ DROP 会清空这两张表的全部数据。
-- 测试库(store_inventory_test)和生产库(store_inventory)都要执行。
-- ============================================================

DROP TABLE IF EXISTS inbound_order_item;
DROP TABLE IF EXISTS inbound_order;

CREATE TABLE inbound_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id VARCHAR(50) NOT NULL COMMENT '本地门店ID',
    qmai_store_id BIGINT COMMENT '企迈门店ID（归属匹配层级1 按门店拉报货单用的键）',
    warehouse_id VARCHAR(50) COMMENT '企迈控制台仓库ID（9.2.2 warehouseId）',
    warehouse_code VARCHAR(50) COMMENT '企迈开放平台仓库编号（9.2.2 warehouseCode，确认收货时回传 warehouseNo 用）',
    warehouse_name VARCHAR(100) COMMENT '仓库名称',
    inbound_no VARCHAR(100) NOT NULL COMMENT '入库单号（控制台 inboundNo）',
    biz_no VARCHAR(100) COMMENT '关联单号（控制台 bizNo）',
    source_declare_no VARCHAR(100) COMMENT '来源报货单号（匹配到的 OpenAPI declareNo）',
    source_require_no VARCHAR(100) COMMENT '来源订货单号（匹配到的 OpenAPI requireNo）',
    inbound_at DATETIME COMMENT '入库时间（控制台 inboundAt）',
    document_date VARCHAR(20) COMMENT '单据日期',
    inbound_type INT COMMENT '入库类型：1期初 2盘盈 3采购 4调拨 5退货 6其他 10加工',
    status INT COMMENT '控制台原始状态：1待入库 2已入库 3已关闭',
    amount DECIMAL(12,2) COMMENT '入库金额（元）',
    product_all_num DECIMAL(12,4) COMMENT '货品总数量',
    product_type_num INT COMMENT '货品种类数',
    creator VARCHAR(50) COMMENT '创建人',
    inbound_person VARCHAR(50) COMMENT '入库人',
    remark VARCHAR(500) COMMENT '备注',
    created_at_qmai DATETIME COMMENT '控制台创建时间',
    local_status VARCHAR(30) DEFAULT 'pending' COMMENT '本地入库状态：pending=待入库 done=已入库 cancelled=已关闭',
    received_at DATETIME COMMENT '本地入库完成时间',
    received_by VARCHAR(50) COMMENT '入库操作人',
    version INT DEFAULT 0 COMMENT '乐观锁',
    del_flag INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_inbound_no (inbound_no),
    INDEX idx_store_id (store_id),
    INDEX idx_biz_no (biz_no),
    INDEX idx_local_status (local_status)
) COMMENT '入库单（企迈控制台数据本地缓存）';

CREATE TABLE inbound_order_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    inbound_order_id BIGINT NOT NULL COMMENT '入库单ID',
    product_code VARCHAR(50) COMMENT '企迈商品编码',
    product_id BIGINT COMMENT '企迈商品ID',
    product_name VARCHAR(200) COMMENT '商品名称',
    product_spec VARCHAR(200) COMMENT '规格',
    product_unit VARCHAR(50) COMMENT '订货单位',
    product_num DECIMAL(12,2) COMMENT '报货数量',
    price DECIMAL(10,2) COMMENT '单价（元）',
    amount DECIMAL(12,2) COMMENT '金额（元）',
    is_gift INT DEFAULT 0 COMMENT '是否赠品 1=是 0=否',
    img_url VARCHAR(500) COMMENT '商品图片',
    performance_name VARCHAR(100) COMMENT '履约方名称',
    received_qty DECIMAL(12,2) DEFAULT 0 COMMENT '已收货数量',
    received INT DEFAULT 0 COMMENT '是否已收货 0=否 1=是',
    version INT DEFAULT 0,
    del_flag INT DEFAULT 0,
    INDEX idx_order_id (inbound_order_id)
) COMMENT '入库单商品明细';

-- Store 表新增字段（幂等：列已存在时自动跳过）
SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'store_info' AND COLUMN_NAME = 'warehouse_id'
);
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE store_info ADD COLUMN warehouse_id VARCHAR(50) DEFAULT NULL COMMENT ''企迈控制台仓库ID'' AFTER qmai_store_id',
    'SELECT ''skip: store_info.warehouse_id already exists''');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
