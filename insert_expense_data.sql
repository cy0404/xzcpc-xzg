-- ============================================================
-- 盘点工具 — 支出分类与项目名称初始化数据
-- 数据来源：5个门店（翠湖、滇池名门、福保、福德、高山铺）3月"自购支出统计"+"支出表"
-- 对应关系：
--   expense_type  = 支出类型表/一级分类（自购支出统计的分类名称）
--   expense_item  = 支出项目表/二级分类（支出表的项目名称），type_id → expense_type.type_id
-- 生成时间：2026-06-15
-- ============================================================

-- ============================================================
-- 一、支出类型/一级分类（expense_type）
-- ============================================================
INSERT INTO expense_type (type_id, name, description, status) VALUES
('0w79rmaqsddbfvnn', '货运物流费', '物料到店产生的物流费或货拉拉运输费用', 'enabled'),
('lvg2n3z3xp5sbyzd',   '办公用品',    '签字笔、记号笔、笔记本、打印资料等', 'enabled'),
('43ux5mjp9pg9z4j8',       '广告物料',    '新品上市或菜单更新等广告店制作的物料', 'enabled'),
('7pw1r4m67dlbrrp4',       '消耗物料',    '门店日常用品（垃圾袋、订书针、扫把拖把等消耗品）', 'enabled'),
('naglb0r8m138x0e2',     '自购物料',    '门店自行购买的水果+食材物料', 'enabled'),
('g6uvb8varx01bi8r',     '通讯费',      '门店手机话费、网络费用', 'enabled'),
('9mwyx76h8xp0anjg',       '维护保养',    '设备、管道、电路维修费用', 'enabled'),
('8s4u642pog4n9z7e',         '水电费',      '门店、仓库所产生的水费、电费', 'enabled'),
('3m9keqei9nxns59e',         '宣传推广',    '线上推广费用、美团或饿了么刷单费用', 'enabled'),
('ot3snnuftgjrdthi',     '线上退款',    '美团、饿了么、小程序退款费用', 'enabled'),
('0l7yo8csovuthp6s',             '人力成本',    '新人员工试岗工资等临时人力支出（新增分类）', 'enabled'),
('hz9p6p0p1417gxfq',              '聚餐费',      '员工聚餐费用', 'enabled'),
('fpxh8kzqrke13b7e',         '兼职',        '兼职人员工资支出', 'enabled');

-- ============================================================
-- 二、项目名称/二级分类（expense_item）— 按分类逐一列出
-- ============================================================

-- 货运物流费
INSERT INTO expense_item (item_id, type_id, name) VALUES
('k1knt2lkur7ugfl0',       '0w79rmaqsddbfvnn', '运费'),
('qdn21zu69t8vl9w0',     '0w79rmaqsddbfvnn', '物流'),
('6xwpn16ndmwrnfjl',     '0w79rmaqsddbfvnn', '货运物流'),
('9nlzyfmugnna4z8v', '0w79rmaqsddbfvnn', '货运物流费'),
('5gxts6myhaypdcs4',        '0w79rmaqsddbfvnn', '跑腿'),
('jww8y6c0lci9o5so',    '0w79rmaqsddbfvnn', '跑腿费'),
('obvgs02dawsxr6re',          '0w79rmaqsddbfvnn', '打车费');

-- 办公用品
INSERT INTO expense_item (item_id, type_id, name) VALUES
('5t1hca68e7hqxnkl', 'lvg2n3z3xp5sbyzd', '办公用品');

-- 广告物料
INSERT INTO expense_item (item_id, type_id, name) VALUES
('rkacrlls4t7boojo', '43ux5mjp9pg9z4j8', '广告');

-- 消耗物料
INSERT INTO expense_item (item_id, type_id, name) VALUES
('vee4eklnewcutlpx',       '7pw1r4m67dlbrrp4', '消耗物料'),
('lhgh6pwthintd5u0', '7pw1r4m67dlbrrp4', '消耗品');

-- 自购物料
INSERT INTO expense_item (item_id, type_id, name) VALUES
('y25siqgh8x74j09s', 'naglb0r8m138x0e2', '自购物料');

-- 通讯费
INSERT INTO expense_item (item_id, type_id, name) VALUES
('dk0hibsfrvw571kj',         'g6uvb8varx01bi8r', '通讯费'),
('cl60q7p5a9iir2jq', 'g6uvb8varx01bi8r', '充值');

-- 维护保养
INSERT INTO expense_item (item_id, type_id, name) VALUES
('i5q3fnswkzbw3kjh',          '9mwyx76h8xp0anjg', '维护保养'),
('ny184jbg54gi3jyz',   '9mwyx76h8xp0anjg', '维修'),
('lgxblrdz3kq162cz','9mwyx76h8xp0anjg', '维修费用');

-- 水电费
INSERT INTO expense_item (item_id, type_id, name) VALUES
('zb52alw93k8kgj2a', '8s4u642pog4n9z7e', '水电费');

-- 宣传推广
INSERT INTO expense_item (item_id, type_id, name) VALUES
('alkxxh56vcft3kkc',       '3m9keqei9nxns59e', '宣传推广'),
('1rt1o6yatxibs2st', '3m9keqei9nxns59e', '企迈充值');

-- 线上退款
INSERT INTO expense_item (item_id, type_id, name) VALUES
('rp0urpbrf9h7gdgs',           'ot3snnuftgjrdthi', '线上退款'),
('95ty0bvdwkda94n2',      'ot3snnuftgjrdthi', '差价'),
('uvbnuzvmqcwoz14q', 'ot3snnuftgjrdthi', '顾客赔偿');

-- 人力成本（原"其他"中的试岗类拆分至此）
INSERT INTO expense_item (item_id, type_id, name) VALUES
('rzbxj33q3w7glbrl',         '0l7yo8csovuthp6s', '试岗'),
('wonub28jjhixogo5', '0l7yo8csovuthp6s', '试岗工资预支'),
('qxsj3zs02ut72oao',  '0l7yo8csovuthp6s', '试岗薪资'),
('uk2knjoz01ocufbw',  '0l7yo8csovuthp6s', '新人试岗');

-- 聚餐费（原"其他"中的员工聚餐拆分至此）
INSERT INTO expense_item (item_id, type_id, name) VALUES
('ib0ysis76x2ribr4', 'hz9p6p0p1417gxfq', '员工聚餐');

-- ============================================================
-- 三、"其他" 项目处理说明
-- ============================================================
-- "其他"是各门店使用的通用项目名，根据详细描述拆分如下：
-- ┌──────────────┬──────────────┬─────────────────────────────┐
-- │ 描述         │ 新项目名     │ 归属分类                    │
-- ├──────────────┼──────────────┼─────────────────────────────┤
-- │ 新人试岗X天  │ 新人试岗     │ 人力成本 (0l7yo8csovuthp6s)       │
-- │ 新人打包错了 │ 顾客赔偿     │ 线上退款 (ot3snnuftgjrdthi)│
-- │ X人员工聚餐  │ 员工聚餐     │ 聚餐费 (hz9p6p0p1417gxfq)          │
-- │ 赔偿给顾客   │ 顾客赔偿     │ 线上退款 (ot3snnuftgjrdthi)│
-- │ 打车费       │ 打车费       │ 货运物流费 (0w79rmaqsddbfvnn)│
-- └──────────────┴──────────────┴─────────────────────────────┘

