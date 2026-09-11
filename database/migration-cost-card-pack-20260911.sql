-- ==============================================================================================
-- 成本卡包材/耗材明细（来源：直营门店云边乌龙sop2026.8.29.xlsx「产品出品包装标准」）
-- 生成时间：2026-09-11 13:14:41   生成器：scripts/costcard/gen_pack_sql.py
--
-- 【本脚本做什么】
--   把成本卡里原来那 1 行整包「包材」（只有金额、无物料）替换成 SOP 拆出来的逐项包材/耗材行，
--   item_type='PACK'。PACK 行只计入成本卡成本，不产生物料消耗（消耗计算跳过 PACK）。
--
-- 【口径决定 · 已确认】
--   1) 98连体盖 → 95：pet 杯产品一律用 95PET杯+盖 WP0327（盘点单价 0.50，与 98 同价）
--      原因（需求方确认）：98PET杯+盖 已是迭代品，不能计入消耗；卡里一律指 95。
--      本脚本全部行都是 item_type='PACK'，而消耗计算对 PACK 直接跳过
--      （ConsumeCalcServiceImpl 判 'PACK'.equals(itemType) → continue），
--      故 95/98 都不会进入 store_material_consume_daily，已只读核验：
--        · store_material_consume_daily 无任何「杯/盖/吸管/膜」物料
--        · cost_card_item / cost_addon 均无引用 MN7KNEIU81
--   2) 封口膜 【已计入】（需求方确认「算的」）
--      SOP 写「封口+白色一体盖」，原整包金额没算封口膜
--      （原 0.735 = 牛皮纸杯0.44 + 白色一体盖0.22 + 吸管0.075）
--      → 本脚本使 26 张卡比原整包贵：22 张 +0.0500、纸杯白毫茉莉 3 张 +0.0300，
--        厚牛油果酸奶巴旦木(奶昔)/牛油果酸奶巴旦木 2 张 +0.0950
--      改口径：把 gen_pack_sql.py 的 INCLUDE_FILM 置 False 重跑即可
--   3) 胖胖杯：SOP 写「胖胖杯+胖胖盖」，需求方确认「胖胖盖和胖胖杯是一体的」→ 只记 1 行，
--      用「胖胖瓶 WP0651」代（原整包 1.28 = 胖胖瓶1.20 + 500粗0.075 也是这么算的）
--   4) 堂食/外卖二选一 → 取「外卖」（表头明示外卖统一口径；外卖为出货主渠道）
--      受影响的卡：牛油果酸奶巴旦木（吸管 500粗 vs 堂食 700粗）、山野滇红(加/去雪顶)
--      堂食口径的等价 SELECT 见文件末尾「堂食口径备选」，替换 3) 里对应行即可
--
-- 【未覆盖】SOP 表里没有的 17 个产品（季节/下架品），保持原整包「包材」行不动：
--     人参果杏子、卡美罗小黄姜、时令荔枝·冰茶、时令荔枝·冰酿、杏福人参、杏福人参果奶
--     杨枝甘露(凯特芒版)、杨枝甘露(新)双倍果肉、焦糖玫瑰滇红、牛油果杏福人参果、胭脂果与葡萄·冰茶、胭脂果与葡萄·酸奶冰浆
--     释迦果·芭乐冰茶、释迦果·芭乐奶酪、释迦果·芭乐酸奶昔、青芒南姜特调(特调)、青芒李子冰茶
--
--   5) 木勺子：需求方确认「木勺子是小料勺」→ 用「小料勺 WP0304」（0.19/个，
--      一级分类「包材成本/吸管类」，是随杯耗材；其余带「勺」的物料都在
--      「辅材成本/器具、设备类」，是工具，不计入包材）。
--      受影响：山野滇红(加雪顶)/山野滇红(去雪顶) 共 5 张卡，各 +0.1900
--
-- 【无剩余缺口】SOP 列出的包材/耗材均有对应物料主数据。
--
-- 【执行顺序】先跑 0) 的 dry-run 核对行数 → 1) 备份 → 2) 删除 → 3) 插入 → 4) 核对
-- 【执行前务必】python scripts/costcard/check_sql_syntax.py  把脚本逐条 PREPARE 解析一遍
-- 【回滚】见文件末尾「回滚」段
--
-- !! 2026-09-11 已在本脚本上出过一次生产事故，务必先读：
--   初版 2) DELETE 写成 `WHERE item_type='PACK' AND 第1组 OR 第2组 …`，
--   AND 优先级高于 OR ⇒ 第 2 组起不受 item_type 约束，
--   **删掉了 54 张卡的 297 行 RAW/SEMI 配方行**。
--   已修正为 `WHERE i.item_type='PACK' AND ( … )`；
--   被删的行由 database/restore-cost-card-raw-semi-20260911.sql 回放修复。
--   教训：多组 OR 条件必须整体加括号；交付前必须过 check_sql_syntax.py。
-- ==============================================================================================

SET NAMES utf8mb4;

-- ---------- 0) 试算：预期插入行数（只读，先跑这条） ----------
-- 预期 = 各卡 (杯+盖项数 + 1根吸管) 之和
-- SELECT COUNT(*) AS will_insert FROM (
-- SELECT '手炒黑糖珍珠' AS pname, 'least_ice,no_ice,normal_ice' AS tkeys, 'WP0327' AS qm, '' AS mid, '95PET杯+盖' AS mname, '个' AS unit, 1 AS qty, 0.5 AS price, '98连体盖(改95)' AS src
-- UNION ALL
-- SELECT '手炒黑糖珍珠' AS pname, 'least_ice,no_ice,normal_ice' AS tkeys, 'WP0319' AS qm, '' AS mid, 'PP500粗吸管' AS mname, '根' AS unit, 1 AS qty, 0.075 AS price, '吸管' AS src
-- UNION ALL
-- SELECT '手炒黑糖珍珠' AS pname, 'warm' AS tkeys, 'WP0302' AS qm, '' AS mid, '牛皮纸杯' AS mname, '个' AS unit, 1 AS qty, 0.44 AS price, '双层牛皮纸杯' AS src
-- UNION ALL
-- SELECT '手炒黑糖珍珠' AS pname, 'warm' AS tkeys, 'WP0310' AS qm, '' AS mid, '封口膜' AS mname, '张' AS unit, 1 AS qty, 0.05 AS price, '封口' AS src
-- UNION ALL
-- SELECT '手炒黑糖珍珠' AS pname, 'warm' AS tkeys, 'WP0675' AS qm, '' AS mid, '白色一体盖' AS mname, '个' AS unit, 1 AS qty, 0.22 AS price, '白色一体盖' AS src
-- UNION ALL
-- SELECT '手炒黑糖珍珠' AS pname, 'warm' AS tkeys, 'WP0319' AS qm, '' AS mid, 'PP500粗吸管' AS mname, '根' AS unit, 1 AS qty, 0.075 AS price, '吸管' AS src
-- UNION ALL
-- SELECT '鲜奶黑糖啵啵' AS pname, 'least_ice,normal_ice' AS tkeys, 'WP0327' AS qm, '' AS mid, '95PET杯+盖' AS mname, '个' AS unit, 1 AS qty, 0.5 AS price, '98连体盖(改95)' AS src
-- UNION ALL
-- SELECT '鲜奶黑糖啵啵' AS pname, 'least_ice,normal_ice' AS tkeys, 'WP0319' AS qm, '' AS mid, 'PP500粗吸管' AS mname, '根' AS unit, 1 AS qty, 0.075 AS price, '吸管' AS src
-- UNION ALL
-- SELECT '鲜奶黑糖啵啵' AS pname, 'room_temp,warm' AS tkeys, 'WP0302' AS qm, '' AS mid, '牛皮纸杯' AS mname, '个' AS unit, 1 AS qty, 0.44 AS price, '双层牛皮纸杯' AS src
-- UNION ALL
-- SELECT '鲜奶黑糖啵啵' AS pname, 'room_temp,warm' AS tkeys, 'WP0310' AS qm, '' AS mid, '封口膜' AS mname, '张' AS unit, 1 AS qty, 0.05 AS price, '封口' AS src
-- UNION ALL
-- SELECT '鲜奶黑糖啵啵' AS pname, 'room_temp,warm' AS tkeys, 'WP0675' AS qm, '' AS mid, '白色一体盖' AS mname, '个' AS unit, 1 AS qty, 0.22 AS price, '白色一体盖' AS src
-- UNION ALL
-- SELECT '鲜奶黑糖啵啵' AS pname, 'room_temp,warm' AS tkeys, 'WP0319' AS qm, '' AS mid, 'PP500粗吸管' AS mname, '根' AS unit, 1 AS qty, 0.075 AS price, '吸管' AS src
-- UNION ALL
-- SELECT '手作米布鲜奶茶' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0302' AS qm, '' AS mid, '牛皮纸杯' AS mname, '个' AS unit, 1 AS qty, 0.44 AS price, '双层牛皮纸杯' AS src
-- UNION ALL
-- SELECT '手作米布鲜奶茶' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0310' AS qm, '' AS mid, '封口膜' AS mname, '张' AS unit, 1 AS qty, 0.05 AS price, '封口' AS src
-- UNION ALL
-- SELECT '手作米布鲜奶茶' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0675' AS qm, '' AS mid, '白色一体盖' AS mname, '个' AS unit, 1 AS qty, 0.22 AS price, '白色一体盖' AS src
-- UNION ALL
-- SELECT '手作米布鲜奶茶' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0319' AS qm, '' AS mid, 'PP500粗吸管' AS mname, '根' AS unit, 1 AS qty, 0.075 AS price, '吸管' AS src
-- UNION ALL
-- SELECT '酸奶紫米露' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0302' AS qm, '' AS mid, '牛皮纸杯' AS mname, '个' AS unit, 1 AS qty, 0.44 AS price, '双层牛皮纸杯' AS src
-- UNION ALL
-- SELECT '酸奶紫米露' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0310' AS qm, '' AS mid, '封口膜' AS mname, '张' AS unit, 1 AS qty, 0.05 AS price, '封口' AS src
-- UNION ALL
-- SELECT '酸奶紫米露' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0675' AS qm, '' AS mid, '白色一体盖' AS mname, '个' AS unit, 1 AS qty, 0.22 AS price, '白色一体盖' AS src
-- UNION ALL
-- SELECT '酸奶紫米露' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0319' AS qm, '' AS mid, 'PP500粗吸管' AS mname, '根' AS unit, 1 AS qty, 0.075 AS price, '吸管' AS src
-- UNION ALL
-- SELECT '纸杯白毫茉莉' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0302' AS qm, '' AS mid, '牛皮纸杯' AS mname, '个' AS unit, 1 AS qty, 0.44 AS price, '双层牛皮纸杯' AS src
-- UNION ALL
-- SELECT '纸杯白毫茉莉' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0310' AS qm, '' AS mid, '封口膜' AS mname, '张' AS unit, 1 AS qty, 0.05 AS price, '封口' AS src
-- UNION ALL
-- SELECT '纸杯白毫茉莉' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0675' AS qm, '' AS mid, '白色一体盖' AS mname, '个' AS unit, 1 AS qty, 0.22 AS price, '白色一体盖' AS src
-- UNION ALL
-- SELECT '纸杯白毫茉莉' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0869' AS qm, '' AS mid, 'PP700细吸管（新）' AS mname, '根' AS unit, 1 AS qty, 0.05 AS price, '吸管' AS src
-- UNION ALL
-- SELECT '树番茄舂柠檬' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0327' AS qm, '' AS mid, '95PET杯+盖' AS mname, '个' AS unit, 1 AS qty, 0.5 AS price, '98连体盖(改95)' AS src
-- UNION ALL
-- SELECT '树番茄舂柠檬' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0869' AS qm, '' AS mid, 'PP700细吸管（新）' AS mname, '根' AS unit, 1 AS qty, 0.05 AS price, '吸管' AS src
-- UNION ALL
-- SELECT '树番茄话梅·德宏' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0327' AS qm, '' AS mid, '95PET杯+盖' AS mname, '个' AS unit, 1 AS qty, 0.5 AS price, '98连体盖(改95)' AS src
-- UNION ALL
-- SELECT '树番茄话梅·德宏' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0869' AS qm, '' AS mid, 'PP700细吸管（新）' AS mname, '根' AS unit, 1 AS qty, 0.05 AS price, '吸管' AS src
-- UNION ALL
-- SELECT '茉莉舂柠檬' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0327' AS qm, '' AS mid, '95PET杯+盖' AS mname, '个' AS unit, 1 AS qty, 0.5 AS price, '98连体盖(改95)' AS src
-- UNION ALL
-- SELECT '茉莉舂柠檬' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0869' AS qm, '' AS mid, 'PP700细吸管（新）' AS mname, '根' AS unit, 1 AS qty, 0.05 AS price, '吸管' AS src
-- UNION ALL
-- SELECT '滇橄榄舂柠檬' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0327' AS qm, '' AS mid, '95PET杯+盖' AS mname, '个' AS unit, 1 AS qty, 0.5 AS price, '98连体盖(改95)' AS src
-- UNION ALL
-- SELECT '滇橄榄舂柠檬' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0869' AS qm, '' AS mid, 'PP700细吸管（新）' AS mname, '根' AS unit, 1 AS qty, 0.05 AS price, '吸管' AS src
-- UNION ALL
-- SELECT '滇红舂柠檬' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0327' AS qm, '' AS mid, '95PET杯+盖' AS mname, '个' AS unit, 1 AS qty, 0.5 AS price, '98连体盖(改95)' AS src
-- UNION ALL
-- SELECT '滇红舂柠檬' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0869' AS qm, '' AS mid, 'PP700细吸管（新）' AS mname, '根' AS unit, 1 AS qty, 0.05 AS price, '吸管' AS src
-- UNION ALL
-- SELECT '青柚滇橄榄' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0327' AS qm, '' AS mid, '95PET杯+盖' AS mname, '个' AS unit, 1 AS qty, 0.5 AS price, '98连体盖(改95)' AS src
-- UNION ALL
-- SELECT '青柚滇橄榄' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0869' AS qm, '' AS mid, 'PP700细吸管（新）' AS mname, '根' AS unit, 1 AS qty, 0.05 AS price, '吸管' AS src
-- UNION ALL
-- SELECT '牛油果酸奶巴旦木' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0314' AS qm, '' AS mid, '注塑杯(500)' AS mname, '个' AS unit, 1 AS qty, 0.44 AS price, '500注塑杯' AS src
-- UNION ALL
-- SELECT '牛油果酸奶巴旦木' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0310' AS qm, '' AS mid, '封口膜' AS mname, '张' AS unit, 1 AS qty, 0.05 AS price, '封口' AS src
-- UNION ALL
-- SELECT '牛油果酸奶巴旦木' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0675' AS qm, '' AS mid, '白色一体盖' AS mname, '个' AS unit, 1 AS qty, 0.22 AS price, '白色一体盖' AS src
-- UNION ALL
-- SELECT '牛油果酸奶巴旦木' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0319' AS qm, '' AS mid, 'PP500粗吸管' AS mname, '根' AS unit, 1 AS qty, 0.075 AS price, '吸管' AS src
-- UNION ALL
-- SELECT '茉莉奶白' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0302' AS qm, '' AS mid, '牛皮纸杯' AS mname, '个' AS unit, 1 AS qty, 0.44 AS price, '双层牛皮纸杯' AS src
-- UNION ALL
-- SELECT '茉莉奶白' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0310' AS qm, '' AS mid, '封口膜' AS mname, '张' AS unit, 1 AS qty, 0.05 AS price, '封口' AS src
-- UNION ALL
-- SELECT '茉莉奶白' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0675' AS qm, '' AS mid, '白色一体盖' AS mname, '个' AS unit, 1 AS qty, 0.22 AS price, '白色一体盖' AS src
-- UNION ALL
-- SELECT '茉莉奶白' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0768' AS qm, '' AS mid, '（新）PLA三孔鲜奶吸管' AS mname, '根' AS unit, 1 AS qty, 0.075 AS price, '吸管' AS src
-- UNION ALL
-- SELECT '新10月黄油红糖(等普洱)' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0302' AS qm, '' AS mid, '牛皮纸杯' AS mname, '个' AS unit, 1 AS qty, 0.44 AS price, '双层牛皮纸杯' AS src
-- UNION ALL
-- SELECT '新10月黄油红糖(等普洱)' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0310' AS qm, '' AS mid, '封口膜' AS mname, '张' AS unit, 1 AS qty, 0.05 AS price, '封口' AS src
-- UNION ALL
-- SELECT '新10月黄油红糖(等普洱)' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0675' AS qm, '' AS mid, '白色一体盖' AS mname, '个' AS unit, 1 AS qty, 0.22 AS price, '白色一体盖' AS src
-- UNION ALL
-- SELECT '新10月黄油红糖(等普洱)' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0768' AS qm, '' AS mid, '（新）PLA三孔鲜奶吸管' AS mname, '根' AS unit, 1 AS qty, 0.075 AS price, '吸管' AS src
-- UNION ALL
-- SELECT '山野滇红(加雪顶)' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0302' AS qm, '' AS mid, '牛皮纸杯' AS mname, '个' AS unit, 1 AS qty, 0.44 AS price, '双层牛皮纸杯' AS src
-- UNION ALL
-- SELECT '山野滇红(加雪顶)' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0310' AS qm, '' AS mid, '封口膜' AS mname, '张' AS unit, 1 AS qty, 0.05 AS price, '封口' AS src
-- UNION ALL
-- SELECT '山野滇红(加雪顶)' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0675' AS qm, '' AS mid, '白色一体盖' AS mname, '个' AS unit, 1 AS qty, 0.22 AS price, '白色一体盖' AS src
-- UNION ALL
-- SELECT '山野滇红(加雪顶)' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0768' AS qm, '' AS mid, '（新）PLA三孔鲜奶吸管' AS mname, '根' AS unit, 1 AS qty, 0.075 AS price, '吸管' AS src
-- UNION ALL
-- SELECT '山野滇红(加雪顶)' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0304' AS qm, '' AS mid, '小料勺' AS mname, '个' AS unit, 1 AS qty, 0.19 AS price, '耗材' AS src
-- UNION ALL
-- SELECT '山野滇红(去雪顶)' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0302' AS qm, '' AS mid, '牛皮纸杯' AS mname, '个' AS unit, 1 AS qty, 0.44 AS price, '双层牛皮纸杯' AS src
-- UNION ALL
-- SELECT '山野滇红(去雪顶)' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0310' AS qm, '' AS mid, '封口膜' AS mname, '张' AS unit, 1 AS qty, 0.05 AS price, '封口' AS src
-- UNION ALL
-- SELECT '山野滇红(去雪顶)' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0675' AS qm, '' AS mid, '白色一体盖' AS mname, '个' AS unit, 1 AS qty, 0.22 AS price, '白色一体盖' AS src
-- UNION ALL
-- SELECT '山野滇红(去雪顶)' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0768' AS qm, '' AS mid, '（新）PLA三孔鲜奶吸管' AS mname, '根' AS unit, 1 AS qty, 0.075 AS price, '吸管' AS src
-- UNION ALL
-- SELECT '山野滇红(去雪顶)' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0304' AS qm, '' AS mid, '小料勺' AS mname, '个' AS unit, 1 AS qty, 0.19 AS price, '耗材' AS src
-- UNION ALL
-- SELECT '杨枝甘露双倍果肉' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0314' AS qm, '' AS mid, '注塑杯(500)' AS mname, '个' AS unit, 1 AS qty, 0.44 AS price, '500注塑杯' AS src
-- UNION ALL
-- SELECT '杨枝甘露双倍果肉' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0310' AS qm, '' AS mid, '封口膜' AS mname, '张' AS unit, 1 AS qty, 0.05 AS price, '封口' AS src
-- UNION ALL
-- SELECT '杨枝甘露双倍果肉' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0675' AS qm, '' AS mid, '白色一体盖' AS mname, '个' AS unit, 1 AS qty, 0.22 AS price, '白色一体盖' AS src
-- UNION ALL
-- SELECT '杨枝甘露双倍果肉' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0319' AS qm, '' AS mid, 'PP500粗吸管' AS mname, '根' AS unit, 1 AS qty, 0.075 AS price, '吸管' AS src
-- UNION ALL
-- SELECT '牛油果燕麦椰椰' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0327' AS qm, '' AS mid, '95PET杯+盖' AS mname, '个' AS unit, 1 AS qty, 0.5 AS price, '98连体盖(改95)' AS src
-- UNION ALL
-- SELECT '牛油果燕麦椰椰' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0319' AS qm, '' AS mid, 'PP500粗吸管' AS mname, '根' AS unit, 1 AS qty, 0.075 AS price, '吸管' AS src
-- UNION ALL
-- SELECT '牛油果碧根果' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0651' AS qm, '' AS mid, '胖胖瓶' AS mname, '个' AS unit, 1 AS qty, 1.2 AS price, '胖胖杯(含盖一体)' AS src
-- UNION ALL
-- SELECT '牛油果碧根果' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0319' AS qm, '' AS mid, 'PP500粗吸管' AS mname, '根' AS unit, 1 AS qty, 0.075 AS price, '吸管' AS src
-- UNION ALL
-- SELECT '厚牛油果酸奶巴旦木(奶昔)' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0314' AS qm, '' AS mid, '注塑杯(500)' AS mname, '个' AS unit, 1 AS qty, 0.44 AS price, '500注塑杯' AS src
-- UNION ALL
-- SELECT '厚牛油果酸奶巴旦木(奶昔)' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0310' AS qm, '' AS mid, '封口膜' AS mname, '张' AS unit, 1 AS qty, 0.05 AS price, '封口' AS src
-- UNION ALL
-- SELECT '厚牛油果酸奶巴旦木(奶昔)' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0675' AS qm, '' AS mid, '白色一体盖' AS mname, '个' AS unit, 1 AS qty, 0.22 AS price, '白色一体盖' AS src
-- UNION ALL
-- SELECT '厚牛油果酸奶巴旦木(奶昔)' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0319' AS qm, '' AS mid, 'PP500粗吸管' AS mname, '根' AS unit, 1 AS qty, 0.075 AS price, '吸管' AS src
-- UNION ALL
-- SELECT '双倍牛油果蔓越莓' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0651' AS qm, '' AS mid, '胖胖瓶' AS mname, '个' AS unit, 1 AS qty, 1.2 AS price, '胖胖杯(含盖一体)' AS src
-- UNION ALL
-- SELECT '双倍牛油果蔓越莓' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0319' AS qm, '' AS mid, 'PP500粗吸管' AS mname, '根' AS unit, 1 AS qty, 0.075 AS price, '吸管' AS src
-- UNION ALL
-- SELECT '酸角话梅·版纳' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0327' AS qm, '' AS mid, '95PET杯+盖' AS mname, '个' AS unit, 1 AS qty, 0.5 AS price, '98连体盖(改95)' AS src
-- UNION ALL
-- SELECT '酸角话梅·版纳' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0869' AS qm, '' AS mid, 'PP700细吸管（新）' AS mname, '根' AS unit, 1 AS qty, 0.05 AS price, '吸管' AS src
-- UNION ALL
-- SELECT '清甜芭乐冰茶' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0327' AS qm, '' AS mid, '95PET杯+盖' AS mname, '个' AS unit, 1 AS qty, 0.5 AS price, '98连体盖(改95)' AS src
-- UNION ALL
-- SELECT '清甜芭乐冰茶' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0869' AS qm, '' AS mid, 'PP700细吸管（新）' AS mname, '根' AS unit, 1 AS qty, 0.05 AS price, '吸管' AS src
-- UNION ALL
-- SELECT '清甜芭乐果奶' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0327' AS qm, '' AS mid, '95PET杯+盖' AS mname, '个' AS unit, 1 AS qty, 0.5 AS price, '98连体盖(改95)' AS src
-- UNION ALL
-- SELECT '清甜芭乐果奶' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0768' AS qm, '' AS mid, '（新）PLA三孔鲜奶吸管' AS mname, '根' AS unit, 1 AS qty, 0.075 AS price, '吸管' AS src
-- UNION ALL
-- SELECT '玫瑰奶白酒' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0327' AS qm, '' AS mid, '95PET杯+盖' AS mname, '个' AS unit, 1 AS qty, 0.5 AS price, '98连体盖(改95)' AS src
-- UNION ALL
-- SELECT '玫瑰奶白酒' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0319' AS qm, '' AS mid, 'PP500粗吸管' AS mname, '根' AS unit, 1 AS qty, 0.075 AS price, '吸管' AS src
-- UNION ALL
-- SELECT '玫瑰奶白酒-热(无糖版)' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0302' AS qm, '' AS mid, '牛皮纸杯' AS mname, '个' AS unit, 1 AS qty, 0.44 AS price, '双层牛皮纸杯' AS src
-- UNION ALL
-- SELECT '玫瑰奶白酒-热(无糖版)' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0310' AS qm, '' AS mid, '封口膜' AS mname, '张' AS unit, 1 AS qty, 0.05 AS price, '封口' AS src
-- UNION ALL
-- SELECT '玫瑰奶白酒-热(无糖版)' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0675' AS qm, '' AS mid, '白色一体盖' AS mname, '个' AS unit, 1 AS qty, 0.22 AS price, '白色一体盖' AS src
-- UNION ALL
-- SELECT '玫瑰奶白酒-热(无糖版)' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0319' AS qm, '' AS mid, 'PP500粗吸管' AS mname, '根' AS unit, 1 AS qty, 0.075 AS price, '吸管' AS src
-- UNION ALL
-- SELECT '青柚与葡萄柚' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0327' AS qm, '' AS mid, '95PET杯+盖' AS mname, '个' AS unit, 1 AS qty, 0.5 AS price, '98连体盖(改95)' AS src
-- UNION ALL
-- SELECT '青柚与葡萄柚' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0319' AS qm, '' AS mid, 'PP500粗吸管' AS mname, '根' AS unit, 1 AS qty, 0.075 AS price, '吸管' AS src
-- UNION ALL
-- SELECT '青柚乌龙冰茶' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0327' AS qm, '' AS mid, '95PET杯+盖' AS mname, '个' AS unit, 1 AS qty, 0.5 AS price, '98连体盖(改95)' AS src
-- UNION ALL
-- SELECT '青柚乌龙冰茶' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0768' AS qm, '' AS mid, '（新）PLA三孔鲜奶吸管' AS mname, '根' AS unit, 1 AS qty, 0.075 AS price, '吸管' AS src
-- ) s
-- JOIN cost_product p ON p.product_name = s.pname
-- JOIN cost_card    k ON k.product_id = p.id AND FIND_IN_SET(k.temp_key, s.tkeys) > 0
-- JOIN material     m ON m.del_flag = 0
--                        AND ((s.qm <> '' AND m.qm_code = s.qm) OR (s.mid <> '' AND m.material_id = s.mid));

-- ---------- 1) 备份将被替换的整包行（回滚用） ----------
DROP TABLE IF EXISTS cost_card_item_pack_bak_20260911;
CREATE TABLE cost_card_item_pack_bak_20260911 AS
SELECT i.* FROM cost_card_item i
JOIN cost_card k    ON k.id = i.card_id
JOIN cost_product p ON p.id = k.product_id
WHERE i.item_type = 'PACK' AND (
  p.product_name IN ('厚牛油果酸奶巴旦木(奶昔)', '双倍牛油果蔓越莓', '山野滇红(加雪顶)', '山野滇红(去雪顶)')
   OR p.product_name IN ('手作米布鲜奶茶', '手炒黑糖珍珠', '新10月黄油红糖(等普洱)', '杨枝甘露双倍果肉')
   OR p.product_name IN ('树番茄舂柠檬', '树番茄话梅·德宏', '清甜芭乐冰茶', '清甜芭乐果奶')
   OR p.product_name IN ('滇橄榄舂柠檬', '滇红舂柠檬', '牛油果燕麦椰椰', '牛油果碧根果')
   OR p.product_name IN ('牛油果酸奶巴旦木', '玫瑰奶白酒', '玫瑰奶白酒-热(无糖版)', '纸杯白毫茉莉')
   OR p.product_name IN ('茉莉奶白', '茉莉舂柠檬', '酸奶紫米露', '酸角话梅·版纳')
   OR p.product_name IN ('青柚与葡萄柚', '青柚乌龙冰茶', '青柚滇橄榄', '鲜奶黑糖啵啵')
);

-- ---------- 2) 删除将重做的整包「包材」行（仅限下列产品，其余产品不动） ----------
DELETE i FROM cost_card_item i
JOIN cost_card k    ON k.id = i.card_id
JOIN cost_product p ON p.id = k.product_id
-- 必须整体加括号：AND 优先级高于 OR，若写成 AND 第1组 OR 第2组…
-- 则第 2 组起的 OR 分支不受 item_type='PACK' 约束，会连 RAW/SEMI 配方行一起删掉
WHERE i.item_type = 'PACK' AND (
  p.product_name IN ('厚牛油果酸奶巴旦木(奶昔)', '双倍牛油果蔓越莓', '山野滇红(加雪顶)', '山野滇红(去雪顶)')
   OR p.product_name IN ('手作米布鲜奶茶', '手炒黑糖珍珠', '新10月黄油红糖(等普洱)', '杨枝甘露双倍果肉')
   OR p.product_name IN ('树番茄舂柠檬', '树番茄话梅·德宏', '清甜芭乐冰茶', '清甜芭乐果奶')
   OR p.product_name IN ('滇橄榄舂柠檬', '滇红舂柠檬', '牛油果燕麦椰椰', '牛油果碧根果')
   OR p.product_name IN ('牛油果酸奶巴旦木', '玫瑰奶白酒', '玫瑰奶白酒-热(无糖版)', '纸杯白毫茉莉')
   OR p.product_name IN ('茉莉奶白', '茉莉舂柠檬', '酸奶紫米露', '酸角话梅·版纳')
   OR p.product_name IN ('青柚与葡萄柚', '青柚乌龙冰茶', '青柚滇橄榄', '鲜奶黑糖啵啵')
);

-- ---------- 3) 插入逐项包材/耗材行 ----------
-- source 列保留 SOP 出处（双层牛皮纸杯/封口/白色一体盖/98连体盖(改95)/吸管/胖胖杯）
-- 单价为成本卡快照（= 物料盘点单价），可用 4.3 的 SQL 核对漂移
-- 注：不写 loss_rate（NOT NULL DEFAULT 0，显式传 NULL 会报 1048）；
--     created_at/updated_at 走 DEFAULT CURRENT_TIMESTAMP
INSERT INTO cost_card_item
  (card_id, item_type, excel_item_name, material_name, material_id, qm_code,
   quantity, unit, unit_price, amount, sort_no)
SELECT k.id, 'PACK', CONCAT(s.mname, '｜', s.src), s.mname, m.material_id, m.qm_code,
       s.qty, s.unit, s.price, ROUND(s.qty * s.price, 4),
       COALESCE((SELECT MAX(x.sort_no) FROM cost_card_item x
                 WHERE x.card_id = k.id AND x.item_type <> 'PACK'), 0) + s.ord
FROM (
  SELECT '手炒黑糖珍珠' AS pname, 'least_ice,no_ice,normal_ice' AS tkeys, 'WP0327' AS qm, '' AS mid, '95PET杯+盖' AS mname, '个' AS unit, 1 AS qty, 0.5 AS price, 1 AS ord, '98连体盖(改95)' AS src
  UNION ALL
  SELECT '手炒黑糖珍珠' AS pname, 'least_ice,no_ice,normal_ice' AS tkeys, 'WP0319' AS qm, '' AS mid, 'PP500粗吸管' AS mname, '根' AS unit, 1 AS qty, 0.075 AS price, 2 AS ord, '吸管' AS src
  UNION ALL
  SELECT '手炒黑糖珍珠' AS pname, 'warm' AS tkeys, 'WP0302' AS qm, '' AS mid, '牛皮纸杯' AS mname, '个' AS unit, 1 AS qty, 0.44 AS price, 1 AS ord, '双层牛皮纸杯' AS src
  UNION ALL
  SELECT '手炒黑糖珍珠' AS pname, 'warm' AS tkeys, 'WP0310' AS qm, '' AS mid, '封口膜' AS mname, '张' AS unit, 1 AS qty, 0.05 AS price, 2 AS ord, '封口' AS src
  UNION ALL
  SELECT '手炒黑糖珍珠' AS pname, 'warm' AS tkeys, 'WP0675' AS qm, '' AS mid, '白色一体盖' AS mname, '个' AS unit, 1 AS qty, 0.22 AS price, 3 AS ord, '白色一体盖' AS src
  UNION ALL
  SELECT '手炒黑糖珍珠' AS pname, 'warm' AS tkeys, 'WP0319' AS qm, '' AS mid, 'PP500粗吸管' AS mname, '根' AS unit, 1 AS qty, 0.075 AS price, 4 AS ord, '吸管' AS src
  UNION ALL
  SELECT '鲜奶黑糖啵啵' AS pname, 'least_ice,normal_ice' AS tkeys, 'WP0327' AS qm, '' AS mid, '95PET杯+盖' AS mname, '个' AS unit, 1 AS qty, 0.5 AS price, 1 AS ord, '98连体盖(改95)' AS src
  UNION ALL
  SELECT '鲜奶黑糖啵啵' AS pname, 'least_ice,normal_ice' AS tkeys, 'WP0319' AS qm, '' AS mid, 'PP500粗吸管' AS mname, '根' AS unit, 1 AS qty, 0.075 AS price, 2 AS ord, '吸管' AS src
  UNION ALL
  SELECT '鲜奶黑糖啵啵' AS pname, 'room_temp,warm' AS tkeys, 'WP0302' AS qm, '' AS mid, '牛皮纸杯' AS mname, '个' AS unit, 1 AS qty, 0.44 AS price, 1 AS ord, '双层牛皮纸杯' AS src
  UNION ALL
  SELECT '鲜奶黑糖啵啵' AS pname, 'room_temp,warm' AS tkeys, 'WP0310' AS qm, '' AS mid, '封口膜' AS mname, '张' AS unit, 1 AS qty, 0.05 AS price, 2 AS ord, '封口' AS src
  UNION ALL
  SELECT '鲜奶黑糖啵啵' AS pname, 'room_temp,warm' AS tkeys, 'WP0675' AS qm, '' AS mid, '白色一体盖' AS mname, '个' AS unit, 1 AS qty, 0.22 AS price, 3 AS ord, '白色一体盖' AS src
  UNION ALL
  SELECT '鲜奶黑糖啵啵' AS pname, 'room_temp,warm' AS tkeys, 'WP0319' AS qm, '' AS mid, 'PP500粗吸管' AS mname, '根' AS unit, 1 AS qty, 0.075 AS price, 4 AS ord, '吸管' AS src
  UNION ALL
  SELECT '手作米布鲜奶茶' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0302' AS qm, '' AS mid, '牛皮纸杯' AS mname, '个' AS unit, 1 AS qty, 0.44 AS price, 1 AS ord, '双层牛皮纸杯' AS src
  UNION ALL
  SELECT '手作米布鲜奶茶' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0310' AS qm, '' AS mid, '封口膜' AS mname, '张' AS unit, 1 AS qty, 0.05 AS price, 2 AS ord, '封口' AS src
  UNION ALL
  SELECT '手作米布鲜奶茶' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0675' AS qm, '' AS mid, '白色一体盖' AS mname, '个' AS unit, 1 AS qty, 0.22 AS price, 3 AS ord, '白色一体盖' AS src
  UNION ALL
  SELECT '手作米布鲜奶茶' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0319' AS qm, '' AS mid, 'PP500粗吸管' AS mname, '根' AS unit, 1 AS qty, 0.075 AS price, 4 AS ord, '吸管' AS src
  UNION ALL
  SELECT '酸奶紫米露' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0302' AS qm, '' AS mid, '牛皮纸杯' AS mname, '个' AS unit, 1 AS qty, 0.44 AS price, 1 AS ord, '双层牛皮纸杯' AS src
  UNION ALL
  SELECT '酸奶紫米露' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0310' AS qm, '' AS mid, '封口膜' AS mname, '张' AS unit, 1 AS qty, 0.05 AS price, 2 AS ord, '封口' AS src
  UNION ALL
  SELECT '酸奶紫米露' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0675' AS qm, '' AS mid, '白色一体盖' AS mname, '个' AS unit, 1 AS qty, 0.22 AS price, 3 AS ord, '白色一体盖' AS src
  UNION ALL
  SELECT '酸奶紫米露' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0319' AS qm, '' AS mid, 'PP500粗吸管' AS mname, '根' AS unit, 1 AS qty, 0.075 AS price, 4 AS ord, '吸管' AS src
  UNION ALL
  SELECT '纸杯白毫茉莉' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0302' AS qm, '' AS mid, '牛皮纸杯' AS mname, '个' AS unit, 1 AS qty, 0.44 AS price, 1 AS ord, '双层牛皮纸杯' AS src
  UNION ALL
  SELECT '纸杯白毫茉莉' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0310' AS qm, '' AS mid, '封口膜' AS mname, '张' AS unit, 1 AS qty, 0.05 AS price, 2 AS ord, '封口' AS src
  UNION ALL
  SELECT '纸杯白毫茉莉' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0675' AS qm, '' AS mid, '白色一体盖' AS mname, '个' AS unit, 1 AS qty, 0.22 AS price, 3 AS ord, '白色一体盖' AS src
  UNION ALL
  SELECT '纸杯白毫茉莉' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0869' AS qm, '' AS mid, 'PP700细吸管（新）' AS mname, '根' AS unit, 1 AS qty, 0.05 AS price, 4 AS ord, '吸管' AS src
  UNION ALL
  SELECT '树番茄舂柠檬' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0327' AS qm, '' AS mid, '95PET杯+盖' AS mname, '个' AS unit, 1 AS qty, 0.5 AS price, 1 AS ord, '98连体盖(改95)' AS src
  UNION ALL
  SELECT '树番茄舂柠檬' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0869' AS qm, '' AS mid, 'PP700细吸管（新）' AS mname, '根' AS unit, 1 AS qty, 0.05 AS price, 2 AS ord, '吸管' AS src
  UNION ALL
  SELECT '树番茄话梅·德宏' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0327' AS qm, '' AS mid, '95PET杯+盖' AS mname, '个' AS unit, 1 AS qty, 0.5 AS price, 1 AS ord, '98连体盖(改95)' AS src
  UNION ALL
  SELECT '树番茄话梅·德宏' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0869' AS qm, '' AS mid, 'PP700细吸管（新）' AS mname, '根' AS unit, 1 AS qty, 0.05 AS price, 2 AS ord, '吸管' AS src
  UNION ALL
  SELECT '茉莉舂柠檬' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0327' AS qm, '' AS mid, '95PET杯+盖' AS mname, '个' AS unit, 1 AS qty, 0.5 AS price, 1 AS ord, '98连体盖(改95)' AS src
  UNION ALL
  SELECT '茉莉舂柠檬' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0869' AS qm, '' AS mid, 'PP700细吸管（新）' AS mname, '根' AS unit, 1 AS qty, 0.05 AS price, 2 AS ord, '吸管' AS src
  UNION ALL
  SELECT '滇橄榄舂柠檬' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0327' AS qm, '' AS mid, '95PET杯+盖' AS mname, '个' AS unit, 1 AS qty, 0.5 AS price, 1 AS ord, '98连体盖(改95)' AS src
  UNION ALL
  SELECT '滇橄榄舂柠檬' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0869' AS qm, '' AS mid, 'PP700细吸管（新）' AS mname, '根' AS unit, 1 AS qty, 0.05 AS price, 2 AS ord, '吸管' AS src
  UNION ALL
  SELECT '滇红舂柠檬' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0327' AS qm, '' AS mid, '95PET杯+盖' AS mname, '个' AS unit, 1 AS qty, 0.5 AS price, 1 AS ord, '98连体盖(改95)' AS src
  UNION ALL
  SELECT '滇红舂柠檬' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0869' AS qm, '' AS mid, 'PP700细吸管（新）' AS mname, '根' AS unit, 1 AS qty, 0.05 AS price, 2 AS ord, '吸管' AS src
  UNION ALL
  SELECT '青柚滇橄榄' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0327' AS qm, '' AS mid, '95PET杯+盖' AS mname, '个' AS unit, 1 AS qty, 0.5 AS price, 1 AS ord, '98连体盖(改95)' AS src
  UNION ALL
  SELECT '青柚滇橄榄' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0869' AS qm, '' AS mid, 'PP700细吸管（新）' AS mname, '根' AS unit, 1 AS qty, 0.05 AS price, 2 AS ord, '吸管' AS src
  UNION ALL
  SELECT '牛油果酸奶巴旦木' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0314' AS qm, '' AS mid, '注塑杯(500)' AS mname, '个' AS unit, 1 AS qty, 0.44 AS price, 1 AS ord, '500注塑杯' AS src
  UNION ALL
  SELECT '牛油果酸奶巴旦木' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0310' AS qm, '' AS mid, '封口膜' AS mname, '张' AS unit, 1 AS qty, 0.05 AS price, 2 AS ord, '封口' AS src
  UNION ALL
  SELECT '牛油果酸奶巴旦木' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0675' AS qm, '' AS mid, '白色一体盖' AS mname, '个' AS unit, 1 AS qty, 0.22 AS price, 3 AS ord, '白色一体盖' AS src
  UNION ALL
  SELECT '牛油果酸奶巴旦木' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0319' AS qm, '' AS mid, 'PP500粗吸管' AS mname, '根' AS unit, 1 AS qty, 0.075 AS price, 4 AS ord, '吸管' AS src
  UNION ALL
  SELECT '茉莉奶白' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0302' AS qm, '' AS mid, '牛皮纸杯' AS mname, '个' AS unit, 1 AS qty, 0.44 AS price, 1 AS ord, '双层牛皮纸杯' AS src
  UNION ALL
  SELECT '茉莉奶白' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0310' AS qm, '' AS mid, '封口膜' AS mname, '张' AS unit, 1 AS qty, 0.05 AS price, 2 AS ord, '封口' AS src
  UNION ALL
  SELECT '茉莉奶白' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0675' AS qm, '' AS mid, '白色一体盖' AS mname, '个' AS unit, 1 AS qty, 0.22 AS price, 3 AS ord, '白色一体盖' AS src
  UNION ALL
  SELECT '茉莉奶白' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0768' AS qm, '' AS mid, '（新）PLA三孔鲜奶吸管' AS mname, '根' AS unit, 1 AS qty, 0.075 AS price, 4 AS ord, '吸管' AS src
  UNION ALL
  SELECT '新10月黄油红糖(等普洱)' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0302' AS qm, '' AS mid, '牛皮纸杯' AS mname, '个' AS unit, 1 AS qty, 0.44 AS price, 1 AS ord, '双层牛皮纸杯' AS src
  UNION ALL
  SELECT '新10月黄油红糖(等普洱)' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0310' AS qm, '' AS mid, '封口膜' AS mname, '张' AS unit, 1 AS qty, 0.05 AS price, 2 AS ord, '封口' AS src
  UNION ALL
  SELECT '新10月黄油红糖(等普洱)' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0675' AS qm, '' AS mid, '白色一体盖' AS mname, '个' AS unit, 1 AS qty, 0.22 AS price, 3 AS ord, '白色一体盖' AS src
  UNION ALL
  SELECT '新10月黄油红糖(等普洱)' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0768' AS qm, '' AS mid, '（新）PLA三孔鲜奶吸管' AS mname, '根' AS unit, 1 AS qty, 0.075 AS price, 4 AS ord, '吸管' AS src
  UNION ALL
  SELECT '山野滇红(加雪顶)' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0302' AS qm, '' AS mid, '牛皮纸杯' AS mname, '个' AS unit, 1 AS qty, 0.44 AS price, 1 AS ord, '双层牛皮纸杯' AS src
  UNION ALL
  SELECT '山野滇红(加雪顶)' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0310' AS qm, '' AS mid, '封口膜' AS mname, '张' AS unit, 1 AS qty, 0.05 AS price, 2 AS ord, '封口' AS src
  UNION ALL
  SELECT '山野滇红(加雪顶)' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0675' AS qm, '' AS mid, '白色一体盖' AS mname, '个' AS unit, 1 AS qty, 0.22 AS price, 3 AS ord, '白色一体盖' AS src
  UNION ALL
  SELECT '山野滇红(加雪顶)' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0768' AS qm, '' AS mid, '（新）PLA三孔鲜奶吸管' AS mname, '根' AS unit, 1 AS qty, 0.075 AS price, 4 AS ord, '吸管' AS src
  UNION ALL
  SELECT '山野滇红(加雪顶)' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0304' AS qm, '' AS mid, '小料勺' AS mname, '个' AS unit, 1 AS qty, 0.19 AS price, 5 AS ord, '耗材' AS src
  UNION ALL
  SELECT '山野滇红(去雪顶)' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0302' AS qm, '' AS mid, '牛皮纸杯' AS mname, '个' AS unit, 1 AS qty, 0.44 AS price, 1 AS ord, '双层牛皮纸杯' AS src
  UNION ALL
  SELECT '山野滇红(去雪顶)' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0310' AS qm, '' AS mid, '封口膜' AS mname, '张' AS unit, 1 AS qty, 0.05 AS price, 2 AS ord, '封口' AS src
  UNION ALL
  SELECT '山野滇红(去雪顶)' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0675' AS qm, '' AS mid, '白色一体盖' AS mname, '个' AS unit, 1 AS qty, 0.22 AS price, 3 AS ord, '白色一体盖' AS src
  UNION ALL
  SELECT '山野滇红(去雪顶)' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0768' AS qm, '' AS mid, '（新）PLA三孔鲜奶吸管' AS mname, '根' AS unit, 1 AS qty, 0.075 AS price, 4 AS ord, '吸管' AS src
  UNION ALL
  SELECT '山野滇红(去雪顶)' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0304' AS qm, '' AS mid, '小料勺' AS mname, '个' AS unit, 1 AS qty, 0.19 AS price, 5 AS ord, '耗材' AS src
  UNION ALL
  SELECT '杨枝甘露双倍果肉' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0314' AS qm, '' AS mid, '注塑杯(500)' AS mname, '个' AS unit, 1 AS qty, 0.44 AS price, 1 AS ord, '500注塑杯' AS src
  UNION ALL
  SELECT '杨枝甘露双倍果肉' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0310' AS qm, '' AS mid, '封口膜' AS mname, '张' AS unit, 1 AS qty, 0.05 AS price, 2 AS ord, '封口' AS src
  UNION ALL
  SELECT '杨枝甘露双倍果肉' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0675' AS qm, '' AS mid, '白色一体盖' AS mname, '个' AS unit, 1 AS qty, 0.22 AS price, 3 AS ord, '白色一体盖' AS src
  UNION ALL
  SELECT '杨枝甘露双倍果肉' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0319' AS qm, '' AS mid, 'PP500粗吸管' AS mname, '根' AS unit, 1 AS qty, 0.075 AS price, 4 AS ord, '吸管' AS src
  UNION ALL
  SELECT '牛油果燕麦椰椰' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0327' AS qm, '' AS mid, '95PET杯+盖' AS mname, '个' AS unit, 1 AS qty, 0.5 AS price, 1 AS ord, '98连体盖(改95)' AS src
  UNION ALL
  SELECT '牛油果燕麦椰椰' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0319' AS qm, '' AS mid, 'PP500粗吸管' AS mname, '根' AS unit, 1 AS qty, 0.075 AS price, 2 AS ord, '吸管' AS src
  UNION ALL
  SELECT '牛油果碧根果' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0651' AS qm, '' AS mid, '胖胖瓶' AS mname, '个' AS unit, 1 AS qty, 1.2 AS price, 1 AS ord, '胖胖杯(含盖一体)' AS src
  UNION ALL
  SELECT '牛油果碧根果' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0319' AS qm, '' AS mid, 'PP500粗吸管' AS mname, '根' AS unit, 1 AS qty, 0.075 AS price, 2 AS ord, '吸管' AS src
  UNION ALL
  SELECT '厚牛油果酸奶巴旦木(奶昔)' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0314' AS qm, '' AS mid, '注塑杯(500)' AS mname, '个' AS unit, 1 AS qty, 0.44 AS price, 1 AS ord, '500注塑杯' AS src
  UNION ALL
  SELECT '厚牛油果酸奶巴旦木(奶昔)' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0310' AS qm, '' AS mid, '封口膜' AS mname, '张' AS unit, 1 AS qty, 0.05 AS price, 2 AS ord, '封口' AS src
  UNION ALL
  SELECT '厚牛油果酸奶巴旦木(奶昔)' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0675' AS qm, '' AS mid, '白色一体盖' AS mname, '个' AS unit, 1 AS qty, 0.22 AS price, 3 AS ord, '白色一体盖' AS src
  UNION ALL
  SELECT '厚牛油果酸奶巴旦木(奶昔)' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0319' AS qm, '' AS mid, 'PP500粗吸管' AS mname, '根' AS unit, 1 AS qty, 0.075 AS price, 4 AS ord, '吸管' AS src
  UNION ALL
  SELECT '双倍牛油果蔓越莓' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0651' AS qm, '' AS mid, '胖胖瓶' AS mname, '个' AS unit, 1 AS qty, 1.2 AS price, 1 AS ord, '胖胖杯(含盖一体)' AS src
  UNION ALL
  SELECT '双倍牛油果蔓越莓' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0319' AS qm, '' AS mid, 'PP500粗吸管' AS mname, '根' AS unit, 1 AS qty, 0.075 AS price, 2 AS ord, '吸管' AS src
  UNION ALL
  SELECT '酸角话梅·版纳' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0327' AS qm, '' AS mid, '95PET杯+盖' AS mname, '个' AS unit, 1 AS qty, 0.5 AS price, 1 AS ord, '98连体盖(改95)' AS src
  UNION ALL
  SELECT '酸角话梅·版纳' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0869' AS qm, '' AS mid, 'PP700细吸管（新）' AS mname, '根' AS unit, 1 AS qty, 0.05 AS price, 2 AS ord, '吸管' AS src
  UNION ALL
  SELECT '清甜芭乐冰茶' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0327' AS qm, '' AS mid, '95PET杯+盖' AS mname, '个' AS unit, 1 AS qty, 0.5 AS price, 1 AS ord, '98连体盖(改95)' AS src
  UNION ALL
  SELECT '清甜芭乐冰茶' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0869' AS qm, '' AS mid, 'PP700细吸管（新）' AS mname, '根' AS unit, 1 AS qty, 0.05 AS price, 2 AS ord, '吸管' AS src
  UNION ALL
  SELECT '清甜芭乐果奶' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0327' AS qm, '' AS mid, '95PET杯+盖' AS mname, '个' AS unit, 1 AS qty, 0.5 AS price, 1 AS ord, '98连体盖(改95)' AS src
  UNION ALL
  SELECT '清甜芭乐果奶' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0768' AS qm, '' AS mid, '（新）PLA三孔鲜奶吸管' AS mname, '根' AS unit, 1 AS qty, 0.075 AS price, 2 AS ord, '吸管' AS src
  UNION ALL
  SELECT '玫瑰奶白酒' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0327' AS qm, '' AS mid, '95PET杯+盖' AS mname, '个' AS unit, 1 AS qty, 0.5 AS price, 1 AS ord, '98连体盖(改95)' AS src
  UNION ALL
  SELECT '玫瑰奶白酒' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0319' AS qm, '' AS mid, 'PP500粗吸管' AS mname, '根' AS unit, 1 AS qty, 0.075 AS price, 2 AS ord, '吸管' AS src
  UNION ALL
  SELECT '玫瑰奶白酒-热(无糖版)' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0302' AS qm, '' AS mid, '牛皮纸杯' AS mname, '个' AS unit, 1 AS qty, 0.44 AS price, 1 AS ord, '双层牛皮纸杯' AS src
  UNION ALL
  SELECT '玫瑰奶白酒-热(无糖版)' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0310' AS qm, '' AS mid, '封口膜' AS mname, '张' AS unit, 1 AS qty, 0.05 AS price, 2 AS ord, '封口' AS src
  UNION ALL
  SELECT '玫瑰奶白酒-热(无糖版)' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0675' AS qm, '' AS mid, '白色一体盖' AS mname, '个' AS unit, 1 AS qty, 0.22 AS price, 3 AS ord, '白色一体盖' AS src
  UNION ALL
  SELECT '玫瑰奶白酒-热(无糖版)' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0319' AS qm, '' AS mid, 'PP500粗吸管' AS mname, '根' AS unit, 1 AS qty, 0.075 AS price, 4 AS ord, '吸管' AS src
  UNION ALL
  SELECT '青柚与葡萄柚' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0327' AS qm, '' AS mid, '95PET杯+盖' AS mname, '个' AS unit, 1 AS qty, 0.5 AS price, 1 AS ord, '98连体盖(改95)' AS src
  UNION ALL
  SELECT '青柚与葡萄柚' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0319' AS qm, '' AS mid, 'PP500粗吸管' AS mname, '根' AS unit, 1 AS qty, 0.075 AS price, 2 AS ord, '吸管' AS src
  UNION ALL
  SELECT '青柚乌龙冰茶' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0327' AS qm, '' AS mid, '95PET杯+盖' AS mname, '个' AS unit, 1 AS qty, 0.5 AS price, 1 AS ord, '98连体盖(改95)' AS src
  UNION ALL
  SELECT '青柚乌龙冰茶' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0768' AS qm, '' AS mid, '（新）PLA三孔鲜奶吸管' AS mname, '根' AS unit, 1 AS qty, 0.075 AS price, 2 AS ord, '吸管' AS src
) s
JOIN cost_product p ON p.product_name = s.pname
JOIN cost_card    k ON k.product_id = p.id AND FIND_IN_SET(k.temp_key, s.tkeys) > 0
JOIN material     m ON m.del_flag = 0
                       AND ((s.qm <> '' AND m.qm_code = s.qm) OR (s.mid <> '' AND m.material_id = s.mid))
;

-- ==============================================================================================
-- 4) 核对（执行后跑）
-- ==============================================================================================
-- 4.1 新增行数（应等于 0) 的 will_insert）
-- SELECT COUNT(*) AS inserted FROM cost_card_item WHERE item_type='PACK' AND material_id IS NOT NULL;

-- 4.2 逐卡包材成本：新明细 vs 原整包金额（delta 即口径差）
-- SELECT p.product_name, k.temp_key,
--        ROUND(SUM(i.amount),4) AS pack_cost,
--        (SELECT ROUND(b.amount,4) FROM cost_card_item_pack_bak_20260911 b WHERE b.card_id=k.id LIMIT 1) AS old_lump,
--        ROUND(SUM(i.amount) - (SELECT b.amount FROM cost_card_item_pack_bak_20260911 b WHERE b.card_id=k.id LIMIT 1), 4) AS delta
-- FROM cost_card_item i
-- JOIN cost_card k    ON k.id = i.card_id
-- JOIN cost_product p ON p.id = k.product_id
-- WHERE i.item_type='PACK' AND i.material_id IS NOT NULL
-- GROUP BY p.product_name, k.temp_key ORDER BY delta DESC;

-- 4.3 单价漂移核对：卡里快照单价 vs 现盘点单价（应返回空集）
-- SELECT p.product_name, k.temp_key, i.material_name, i.unit_price AS card_price, r.unit_price AS rule_price
-- FROM cost_card_item i
-- JOIN cost_card k    ON k.id = i.card_id
-- JOIN cost_product p ON p.id = k.product_id
-- LEFT JOIN material_inventory_rule r ON r.material_id = i.material_id AND r.del_flag = 0
-- WHERE i.item_type='PACK' AND i.material_id IS NOT NULL AND i.unit_price <> r.unit_price;

-- 4.4 残留整包行（应只剩 SOP 未覆盖的 17 个产品）
-- SELECT p.product_name, k.temp_key, i.amount FROM cost_card_item i
-- JOIN cost_card k    ON k.id = i.card_id
-- JOIN cost_product p ON p.id = k.product_id
-- WHERE i.item_type='PACK' AND i.material_id IS NULL ORDER BY p.product_name;

-- ==============================================================================================
-- 回滚
-- ==============================================================================================
-- DELETE i FROM cost_card_item i
-- JOIN cost_card k    ON k.id = i.card_id
-- JOIN cost_product p ON p.id = k.product_id
-- WHERE i.item_type='PACK' AND i.material_id IS NOT NULL AND (
--   p.product_name IN ('厚牛油果酸奶巴旦木(奶昔)', '双倍牛油果蔓越莓', '山野滇红(加雪顶)', '山野滇红(去雪顶)')
--    OR p.product_name IN ('手作米布鲜奶茶', '手炒黑糖珍珠', '新10月黄油红糖(等普洱)', '杨枝甘露双倍果肉')
--    OR p.product_name IN ('树番茄舂柠檬', '树番茄话梅·德宏', '清甜芭乐冰茶', '清甜芭乐果奶')
--    OR p.product_name IN ('滇橄榄舂柠檬', '滇红舂柠檬', '牛油果燕麦椰椰', '牛油果碧根果')
--    OR p.product_name IN ('牛油果酸奶巴旦木', '玫瑰奶白酒', '玫瑰奶白酒-热(无糖版)', '纸杯白毫茉莉')
--    OR p.product_name IN ('茉莉奶白', '茉莉舂柠檬', '酸奶紫米露', '酸角话梅·版纳')
--    OR p.product_name IN ('青柚与葡萄柚', '青柚乌龙冰茶', '青柚滇橄榄', '鲜奶黑糖啵啵')
-- );
-- INSERT INTO cost_card_item (card_id, item_type, excel_item_name, material_name, material_id,
--   qm_code, semi_code, quantity, unit, loss_rate, unit_price, amount, sort_no, created_at, updated_at)
-- SELECT card_id, item_type, excel_item_name, material_name, material_id,
--   qm_code, semi_code, quantity, unit, loss_rate, unit_price, amount, sort_no, created_at, updated_at
-- FROM cost_card_item_pack_bak_20260911;

-- ==============================================================================================
-- 堂食口径备选：把 3) 里这三张卡的 SELECT 行替换成下面这段（其余不变）
--   差异：牛油果酸奶巴旦木 吸管 500粗→700粗；山野滇红 封口+一体盖→半球盖
-- ==============================================================================================
-- SELECT '牛油果酸奶巴旦木' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0314' AS qm, '' AS mid, '注塑杯(500)' AS mname, '个' AS unit, 1 AS qty, 0.44 AS price, 1 AS ord, '500注塑杯' AS src
-- SELECT '牛油果酸奶巴旦木' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0312' AS qm, '' AS mid, '半球杯盖' AS mname, '个' AS unit, 1 AS qty, 0.15 AS price, 2 AS ord, '半球盖' AS src
-- SELECT '牛油果酸奶巴旦木' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0313' AS qm, '' AS mid, 'PP700粗吸管' AS mname, '支' AS unit, 1 AS qty, 0.1 AS price, 3 AS ord, '吸管' AS src
-- SELECT '山野滇红(加雪顶)' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0302' AS qm, '' AS mid, '牛皮纸杯' AS mname, '个' AS unit, 1 AS qty, 0.44 AS price, 1 AS ord, '双层牛皮纸杯' AS src
-- SELECT '山野滇红(加雪顶)' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0312' AS qm, '' AS mid, '半球杯盖' AS mname, '个' AS unit, 1 AS qty, 0.15 AS price, 2 AS ord, '半球盖' AS src
-- SELECT '山野滇红(加雪顶)' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0768' AS qm, '' AS mid, '（新）PLA三孔鲜奶吸管' AS mname, '根' AS unit, 1 AS qty, 0.075 AS price, 3 AS ord, '吸管' AS src
-- SELECT '山野滇红(加雪顶)' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0304' AS qm, '' AS mid, '小料勺' AS mname, '个' AS unit, 1 AS qty, 0.19 AS price, 4 AS ord, '耗材' AS src
-- SELECT '山野滇红(去雪顶)' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0302' AS qm, '' AS mid, '牛皮纸杯' AS mname, '个' AS unit, 1 AS qty, 0.44 AS price, 1 AS ord, '双层牛皮纸杯' AS src
-- SELECT '山野滇红(去雪顶)' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0312' AS qm, '' AS mid, '半球杯盖' AS mname, '个' AS unit, 1 AS qty, 0.15 AS price, 2 AS ord, '半球盖' AS src
-- SELECT '山野滇红(去雪顶)' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0768' AS qm, '' AS mid, '（新）PLA三孔鲜奶吸管' AS mname, '根' AS unit, 1 AS qty, 0.075 AS price, 3 AS ord, '吸管' AS src
-- SELECT '山野滇红(去雪顶)' AS pname, 'normal_ice,less_ice,least_ice,no_ice,ice,default,warm,hot,room_temp' AS tkeys, 'WP0304' AS qm, '' AS mid, '小料勺' AS mname, '个' AS unit, 1 AS qty, 0.19 AS price, 4 AS ord, '耗材' AS src
