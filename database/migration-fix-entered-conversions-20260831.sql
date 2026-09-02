-- ============================================================
-- 已录入数据修正：按修正后的换算链重算 input_qty（2026-08-31）
-- 背景：换算链修正前录入的"包/瓶"数据，input_qty 按旧链落库；
--       链修正后已录入行不会自动重算，本脚本按 unit_inputs 原文
--       × 当前正确链重算。
-- 涉及：新黑糖粉(旧1000g/包→500g/包)、奇亚籽(旧1000g/包→250g/包)
--       （柠檬除胶剂 瓶 行见文末确认清单，未包含在本脚本）
-- 用法：核对下方清单 → 生产库执行（可重复执行，幂等）
-- ============================================================

-- 核对：执行前先跑下面查询看每行旧值/新值：
-- SELECT id, task_id, material_id, input_qty, base_qty, base_unit_snapshot,
--        unit_inputs, conversion_snapshot
-- FROM task_zone_material WHERE id IN (...ids...);

UPDATE task_zone_material
SET input_qty = 1500.0, base_qty = 1500.0
WHERE id = 121840;
-- 门店：象子茶铺茶保山板桥镇店 物料：新黑糖粉 原明细 {"g":"","包":"3"} 旧值 3000.0 新值 1500.0
UPDATE task_zone_material
SET input_qty = 500.0, base_qty = 500.0
WHERE id = 124540;
-- 门店：象子茶铺茶丽江祥和店 物料：新黑糖粉 原明细 {"g":"","包":"1"} 旧值 1000.0 新值 500.0
UPDATE task_zone_material
SET input_qty = 2350.0, base_qty = 2350.0
WHERE id = 125440;
-- 门店：象子茶铺茶勐海店 物料：新黑糖粉 原明细 {"g":"350","包":"4"} 旧值 4350.0 新值 2350.0
UPDATE task_zone_material
SET input_qty = 1356.4, base_qty = 1356.4
WHERE id = 128500;
-- 门店：象子茶铺茶丽江永胜店 物料：新黑糖粉 原明细 {"g":"856.4","包":"1"} 旧值 1856.4 新值 1356.4
UPDATE task_zone_material
SET input_qty = 1000.0, base_qty = 1000.0
WHERE id = 128860;
-- 门店：象子茶铺茶天骄北麓店 物料：新黑糖粉 原明细 {"g":"","包":"2"} 旧值 2000.0 新值 1000.0
UPDATE task_zone_material
SET input_qty = 1000.0, base_qty = 1000.0
WHERE id = 130660;
-- 门店：象子茶铺茶玉溪华宁盘溪镇店 物料：新黑糖粉 原明细 {"g":"","包":"2"} 旧值 2000.0 新值 1000.0
UPDATE task_zone_material
SET input_qty = 500.0, base_qty = 500.0
WHERE id = 132280;
-- 门店：象子茶铺茶南屏街世纪广场店 物料：新黑糖粉 原明细 {"g":"","包":"1"} 旧值 1000.0 新值 500.0
UPDATE task_zone_material
SET input_qty = 1500.0, base_qty = 1500.0
WHERE id = 133180;
-- 门店：象子茶铺茶红河泸西店 物料：新黑糖粉 原明细 {"g":"500","包":"2"} 旧值 2500.0 新值 1500.0
UPDATE task_zone_material
SET input_qty = 1000.0, base_qty = 1000.0
WHERE id = 133900;
-- 门店：象子茶铺茶曲靖花柯店 物料：新黑糖粉 原明细 {"g":"","包":"2"} 旧值 2000.0 新值 1000.0
UPDATE task_zone_material
SET input_qty = 500.0, base_qty = 500.0
WHERE id = 134260;
-- 门店：象子茶铺茶普洱创基店 物料：新黑糖粉 原明细 {"g":"","包":"1"} 旧值 1000.0 新值 500.0
UPDATE task_zone_material
SET input_qty = 950.0, base_qty = 950.0
WHERE id = 134620;
-- 门店：象子茶铺茶大理云龙店 物料：新黑糖粉 原明细 {"g":"","包":"1.9"} 旧值 1900.0 新值 950.0
UPDATE task_zone_material
SET input_qty = 500.0, base_qty = 500.0
WHERE id = 138220;
-- 门店：象子茶铺茶普洱悦城店 物料：新黑糖粉 原明细 {"g":"","包":"1"} 旧值 1000.0 新值 500.0
UPDATE task_zone_material
SET input_qty = 1500.0, base_qty = 1500.0
WHERE id = 140380;
-- 门店：象子茶铺茶保山五洲店 物料：新黑糖粉 原明细 {"g":"","包":"3"} 旧值 3000.0 新值 1500.0
UPDATE task_zone_material
SET input_qty = 2000.0, base_qty = 2000.0
WHERE id = 141460;
-- 门店：象子茶铺茶南涧店 物料：新黑糖粉 原明细 {"g":"","包":"4"} 旧值 4000.0 新值 2000.0
UPDATE task_zone_material
SET input_qty = 3000.0, base_qty = 3000.0
WHERE id = 146140;
-- 门店：象子茶铺茶富民彩玉国际店 物料：新黑糖粉 原明细 {"g":"","包":"6"} 旧值 6000.0 新值 3000.0
UPDATE task_zone_material
SET input_qty = 2500.0, base_qty = 2500.0
WHERE id = 146860;
-- 门店：象子茶铺茶镇康南伞店 物料：新黑糖粉 原明细 {"g":"","包":"5"} 旧值 5000.0 新值 2500.0
UPDATE task_zone_material
SET input_qty = 750.0, base_qty = 750.0
WHERE id = 149020;
-- 门店：象子茶铺茶贵州望谟店 物料：新黑糖粉 原明细 {"g":"","包":"1.5"} 旧值 1500.0 新值 750.0
UPDATE task_zone_material
SET input_qty = 1500.0, base_qty = 1500.0
WHERE id = 152080;
-- 门店：象子茶铺茶新平店 物料：新黑糖粉 原明细 {"g":"","包":"3"} 旧值 3000.0 新值 1500.0
UPDATE task_zone_material
SET input_qty = 1500.0, base_qty = 1500.0
WHERE id = 152260;
-- 门店：象子茶铺茶德宏盈江店 物料：新黑糖粉 原明细 {"g":"","包":"3"} 旧值 3000.0 新值 1500.0
UPDATE task_zone_material
SET input_qty = 2560.0, base_qty = 2560.0
WHERE id = 152980;
-- 门店：象子茶铺茶开远店 物料：新黑糖粉 原明细 {"g":"2500","包":"0.12"} 旧值 2620.0 新值 2560.0
UPDATE task_zone_material
SET input_qty = 250.0, base_qty = 250.0
WHERE id = 124589;
-- 门店：象子茶铺茶丽江祥和店 物料：奇亚籽（原材料） 原明细 {"g":"","包":"1"} 旧值 1000.0 新值 250.0
UPDATE task_zone_material
SET input_qty = 250.0, base_qty = 250.0
WHERE id = 125489;
-- 门店：象子茶铺茶勐海店 物料：奇亚籽（原材料） 原明细 {"g":"","包":"1"} 旧值 1000.0 新值 250.0
UPDATE task_zone_material
SET input_qty = 1750.0, base_qty = 1750.0
WHERE id = 130709;
-- 门店：象子茶铺茶玉溪华宁盘溪镇店 物料：奇亚籽（原材料） 原明细 {"g":"","包":"7"} 旧值 7000.0 新值 1750.0
UPDATE task_zone_material
SET input_qty = 1250.0, base_qty = 1250.0
WHERE id = 132329;
-- 门店：象子茶铺茶南屏街世纪广场店 物料：奇亚籽（原材料） 原明细 {"g":"","包":"5"} 旧值 5000.0 新值 1250.0
UPDATE task_zone_material
SET input_qty = 750.0, base_qty = 750.0
WHERE id = 132509;
-- 门店：象子茶铺茶漾濞店 物料：奇亚籽（原材料） 原明细 {"g":"","包":"3"} 旧值 3000.0 新值 750.0
UPDATE task_zone_material
SET input_qty = 1150.0, base_qty = 1150.0
WHERE id = 133229;
-- 门店：象子茶铺茶红河泸西店 物料：奇亚籽（原材料） 原明细 {"g":"150","包":"4"} 旧值 4150.0 新值 1150.0
UPDATE task_zone_material
SET input_qty = 250.0, base_qty = 250.0
WHERE id = 134309;
-- 门店：象子茶铺茶普洱创基店 物料：奇亚籽（原材料） 原明细 {"g":"","包":"1"} 旧值 1000.0 新值 250.0
UPDATE task_zone_material
SET input_qty = 650.0, base_qty = 650.0
WHERE id = 134669;
-- 门店：象子茶铺茶大理云龙店 物料：奇亚籽（原材料） 原明细 {"g":"","包":"2.6"} 旧值 2600.0 新值 650.0
UPDATE task_zone_material
SET input_qty = 1250.0, base_qty = 1250.0
WHERE id = 138269;
-- 门店：象子茶铺茶普洱悦城店 物料：奇亚籽（原材料） 原明细 {"g":"","包":"5","件":""} 旧值 5000.0 新值 1250.0
UPDATE task_zone_material
SET input_qty = 1000.0, base_qty = 1000.0
WHERE id = 140429;
-- 门店：象子茶铺茶保山五洲店 物料：奇亚籽（原材料） 原明细 {"g":"","包":"4"} 旧值 4000.0 新值 1000.0
UPDATE task_zone_material
SET input_qty = 500.0, base_qty = 500.0
WHERE id = 141509;
-- 门店：象子茶铺茶南涧店 物料：奇亚籽（原材料） 原明细 {"g":"","包":"2"} 旧值 2000.0 新值 500.0
UPDATE task_zone_material
SET input_qty = 750.0, base_qty = 750.0
WHERE id = 146909;
-- 门店：象子茶铺茶镇康南伞店 物料：奇亚籽（原材料） 原明细 {"g":"","包":"3"} 旧值 3000.0 新值 750.0
UPDATE task_zone_material
SET input_qty = 250.0, base_qty = 250.0
WHERE id = 148169;
-- 门店：象子茶铺茶东川店 物料：奇亚籽（原材料） 原明细 {"g":"","包":"1"} 旧值 1000.0 新值 250.0
UPDATE task_zone_material
SET input_qty = 500.0, base_qty = 500.0
WHERE id = 149069;
-- 门店：象子茶铺茶贵州望谟店 物料：奇亚籽（原材料） 原明细 {"g":"","包":"2"} 旧值 2000.0 新值 500.0
UPDATE task_zone_material
SET input_qty = 500.0, base_qty = 500.0
WHERE id = 150509;
-- 门店：象子茶铺茶楚雄彝海公园店 物料：奇亚籽（原材料） 原明细 {"g":"","包":"2"} 旧值 2000.0 新值 500.0
UPDATE task_zone_material
SET input_qty = 500.0, base_qty = 500.0
WHERE id = 152129;
-- 门店：象子茶铺茶新平店 物料：奇亚籽（原材料） 原明细 {"g":"","包":"2"} 旧值 2000.0 新值 500.0
UPDATE task_zone_material
SET input_qty = 720.0, base_qty = 720.0
WHERE id = 152309;
-- 门店：象子茶铺茶德宏盈江店 物料：奇亚籽（原材料） 原明细 {"g":"220","包":"2"} 旧值 2220.0 新值 720.0
UPDATE task_zone_material
SET input_qty = 775.0, base_qty = 775.0
WHERE id = 153029;
-- 门店：象子茶铺茶开远店 物料：奇亚籽（原材料） 原明细 {"g":"750","包":"0.1"} 旧值 850.0 新值 775.0


-- ============================================================
-- 第二批（2026-08-31 追加）：8月进行中任务，按当前链重算的其余真错误
-- 修正内容：WP0674 消毒片(旧 1瓶=100片→新 200片)、
--   WP0399/WP0396/WP0388 贴纸(旧 1捆=100张→新 200张)、
--   WP0510/WP0509 纯水机(无链 ratio=1，input_qty 滞后于 unit_inputs)、
--   WP0497 蓝毛巾(同上)
-- 已跳过：g 重量输入的四舍五入差(<0.5，链未变)；WP0601 重污清洁剂待确认
-- ============================================================
UPDATE task_zone_material
SET input_qty = 628.0, base_qty = 628.0
WHERE id = 124690;
-- task=695 象子茶铺茶丽江玉龙店 滇红贴纸 {"张":"28","捆":"3"} 328.0 → 628.0
UPDATE task_zone_material
SET input_qty = 400.0, base_qty = 400.0
WHERE id = 128470;
-- task=716 象子茶铺茶丽江永胜店 滇红贴纸 {"张":"","捆":"2"} 200.0 → 400.0
UPDATE task_zone_material
SET input_qty = 1300.0, base_qty = 1300.0
WHERE id = 146830;
-- task=818 象子茶铺茶镇康南伞店 滇红贴纸 {"张":"","捆":"6.5"} 650.0 → 1300.0
UPDATE task_zone_material
SET input_qty = 400.0, base_qty = 400.0
WHERE id = 148090;
-- task=825 象子茶铺茶东川店 滇红贴纸 {"张":"","捆":"2"} 200.0 → 400.0
UPDATE task_zone_material
SET input_qty = 1400.0, base_qty = 1400.0
WHERE id = 148990;
-- task=830 象子茶铺茶贵州望谟店 滇红贴纸 {"张":"","捆":"7"} 700.0 → 1400.0
UPDATE task_zone_material
SET input_qty = 656.0, base_qty = 656.0
WHERE id = 150430;
-- task=838 象子茶铺茶楚雄彝海公园店 滇红贴纸 {"张":"56","捆":"3"} 356.0 → 656.0
UPDATE task_zone_material
SET input_qty = 3100.0, base_qty = 3100.0
WHERE id = 150610;
-- task=839 象子茶铺茶楚雄彝人古镇店 滇红贴纸 {"张":"","捆":"15.5"} 2300.0 → 3100.0
UPDATE task_zone_material
SET input_qty = 600.0, base_qty = 600.0
WHERE id = 152950;
-- task=852 象子茶铺茶开远店 滇红贴纸 {"张":"","捆":"3"} 300.0 → 600.0
UPDATE task_zone_material
SET input_qty = 438.0, base_qty = 438.0
WHERE id = 124688;
-- task=695 象子茶铺茶丽江玉龙店 牛油果贴纸 {"张":"38","捆":"2"} 238.0 → 438.0
UPDATE task_zone_material
SET input_qty = 824.0, base_qty = 824.0
WHERE id = 128468;
-- task=716 象子茶铺茶丽江永胜店 牛油果贴纸 {"张":"24","捆":"4"} 424.0 → 824.0
UPDATE task_zone_material
SET input_qty = 200.0, base_qty = 200.0
WHERE id = 129548;
-- task=722 象子茶铺茶文山富宁店 牛油果贴纸 {"张":"","捆":"1"} 100.0 → 200.0
UPDATE task_zone_material
SET input_qty = 1500.0, base_qty = 1500.0
WHERE id = 146828;
-- task=818 象子茶铺茶镇康南伞店 牛油果贴纸 {"张":"","捆":"7.5"} 750.0 → 1500.0
UPDATE task_zone_material
SET input_qty = 1600.0, base_qty = 1600.0
WHERE id = 148988;
-- task=830 象子茶铺茶贵州望谟店 牛油果贴纸 {"张":"","捆":"8"} 800.0 → 1600.0
UPDATE task_zone_material
SET input_qty = 899.5455, base_qty = 899.5455
WHERE id = 150428;
-- task=838 象子茶铺茶楚雄彝海公园店 牛油果贴纸 {"张":"","捆":"4","g":"219"} 500.0 → 899.5455
UPDATE task_zone_material
SET input_qty = 280.0, base_qty = 280.0
WHERE id = 152948;
-- task=852 象子茶铺茶开远店 牛油果贴纸 {"张":"","捆":"1.4"} 140.0 → 280.0
UPDATE task_zone_material
SET input_qty = 400.0, base_qty = 400.0
WHERE id = 121807;
-- task=679 象子茶铺茶保山板桥镇店 普洱贴纸 {"张":"","捆":"2"} 200.0 → 400.0
UPDATE task_zone_material
SET input_qty = 848.0, base_qty = 848.0
WHERE id = 124687;
-- task=695 象子茶铺茶丽江玉龙店 普洱贴纸 {"张":"48","捆":"4"} 448.0 → 848.0
UPDATE task_zone_material
SET input_qty = 200.0, base_qty = 200.0
WHERE id = 128467;
-- task=716 象子茶铺茶丽江永胜店 普洱贴纸 {"张":"","捆":"1"} 100.0 → 200.0
UPDATE task_zone_material
SET input_qty = 438.0, base_qty = 438.0
WHERE id = 135127;
-- task=753 象子茶铺茶保山潞江坝镇 普洱贴纸 {"张":"38","捆":"2"} 238.0 → 438.0
UPDATE task_zone_material
SET input_qty = 1060.0, base_qty = 1060.0
WHERE id = 148987;
-- task=830 象子茶铺茶贵州望谟店 普洱贴纸 {"张":"","捆":"5.3"} 530.0 → 1060.0
UPDATE task_zone_material
SET input_qty = 800.0, base_qty = 800.0
WHERE id = 150427;
-- task=838 象子茶铺茶楚雄彝海公园店 普洱贴纸 {"张":"","捆":"4"} 400.0 → 800.0
UPDATE task_zone_material
SET input_qty = 1400.0, base_qty = 1400.0
WHERE id = 152947;
-- task=852 象子茶铺茶开远店 普洱贴纸 {"张":"","捆":"7"} 700.0 → 1400.0
UPDATE task_zone_material
SET input_qty = 4.0, base_qty = 4.0
WHERE id = 142076;
-- task=791 象子茶铺茶弥勒印象街店 蓝色毛巾(蓝色) {"条":"4"} 2.0 → 4.0
UPDATE task_zone_material
SET input_qty = 2.0, base_qty = 2.0
WHERE id = 140332;
-- task=782 象子茶铺茶保山五洲店 纯水机压缩活性炭 {"根":"2"} 1.0 → 2.0
UPDATE task_zone_material
SET input_qty = 1.0, base_qty = 1.0
WHERE id = 121791;
-- task=679 象子茶铺茶保山板桥镇店 纯水机GR0膜 {"根":"1"} 0.0 → 1.0
UPDATE task_zone_material
SET input_qty = 7.0, base_qty = 7.0
WHERE id = 125391;
-- task=699 象子茶铺茶勐海店 纯水机GR0膜 {"根":"7"} 1.0 → 7.0
UPDATE task_zone_material
SET input_qty = 200.0, base_qty = 200.0
WHERE id = 121765;
-- task=679 象子茶铺茶保山板桥镇店 复洁牌含氯消毒片 {"片":"","瓶":"1"} 100.0 → 200.0
UPDATE task_zone_material
SET input_qty = 200.0, base_qty = 200.0
WHERE id = 125365;
-- task=699 象子茶铺茶勐海店 复洁牌含氯消毒片 {"片":"","瓶":"1"} 100.0 → 200.0
UPDATE task_zone_material
SET input_qty = 280.0, base_qty = 280.0
WHERE id = 134545;
-- task=750 象子茶铺茶大理云龙店 复洁牌含氯消毒片 {"片":"","瓶":"1.4"} 140.0 → 280.0
UPDATE task_zone_material
SET input_qty = 312.0, base_qty = 312.0
WHERE id = 141385;
-- task=788 象子茶铺茶南涧店 复洁牌含氯消毒片 {"片":"112","瓶":"1"} 212.0 → 312.0
UPDATE task_zone_material
SET input_qty = 440.0, base_qty = 440.0
WHERE id = 146785;
-- task=818 象子茶铺茶镇康南伞店 复洁牌含氯消毒片 {"片":"","瓶":"2.2"} 220.0 → 440.0
UPDATE task_zone_material
SET input_qty = 200.0, base_qty = 200.0
WHERE id = 148045;
-- task=825 象子茶铺茶东川店 复洁牌含氯消毒片 {"片":"","瓶":"1"} 100.0 → 200.0
UPDATE task_zone_material
SET input_qty = 230.0, base_qty = 230.0
WHERE id = 148945;
-- task=830 象子茶铺茶贵州望谟店 复洁牌含氯消毒片 {"片":"30","瓶":"1"} 130.0 → 230.0
UPDATE task_zone_material
SET input_qty = 400.0, base_qty = 400.0
WHERE id = 149845;
-- task=835 象子茶铺茶墨江店 复洁牌含氯消毒片 {"片":"","瓶":"2"} 200.0 → 400.0
UPDATE task_zone_material
SET input_qty = 200.0, base_qty = 200.0
WHERE id = 150385;
-- task=838 象子茶铺茶楚雄彝海公园店 复洁牌含氯消毒片 {"片":"","瓶":"1"} 100.0 → 200.0
UPDATE task_zone_material
SET input_qty = 200.0, base_qty = 200.0
WHERE id = 150565;
-- task=839 象子茶铺茶楚雄彝人古镇店 复洁牌含氯消毒片 {"片":"","瓶":"1"} 100.0 → 200.0
UPDATE task_zone_material
SET input_qty = 180.0, base_qty = 180.0
WHERE id = 152905;
-- task=852 象子茶铺茶开远店 复洁牌含氯消毒片 {"片":"","瓶":"0.9","g":""} 90.0 → 180.0
