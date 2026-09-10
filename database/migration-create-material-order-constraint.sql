-- ============================================================
-- 企迈商品订货约束表（起订量 / 订货倍数 / 限购）
-- 背景：企迈下单页对部分商品有「起订数量 / 订货倍数 / 限购数量」约束，智能订货按缺口精算
--       会算出 2 瓶酸角这类下不了单的量（酸角起订 12 瓶），店长必须手动改成整件。
--       本表存约束，生成建议量时做 max(起订量, ceil(缺口/倍数)*倍数) 取整。
-- 数据来源：企迈控制台订货模板（POST /gw/scm/console/route/order/list + detail）
--          2026-09-10 拉取：20 个模板 / 398 商品 / 34 条有约束
-- 执行库：store_inventory（正式）均需执行（脚本无 USE）
-- 维护：企迈侧调整约束后，重跑拉取脚本刷新本表（按 qm_code 幂等 upsert）
-- ============================================================

CREATE TABLE IF NOT EXISTS `material_order_constraint` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `qm_code`       VARCHAR(64)  NOT NULL                COMMENT '企迈商品编码（如 WP0733）',
  `material_id`   VARCHAR(64)  DEFAULT NULL            COMMENT '本地物料业务码（关联 material.material_id，冗余便于关联）',
  `material_name` VARCHAR(100) DEFAULT NULL            COMMENT '物料名称快照',
  `order_unit`    VARCHAR(20)  DEFAULT NULL            COMMENT '约束口径单位（订货单位，如 瓶/g/份/件）',
  `min_order_qty` DECIMAL(12,4) DEFAULT NULL           COMMENT '起订数量（建议量低于此值则提升到此值；NULL=无约束）',
  `order_multiple` DECIMAL(12,4) DEFAULT NULL          COMMENT '订货倍数（建议量向上取整到该倍数；NULL=无约束）',
  `limit_qty`     DECIMAL(12,4) DEFAULT NULL           COMMENT '单次限购上限（NULL=不限购）',
  `source`        VARCHAR(32)  DEFAULT 'qmai_console'  COMMENT '来源：qmai_console=企迈控制台订货模板',
  `remark`        VARCHAR(255) DEFAULT NULL            COMMENT '备注（规格 / 模板启用状态）',
  `created_at`    DATETIME     DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `del_flag`      TINYINT      DEFAULT 0               COMMENT '逻辑删除 0=正常 1=删除',
  `version`       INT          DEFAULT 0               COMMENT '乐观锁',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_qm_code` (`qm_code`),
  KEY `idx_material_id` (`material_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='企迈商品订货约束（起订量/倍数/限购）——智能订货建议量取整依据';

-- ------------------------------------------------------------
-- 数据：2026-09-10 从企迈控制台订货模板拉取（34 条）
-- ------------------------------------------------------------
-- 数据导出：企迈控制台订货模板（route/order）拉取，2026-09-10
-- 字段口径：起订量/倍数/限购均为「订货单位」的数量；NULL=无该约束

INSERT INTO `material_order_constraint`
  (`qm_code`, `material_id`, `material_name`, `order_unit`, `min_order_qty`, `order_multiple`, `limit_qty`, `remark`) VALUES
  ('WP0362', 'cmpdj8kpz01sf3pmide9ef5s3', '云南红糖', '包', 10, 10, NULL, '1000g/包*10包/件'),
  ('WP0639', 'cmpdj8itd012q3pmih1bdgb13', '厚椰乳', '件', 1, NULL, 10, '1L/瓶*12瓶/件'),
  ('WP0940', 'cmsa28vnr0bo83pq3p2jmrlo7', '8周年-风车', '个', 50, 50, NULL, '500个/件'),
  ('WP0973', 'cmpdox4nkgn2d6h9egr1r4tj', '刺绣书签', '捆', 1, NULL, 10, '20个/包'),
  ('WP0676', 'cmpdj8inh010i3pmifxvsoxu8', '牛油果杯套', '捆', 20, 20, NULL, '50张/捆*20捆/件'),
  ('WP0664', 'cmpdj8ipk011c3pmia5qhfvpa', '香茅', 'g', 100, 100, NULL, '1000g/kg'),
  ('WP0893', NULL, '青芒香片', '个', 10, NULL, 30, '个 [模板未启用]'),
  ('WP0364', 'cmpdj8koz01s53pmidkhb5vp9', '剥壳巴旦木1公斤', '包', 10, 10, 50, '1000g/包*10包/件'),
  ('WP0651', 'cmpdj8iqw011w3pmicw6p2u2b', '胖胖瓶', '件', 1, NULL, 3, '100个/件'),
  ('WP0337', 'cmpdj8kwz01un3pmiskqvm04x', '水果糖浆.', '瓶', 1, NULL, 15, '1kg/瓶*12瓶/件'),
  ('WP0778', 'cmpdj8i8k00ut3pmijdy8024o', '云南杯套（新）', '捆', 20, 20, NULL, '50张/捆*20捆/件'),
  ('WP0735', 'cmpdj8igd00xq3pmill561jfy', '云南风物杯套', '捆', 20, 20, NULL, '50张/捆*20捆/件'),
  ('WP0733', 'cmpdj8igq00xv3pmi7d642fox', '酸角果泥果酱', '瓶', 12, 12, NULL, '1kg/瓶*12瓶/件'),
  ('WP0877', 'cmq28fb1803x43pq3ign05xbp', '南姜', 'g', 300, 100, NULL, '1000g/kg'),
  ('WP0876', 'cmq28fb1q03x93pq3fih3cin7', '金桔柠檬', 'g', 300, 100, NULL, '1000g/kg'),
  ('WP0875', 'cmq28fb2603xe3pq3cgu4lnxp', '柠檬叶', 'g', 100, 100, NULL, '1000g/kg'),
  ('WP0780', 'cmpdj8i7r00uj3pmiipvc098n', '马年红包', '包', 1, NULL, NULL, '5个/包'),
  ('WP0608', 'cmpdj8j0m01583pmi0eajckng', '大象冰箱贴（亚克力）', '个', 1, NULL, 50, '1/个'),
  ('WP0863', NULL, '小象搬果海绵擦', '个', 10, NULL, 22, '个 [模板未启用]'),
  ('WP0864', NULL, '芭乐释迦杯套', '捆', NULL, NULL, 15, '50张/捆 [模板未启用]'),
  ('WP0971', 'cmpdoxt6b198nl9hxhcywt99', '云边乌龙茶', '件', NULL, NULL, 1, '100g/包*25包/箱'),
  ('TJ00015', 'cmpdox2hnbg1dtbdgudw8tsg', '【新店】八周年', '套', NULL, NULL, 1, '[模板未启用]'),
  ('TJ00014', 'cmpdoxthakmzjb4mgij264gn', '【L4门店】八周年', '套', NULL, NULL, 1, '[模板未启用]'),
  ('TJ00013', 'cmpdoxvjjczz4vaag7chthci', '【L3门店】八周年', '套', NULL, NULL, 1, '[模板未启用]'),
  ('TJ00012', 'cmpdox2ub44kxy4w50t9vnsl', '【L2门店】八周年', '套', NULL, NULL, 1, '[模板未启用]'),
  ('TJ00011', 'cmpdoxdb8pvnz8371h71ia1o', '【L1门店】八周年', '套', NULL, NULL, 1, '[模板未启用]'),
  ('TJ00010', 'cmpdoxlr55qkpnw1pz5x9j3v', '【高山铺】八周年', '套', NULL, NULL, 1, '[模板未启用]'),
  ('TJ00009', 'cmpdoxbfexv266lx1kdt4czg', '【大理古城】八周年', '套', NULL, NULL, 1, '[模板未启用]'),
  ('WP0354', 'cmpdj8ksx01t43pmiwc297n9j', '冷冻牛奶米布', '件', NULL, NULL, 3, '500g/包*20包/件'),
  ('WP0352', 'cmpdj8ktc01t93pmib2i7tyrh', '冷冻牛油果泥(24包/件)', '件', 1, NULL, 30, '250g/包*24包/件'),
  ('WP0383', 'cmpdj8kl201qr3pmix7x4o40w', '乍甸酸奶', '份', 5, 5, NULL, '180g/包*20包/份'),
  ('WP0642', 'cmpdj8isx012l3pmi1rwagefc', '咸法干酪乳', '瓶', 1, NULL, 24, '1L/瓶*12瓶/件'),
  ('WP0358', 'cmpdj8krb01su3pmidrsp9h4l', '冷冻滇橄榄原汁', '瓶', 6, 6, NULL, '1L/瓶 * 6瓶/件'),
  ('WP0717', NULL, '猕猴桃贴纸（40小张）', '捆', NULL, NULL, 5, '40小张/捆 [模板未启用]')
ON DUPLICATE KEY UPDATE
  `material_id`=VALUES(`material_id`), `material_name`=VALUES(`material_name`),
  `order_unit`=VALUES(`order_unit`), `min_order_qty`=VALUES(`min_order_qty`),
  `order_multiple`=VALUES(`order_multiple`), `limit_qty`=VALUES(`limit_qty`),
  `remark`=VALUES(`remark`), `updated_at`=NOW();

-- 校验
-- SELECT qm_code, material_name, order_unit, min_order_qty, order_multiple, limit_qty
-- FROM material_order_constraint WHERE del_flag=0 ORDER BY min_order_qty DESC;
-- SELECT COUNT(*) FROM material_order_constraint;  -- 应为 34
