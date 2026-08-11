-- ============================================================
-- 脚本2：插入 Excel 中新增的物料
-- 自动跳过已存在的 qm_code
-- 对于已存在但 del_flag!=0 的物料：reactivate 为 del_flag=0
-- 执行前请先跑 update_del_flag.sql
-- ============================================================

-- Step 2a: 重新激活已存在但被标记删除的物料（del_flag=1 或 2 → 0）
UPDATE material
SET del_flag = 0
WHERE del_flag IN (1, 2)
  AND qm_code IN (
        'WP0917','WP0869','WP0864','WP0684','WP0350','WP0868','WP0854','WP0850','WP0783','WP0687',
        'WP0583','WP0573','WP0358','WP0356','WP0345','WP0343','WP0342','WP0341','WP0340','WP0339',
        'WP0338','WP0337','WP0336','WP0334','WP0333','WP0860','WP0859','WP0858','WP0857','WP0851',
        'WP0788','WP0787','WP0786','WP0758','WP0755','WP0728','WP0689','WP0642','WP0639','WP0384',
        'WP0383','WP0381','WP0378','WP0354','WP0352','WP0688','WP0648','WP0644','WP0643','WP0641',
        'WP0585','WP0584','WP0580','WP0574','WP0572','WP0571','WP0565','WP0557','WP0556','WP0555',
        'WP0553','WP0552','WP0551','WP0549','WP0540','WP0733','WP0698','WP0645','WP0631','WP0577',
        'WP0537','WP0536','WP0535','WP0534','WP0533','WP0531','WP0530','WP0382','WP0374','WP0373',
        'WP0372','WP0368','WP0366','WP0365','WP0852','WP0675','WP0661','WP0651','WP0364','WP0363',
        'WP0362','WP0361','WP0360','WP0346','WP0330','WP0327','WP0325','WP0320','WP0316','WP0314',
        'WP0312','WP0302','WP0301','WP0768','WP0751','WP0750','WP0674','WP0673','WP0603','WP0602',
        'WP0601','WP0600','WP0430','WP0319','WP0318','WP0313','WP0311','WP0310','WP0309','WP0306',
        'WP0305','WP0304','WP0856','WP0525','WP0524','WP0523','WP0516','WP0510','WP0509','WP0508',
        'WP0505','WP0502','WP0500','WP0497','WP0495','WP0494','WP0492','WP0491','WP0490','WP0488',
        'WP0487','WP0778','WP0764','WP0752','WP0735','WP0693','WP0676','WP0647','WP0646','WP0608',
        'WP0579','WP0575','WP0564','WP0562','WP0561','WP0559','WP0558','WP0548','WP0526','WP0499',
        'WP0486','WP0399','WP0396','WP0395','WP0388','WP0332','WP0944'
  );

SELECT ROW_COUNT() AS 重新激活行数;

-- Step 2b: 插入全新物料
INSERT INTO material (material_id, qm_code, material_name, spec, del_flag)
SELECT src.qm_code, src.qm_code, src.material_name, src.spec, 0
FROM (
            SELECT 'WP0917' AS qm_code, '甜白酒（新版）' AS material_name, '750g/瓶*12瓶/件' AS spec
    UNION ALL SELECT 'WP0869' AS qm_code, 'PP700细吸管（新）' AS material_name, '200根 / 包*20/件' AS spec
    UNION ALL SELECT 'WP0864' AS qm_code, '芭乐释迦杯套' AS material_name, '50张/捆' AS spec
    UNION ALL SELECT 'WP0684' AS qm_code, '棉蒸布' AS material_name, '10张/捆*5捆/件' AS spec
    UNION ALL SELECT 'WP0350' AS qm_code, '速冻芒果浆' AS material_name, '950ml/瓶*12瓶/件' AS spec
    UNION ALL SELECT 'WP0868' AS qm_code, '普洱茶（新版）' AS material_name, '50g/包*10包/份' AS spec
    UNION ALL SELECT 'WP0854' AS qm_code, '速冻红芭乐果浆' AS material_name, '950g/瓶 * 12瓶/件' AS spec
    UNION ALL SELECT 'WP0850' AS qm_code, '话梅饮料浓浆' AS material_name, '500g/包*20包/件' AS spec
    UNION ALL SELECT 'WP0783' AS qm_code, '冷冻胭脂果汁' AS material_name, '950ml/瓶*12瓶/件' AS spec
    UNION ALL SELECT 'WP0687' AS qm_code, '速冻白西柚果粒' AS material_name, '500g/包*20包/件' AS spec
    UNION ALL SELECT 'WP0583' AS qm_code, '花瓣（原材料）' AS material_name, '50g/包*100包/件' AS spec
    UNION ALL SELECT 'WP0573' AS qm_code, '糯米小丸子' AS material_name, '1kg/包*16包/件' AS spec
    UNION ALL SELECT 'WP0358' AS qm_code, '冷冻滇橄榄原汁' AS material_name, '1L/瓶 * 6瓶/件' AS spec
    UNION ALL SELECT 'WP0356' AS qm_code, '冷冻树番茄' AS material_name, '1kg/瓶*12瓶/件' AS spec
    UNION ALL SELECT 'WP0345' AS qm_code, '兰韵清露茶' AS material_name, '50g/包*10包/份' AS spec
    UNION ALL SELECT 'WP0343' AS qm_code, '青玉观音茶' AS material_name, '500g/包*30包/件' AS spec
    UNION ALL SELECT 'WP0342' AS qm_code, '云豪茉莉茶' AS material_name, '100g/包*50包/件' AS spec
    UNION ALL SELECT 'WP0341' AS qm_code, '山野滇红茶' AS material_name, '500g/包*30包/件' AS spec
    UNION ALL SELECT 'WP0340' AS qm_code, '普洱茶（旧版）' AS material_name, '500g/包*25包/件' AS spec
    UNION ALL SELECT 'WP0339' AS qm_code, '黑糖糖浆' AS material_name, '2kg/瓶*10瓶/件' AS spec
    UNION ALL SELECT 'WP0338' AS qm_code, '冰蔗糖浆（新）' AS material_name, '6kg/瓶*4瓶/件' AS spec
    UNION ALL SELECT 'WP0337' AS qm_code, '水果糖浆.' AS material_name, '1kg/瓶*12瓶/件' AS spec
    UNION ALL SELECT 'WP0336' AS qm_code, '幼砂糖-' AS material_name, '30kg/件' AS spec
    UNION ALL SELECT 'WP0334' AS qm_code, '新黑糖粉' AS material_name, '1kg/包*20包/件' AS spec
    UNION ALL SELECT 'WP0333' AS qm_code, '香草糖浆1' AS material_name, '1.2kg/瓶' AS spec
    UNION ALL SELECT 'WP0860' AS qm_code, '释迦果（半成品）' AS material_name, '1000g/kg' AS spec
    UNION ALL SELECT 'WP0859' AS qm_code, '新鲜芭乐' AS material_name, '1000g/kg' AS spec
    UNION ALL SELECT 'WP0858' AS qm_code, '释迦芭乐果肉' AS material_name, '1000g/kg' AS spec
    UNION ALL SELECT 'WP0857' AS qm_code, '预制芭乐汁' AS material_name, '1000g/kg' AS spec
    UNION ALL SELECT 'WP0851' AS qm_code, '牛油果奶油奶酪' AS material_name, '1000g/kg' AS spec
    UNION ALL SELECT 'WP0788' AS qm_code, '秘制葡萄果肉' AS material_name, '1000g/kg' AS spec
    UNION ALL SELECT 'WP0787' AS qm_code, '芝士奶盖' AS material_name, '1000g/kg' AS spec
    UNION ALL SELECT 'WP0786' AS qm_code, '预制胭脂果葡萄浆' AS material_name, '1000g/kg' AS spec
    UNION ALL SELECT 'WP0758' AS qm_code, '橄榄混合汁' AS material_name, '1000g/kg' AS spec
    UNION ALL SELECT 'WP0755' AS qm_code, '鲜橙预制汁' AS material_name, '1000g/kg' AS spec
    UNION ALL SELECT 'WP0728' AS qm_code, '玫瑰汁' AS material_name, '1000g/kg' AS spec
    UNION ALL SELECT 'WP0689' AS qm_code, '芒果粒' AS material_name, '1000g/kg' AS spec
    UNION ALL SELECT 'WP0642' AS qm_code, '咸法干酪乳' AS material_name, '1L/瓶*12瓶/件' AS spec
    UNION ALL SELECT 'WP0639' AS qm_code, '厚椰乳' AS material_name, '1L/瓶*12瓶/件' AS spec
    UNION ALL SELECT 'WP0384' AS qm_code, '悦鲜活鲜奶' AS material_name, '950ml/瓶*12瓶/件' AS spec
    UNION ALL SELECT 'WP0383' AS qm_code, '乍甸酸奶' AS material_name, '180g/包*20包/份' AS spec
    UNION ALL SELECT 'WP0381' AS qm_code, '安佳淡奶油' AS material_name, '1L/瓶*12瓶/件' AS spec
    UNION ALL SELECT 'WP0378' AS qm_code, '冰勃朗' AS material_name, '1kg/瓶*12瓶/件' AS spec
    UNION ALL SELECT 'WP0354' AS qm_code, '冷冻牛奶米布' AS material_name, '500g/包*20包/件' AS spec
    UNION ALL SELECT 'WP0352' AS qm_code, '冷冻牛油果泥(24包/件)' AS material_name, '250g/包*24包/件' AS spec
    UNION ALL SELECT 'WP0688' AS qm_code, '调制椰奶' AS material_name, '1000g/kg' AS spec
    UNION ALL SELECT 'WP0648' AS qm_code, '燕麦龙珠（本成品）' AS material_name, '1000g/kg' AS spec
    UNION ALL SELECT 'WP0644' AS qm_code, '苦瓜（半成品）' AS material_name, '1000g/kg' AS spec
    UNION ALL SELECT 'WP0643' AS qm_code, '奇亚籽（半成品）' AS material_name, '1000g/kg' AS spec
    UNION ALL SELECT 'WP0641' AS qm_code, '新鲜牛油果' AS material_name, '1000g/kg' AS spec
    UNION ALL SELECT 'WP0585' AS qm_code, '火龙果汁' AS material_name, '1000g/kg' AS spec
    UNION ALL SELECT 'WP0584' AS qm_code, '花瓣（半成品）' AS material_name, '1000g/kg' AS spec
    UNION ALL SELECT 'WP0580' AS qm_code, '木薯淀粉' AS material_name, '1000g/kg' AS spec
    UNION ALL SELECT 'WP0574' AS qm_code, '小丸子' AS material_name, '1000g/kg' AS spec
    UNION ALL SELECT 'WP0572' AS qm_code, '雪顶' AS material_name, '1000g/kg' AS spec
    UNION ALL SELECT 'WP0571' AS qm_code, '橙子块' AS material_name, '1000g/kg' AS spec
    UNION ALL SELECT 'WP0565' AS qm_code, '葡萄整果' AS material_name, '1000g/kg' AS spec
    UNION ALL SELECT 'WP0557' AS qm_code, '普洱茶茶汤' AS material_name, '1000g/kg' AS spec
    UNION ALL SELECT 'WP0556' AS qm_code, '山野滇红茶汤' AS material_name, '1000g/kg' AS spec
    UNION ALL SELECT 'WP0555' AS qm_code, '青玉观音茶汤' AS material_name, '1000g/kg' AS spec
    UNION ALL SELECT 'WP0553' AS qm_code, '云豪茉莉茶汤' AS material_name, '1000g/kg' AS spec
    UNION ALL SELECT 'WP0552' AS qm_code, '兰韵清露茶汤' AS material_name, '1000g/kg' AS spec
    UNION ALL SELECT 'WP0551' AS qm_code, '南非橙' AS material_name, '1000g/kg' AS spec
    UNION ALL SELECT 'WP0549' AS qm_code, '羽衣甘蓝叶（半成品）' AS material_name, '1000g/kg' AS spec
    UNION ALL SELECT 'WP0540' AS qm_code, '芒果酱' AS material_name, '1000g/kg' AS spec
    UNION ALL SELECT 'WP0733' AS qm_code, '酸角果泥果酱' AS material_name, '1kg/瓶*12瓶/件' AS spec
    UNION ALL SELECT 'WP0698' AS qm_code, '洱宝话梅' AS material_name, '160g/包*40包/件' AS spec
    UNION ALL SELECT 'WP0645' AS qm_code, '奇亚籽（原材料）' AS material_name, '1000g/包*10包/件' AS spec
    UNION ALL SELECT 'WP0631' AS qm_code, '燕麦龙珠（原材料）' AS material_name, '850g/瓶*12瓶/件' AS spec
    UNION ALL SELECT 'WP0577' AS qm_code, '玫瑰酱' AS material_name, '1000g/瓶*12瓶/件' AS spec
    UNION ALL SELECT 'WP0537' AS qm_code, '黄油红糖' AS material_name, '1000g/kg' AS spec
    UNION ALL SELECT 'WP0536' AS qm_code, '珍珠' AS material_name, '1000g/kg' AS spec
    UNION ALL SELECT 'WP0535' AS qm_code, '寒天冻' AS material_name, '1000g/kg' AS spec
    UNION ALL SELECT 'WP0534' AS qm_code, '柠檬片' AS material_name, '1000g/kg' AS spec
    UNION ALL SELECT 'WP0533' AS qm_code, '酸奶酱' AS material_name, '1000g/kg' AS spec
    UNION ALL SELECT 'WP0531' AS qm_code, '牛油果泥' AS material_name, '1000g/kg' AS spec
    UNION ALL SELECT 'WP0530' AS qm_code, '紫米（半成品）' AS material_name, '1000g/kg' AS spec
    UNION ALL SELECT 'WP0382' AS qm_code, '氧化亚氮气弹' AS material_name, '10支/盒*10盒/件' AS spec
    UNION ALL SELECT 'WP0374' AS qm_code, '酸奶粉' AS material_name, '1kg/包*20包/件' AS spec
    UNION ALL SELECT 'WP0373' AS qm_code, '紫米（原材料）' AS material_name, '500g/包*24包/件' AS spec
    UNION ALL SELECT 'WP0372' AS qm_code, '炒米' AS material_name, '500g/瓶*25瓶/件' AS spec
    UNION ALL SELECT 'WP0368' AS qm_code, '维生素C' AS material_name, '1kg/包' AS spec
    UNION ALL SELECT 'WP0366' AS qm_code, '珍珠粉圆' AS material_name, '1kg/包*16包/件' AS spec
    UNION ALL SELECT 'WP0365' AS qm_code, '水晶冻粉' AS material_name, '1kg/包*12包/件' AS spec
    UNION ALL SELECT 'WP0852' AS qm_code, '纸杯（顾客喝水用）' AS material_name, '25个/提' AS spec
    UNION ALL SELECT 'WP0675' AS qm_code, '白色一体盖' AS material_name, '1000个/件' AS spec
    UNION ALL SELECT 'WP0661' AS qm_code, '针织袋(通版）' AS material_name, '个' AS spec
    UNION ALL SELECT 'WP0651' AS qm_code, '胖胖瓶' AS material_name, '100个/件' AS spec
    UNION ALL SELECT 'WP0364' AS qm_code, '剥壳巴旦木1公斤' AS material_name, '1000g/包*10包/件' AS spec
    UNION ALL SELECT 'WP0363' AS qm_code, '大麦若叶粉' AS material_name, '250g/包*24包/件' AS spec
    UNION ALL SELECT 'WP0362' AS qm_code, '云南红糖' AS material_name, '1000g/包*10包/件' AS spec
    UNION ALL SELECT 'WP0361' AS qm_code, '碧根果仁' AS material_name, '250g/包*40包/件' AS spec
    UNION ALL SELECT 'WP0360' AS qm_code, '泰国木薯淀粉' AS material_name, '500g/包*30包/件' AS spec
    UNION ALL SELECT 'WP0346' AS qm_code, '安佳黄油' AS material_name, '227g/盒*40盒/件' AS spec
    UNION ALL SELECT 'WP0330' AS qm_code, '无纺布单杯袋' AS material_name, '600个/件' AS spec
    UNION ALL SELECT 'WP0327' AS qm_code, '98PET杯+盖' AS material_name, '1000个/件' AS spec
    UNION ALL SELECT 'WP0325' AS qm_code, '无纺布双杯袋' AS material_name, '400个/件' AS spec
    UNION ALL SELECT 'WP0320' AS qm_code, '纸质小料杯' AS material_name, '50个/捆*20捆/件' AS spec
    UNION ALL SELECT 'WP0316' AS qm_code, 'PLA单个打包袋' AS material_name, '100个/捆*20捆/件' AS spec
    UNION ALL SELECT 'WP0314' AS qm_code, '注塑杯(500)' AS material_name, '500个/件' AS spec
    UNION ALL SELECT 'WP0312' AS qm_code, '半球杯盖' AS material_name, '1000个/件' AS spec
    UNION ALL SELECT 'WP0302' AS qm_code, '牛皮纸杯' AS material_name, '500个/件' AS spec
    UNION ALL SELECT 'WP0301' AS qm_code, '试饮杯' AS material_name, '50个/捆*40捆/件' AS spec
    UNION ALL SELECT 'WP0768' AS qm_code, '（新）PLA三孔鲜奶吸管' AS material_name, '200根/包*25包/件' AS spec
    UNION ALL SELECT 'WP0751' AS qm_code, '沙发清洁剂' AS material_name, '500ml/瓶' AS spec
    UNION ALL SELECT 'WP0750' AS qm_code, '椰子油去污膏' AS material_name, '450g/瓶' AS spec
    UNION ALL SELECT 'WP0674' AS qm_code, '含氟消毒片' AS material_name, '100片/瓶' AS spec
    UNION ALL SELECT 'WP0673' AS qm_code, '餐具浸泡去渍粉' AS material_name, '1000g/瓶' AS spec
    UNION ALL SELECT 'WP0603' AS qm_code, '一次性胶手套(L码)' AS material_name, '50副/包*10包/L码' AS spec
    UNION ALL SELECT 'WP0602' AS qm_code, '一次性胶手套(M码)' AS material_name, '50副/包*10包/M码' AS spec
    UNION ALL SELECT 'WP0601' AS qm_code, '柠檬除胶剂' AS material_name, '1kg/瓶' AS spec
    UNION ALL SELECT 'WP0600' AS qm_code, '双头铲刀' AS material_name, '把' AS spec
    UNION ALL SELECT 'WP0430' AS qm_code, '保鲜膜' AS material_name, '卷' AS spec
    UNION ALL SELECT 'WP0319' AS qm_code, 'PP500粗吸管' AS material_name, '100根/包*20包/件' AS spec
    UNION ALL SELECT 'WP0318' AS qm_code, 'PP700细吸管-（旧）' AS material_name, '100支/包*20包/件' AS spec
    UNION ALL SELECT 'WP0313' AS qm_code, 'PP700粗吸管' AS material_name, '100支/20包/件' AS spec
    UNION ALL SELECT 'WP0311' AS qm_code, 'PLA双个打包袋' AS material_name, '100个/捆*20捆/件' AS spec
    UNION ALL SELECT 'WP0310' AS qm_code, '封口膜' AS material_name, '2000张/卷' AS spec
    UNION ALL SELECT 'WP0309' AS qm_code, '新版小黑勺' AS material_name, '100根/包*5包/件' AS spec
    UNION ALL SELECT 'WP0306' AS qm_code, '四杯托(纸托)' AS material_name, '300个/件' AS spec
    UNION ALL SELECT 'WP0305' AS qm_code, '无纺布四杯袋' AS material_name, '400个/件' AS spec
    UNION ALL SELECT 'WP0304' AS qm_code, '小木勺子(奶油专用)' AS material_name, '100个/包*50包/件' AS spec
    UNION ALL SELECT 'WP0856' AS qm_code, '红心芭乐' AS material_name, '1000g/kg' AS spec
    UNION ALL SELECT 'WP0525' AS qm_code, '台面毛巾(灰色)' AS material_name, '条' AS spec
    UNION ALL SELECT 'WP0524' AS qm_code, '地面毛巾(酒红色)' AS material_name, '条' AS spec
    UNION ALL SELECT 'WP0523' AS qm_code, 'T33口感调节活性炭' AS material_name, '40根/箱' AS spec
    UNION ALL SELECT 'WP0516' AS qm_code, '除油喷壶' AS material_name, '个' AS spec
    UNION ALL SELECT 'WP0510' AS qm_code, '纯水机GR0膜' AS material_name, '根' AS spec
    UNION ALL SELECT 'WP0509' AS qm_code, '纯水机压缩活性炭' AS material_name, '根' AS spec
    UNION ALL SELECT 'WP0508' AS qm_code, '纯水机PP棉' AS material_name, '根' AS spec
    UNION ALL SELECT 'WP0505' AS qm_code, '定制口罩' AS material_name, '50个/捆*50捆/件' AS spec
    UNION ALL SELECT 'WP0502' AS qm_code, '强效清洁剂' AS material_name, '473ml/瓶' AS spec
    UNION ALL SELECT 'WP0500' AS qm_code, '清洁棉' AS material_name, '100盒/包' AS spec
    UNION ALL SELECT 'WP0497' AS qm_code, '蓝色毛巾(蓝色)' AS material_name, '条' AS spec
    UNION ALL SELECT 'WP0495' AS qm_code, '出品毛巾(木色)' AS material_name, '条' AS spec
    UNION ALL SELECT 'WP0494' AS qm_code, '垃圾袋' AS material_name, '包' AS spec
    UNION ALL SELECT 'WP0492' AS qm_code, '酸性清洗粉' AS material_name, '1000g/包' AS spec
    UNION ALL SELECT 'WP0491' AS qm_code, '一次性防尘帽' AS material_name, '100个/包*10包/箱' AS spec
    UNION ALL SELECT 'WP0490' AS qm_code, '定制纸巾' AS material_name, '60包/件' AS spec
    UNION ALL SELECT 'WP0488' AS qm_code, '玻璃清洁剂' AS material_name, '500ml/瓶' AS spec
    UNION ALL SELECT 'WP0487' AS qm_code, '一次性胶手套(S码)' AS material_name, '50副/包*10包/S码' AS spec
    UNION ALL SELECT 'WP0778' AS qm_code, '云南杯套（新）' AS material_name, '50张/捆*20捆/件' AS spec
    UNION ALL SELECT 'WP0764' AS qm_code, '圆边防漏溢纸' AS material_name, '500张/包' AS spec
    UNION ALL SELECT 'WP0752' AS qm_code, '外卖安心贴' AS material_name, '卷' AS spec
    UNION ALL SELECT 'WP0735' AS qm_code, '云南风物杯套' AS material_name, '50张/捆*20捆/件' AS spec
    UNION ALL SELECT 'WP0693' AS qm_code, '牛油果限定公仔' AS material_name, '100个/袋' AS spec
    UNION ALL SELECT 'WP0676' AS qm_code, '牛油果杯套' AS material_name, '50张/捆*20捆/件' AS spec
    UNION ALL SELECT 'WP0647' AS qm_code, '苦瓜（原材料）' AS material_name, '1000g/1kg' AS spec
    UNION ALL SELECT 'WP0646' AS qm_code, '牛油果鲜果' AS material_name, '1000g/1kg' AS spec
    UNION ALL SELECT 'WP0608' AS qm_code, '大象冰箱贴（亚克力）' AS material_name, '1/个' AS spec
    UNION ALL SELECT 'WP0579' AS qm_code, '玫瑰杯套' AS material_name, '25个/捆*80捆/箱' AS spec
    UNION ALL SELECT 'WP0575' AS qm_code, '火龙果' AS material_name, '1000g/kg' AS spec
    UNION ALL SELECT 'WP0564' AS qm_code, '葡萄' AS material_name, '1000g/kg' AS spec
    UNION ALL SELECT 'WP0562' AS qm_code, '芒果' AS material_name, '1000g/kg' AS spec
    UNION ALL SELECT 'WP0561' AS qm_code, '柠檬' AS material_name, '1000g/kg' AS spec
    UNION ALL SELECT 'WP0559' AS qm_code, '橄榄鲜果' AS material_name, '1000g/kg' AS spec
    UNION ALL SELECT 'WP0558' AS qm_code, '橙子' AS material_name, '1000g/kg' AS spec
    UNION ALL SELECT 'WP0548' AS qm_code, '羽衣甘蓝（原料）' AS material_name, '1000g/kg' AS spec
    UNION ALL SELECT 'WP0526' AS qm_code, '80*40小票打印纸(新店使用)' AS material_name, '200卷/件' AS spec
    UNION ALL SELECT 'WP0499' AS qm_code, '防水效期贴' AS material_name, '500张/卷' AS spec
    UNION ALL SELECT 'WP0486' AS qm_code, 'logo标签纸' AS material_name, '5卷/条*20/件' AS spec
    UNION ALL SELECT 'WP0399' AS qm_code, '普洱贴纸' AS material_name, '100张/捆' AS spec
    UNION ALL SELECT 'WP0396' AS qm_code, '牛油果贴纸' AS material_name, '100张/捆' AS spec
    UNION ALL SELECT 'WP0395' AS qm_code, '滇橄榄贴纸' AS material_name, '100张/捆' AS spec
    UNION ALL SELECT 'WP0388' AS qm_code, '滇红贴纸' AS material_name, '100张/捆' AS spec
    UNION ALL SELECT 'WP0332' AS qm_code, '柠檬茶杯套' AS material_name, '50张/捆' AS spec
    UNION ALL SELECT 'WP0944' AS qm_code, '蔓越莓干' AS material_name, '250g/包*40包/件' AS spec
) AS src
WHERE NOT EXISTS (
    SELECT 1 FROM material m WHERE m.qm_code = src.qm_code
);

SELECT ROW_COUNT() AS 新增物料行数;

-- Step 2c: 为新物料创建盘点规则
INSERT INTO material_inventory_rule (rule_id, material_id, base_unit, inventory_units, unit_price, del_flag)
SELECT CONCAT('RULE_', src.qm_code), src.qm_code, src.base_unit, src.base_unit, src.unit_price, 0
FROM (
            SELECT 'WP0917' AS qm_code, 'g' AS base_unit, 0.015 AS unit_price
    UNION ALL SELECT 'WP0869' AS qm_code, '根' AS base_unit, 0.05 AS unit_price
    UNION ALL SELECT 'WP0864' AS qm_code, '张' AS base_unit, 0.24 AS unit_price
    UNION ALL SELECT 'WP0684' AS qm_code, '张' AS base_unit, 6.0 AS unit_price
    UNION ALL SELECT 'WP0350' AS qm_code, 'g' AS base_unit, 0.022 AS unit_price
    UNION ALL SELECT 'WP0868' AS qm_code, 'g' AS base_unit, 0.15 AS unit_price
    UNION ALL SELECT 'WP0854' AS qm_code, 'g' AS base_unit, 0.037 AS unit_price
    UNION ALL SELECT 'WP0850' AS qm_code, 'g' AS base_unit, 0.04 AS unit_price
    UNION ALL SELECT 'WP0783' AS qm_code, 'ml' AS base_unit, 0.042 AS unit_price
    UNION ALL SELECT 'WP0687' AS qm_code, 'g' AS base_unit, 0.04 AS unit_price
    UNION ALL SELECT 'WP0583' AS qm_code, 'g' AS base_unit, 0.24 AS unit_price
    UNION ALL SELECT 'WP0573' AS qm_code, 'g' AS base_unit, 0.018 AS unit_price
    UNION ALL SELECT 'WP0358' AS qm_code, 'g' AS base_unit, 0.035 AS unit_price
    UNION ALL SELECT 'WP0356' AS qm_code, 'g' AS base_unit, 0.045 AS unit_price
    UNION ALL SELECT 'WP0345' AS qm_code, 'g' AS base_unit, 0.16 AS unit_price
    UNION ALL SELECT 'WP0343' AS qm_code, 'g' AS base_unit, 0.16 AS unit_price
    UNION ALL SELECT 'WP0342' AS qm_code, 'g' AS base_unit, 0.2 AS unit_price
    UNION ALL SELECT 'WP0341' AS qm_code, 'g' AS base_unit, 0.176 AS unit_price
    UNION ALL SELECT 'WP0340' AS qm_code, 'g' AS base_unit, 0.13 AS unit_price
    UNION ALL SELECT 'WP0339' AS qm_code, 'g' AS base_unit, 0.038 AS unit_price
    UNION ALL SELECT 'WP0338' AS qm_code, 'g' AS base_unit, 0.011 AS unit_price
    UNION ALL SELECT 'WP0337' AS qm_code, 'g' AS base_unit, 0.025 AS unit_price
    UNION ALL SELECT 'WP0336' AS qm_code, 'g' AS base_unit, 0.012 AS unit_price
    UNION ALL SELECT 'WP0334' AS qm_code, 'g' AS base_unit, 0.03 AS unit_price
    UNION ALL SELECT 'WP0333' AS qm_code, 'g' AS base_unit, 0.025 AS unit_price
    UNION ALL SELECT 'WP0860' AS qm_code, 'g' AS base_unit, 0.021 AS unit_price
    UNION ALL SELECT 'WP0859' AS qm_code, 'g' AS base_unit, 0.017 AS unit_price
    UNION ALL SELECT 'WP0858' AS qm_code, 'g' AS base_unit, 0.026 AS unit_price
    UNION ALL SELECT 'WP0857' AS qm_code, 'g' AS base_unit, 0.024 AS unit_price
    UNION ALL SELECT 'WP0851' AS qm_code, 'g' AS base_unit, 0.047 AS unit_price
    UNION ALL SELECT 'WP0788' AS qm_code, 'g' AS base_unit, 0.022 AS unit_price
    UNION ALL SELECT 'WP0787' AS qm_code, 'g' AS base_unit, 0.033 AS unit_price
    UNION ALL SELECT 'WP0786' AS qm_code, 'g' AS base_unit, 0.022 AS unit_price
    UNION ALL SELECT 'WP0758' AS qm_code, 'g' AS base_unit, 0.032 AS unit_price
    UNION ALL SELECT 'WP0755' AS qm_code, 'g' AS base_unit, 0.03 AS unit_price
    UNION ALL SELECT 'WP0728' AS qm_code, 'g' AS base_unit, 0.023 AS unit_price
    UNION ALL SELECT 'WP0689' AS qm_code, 'g' AS base_unit, 0.011 AS unit_price
    UNION ALL SELECT 'WP0642' AS qm_code, 'ml' AS base_unit, 0.042 AS unit_price
    UNION ALL SELECT 'WP0639' AS qm_code, 'ml' AS base_unit, 0.015 AS unit_price
    UNION ALL SELECT 'WP0384' AS qm_code, 'ml' AS base_unit, 0.016 AS unit_price
    UNION ALL SELECT 'WP0383' AS qm_code, 'g' AS base_unit, 0.014 AS unit_price
    UNION ALL SELECT 'WP0381' AS qm_code, 'ml' AS base_unit, 0.042 AS unit_price
    UNION ALL SELECT 'WP0378' AS qm_code, 'g' AS base_unit, 0.022 AS unit_price
    UNION ALL SELECT 'WP0354' AS qm_code, 'g' AS base_unit, 0.024 AS unit_price
    UNION ALL SELECT 'WP0352' AS qm_code, 'g' AS base_unit, 0.061 AS unit_price
    UNION ALL SELECT 'WP0688' AS qm_code, 'g' AS base_unit, 0.02 AS unit_price
    UNION ALL SELECT 'WP0648' AS qm_code, 'g' AS base_unit, 0.038 AS unit_price
    UNION ALL SELECT 'WP0644' AS qm_code, 'g' AS base_unit, 0.01 AS unit_price
    UNION ALL SELECT 'WP0643' AS qm_code, 'g' AS base_unit, 0.004 AS unit_price
    UNION ALL SELECT 'WP0641' AS qm_code, 'g' AS base_unit, 0.05 AS unit_price
    UNION ALL SELECT 'WP0585' AS qm_code, 'g' AS base_unit, 0.007 AS unit_price
    UNION ALL SELECT 'WP0584' AS qm_code, 'g' AS base_unit, 0.012 AS unit_price
    UNION ALL SELECT 'WP0580' AS qm_code, 'g' AS base_unit, 0.001 AS unit_price
    UNION ALL SELECT 'WP0574' AS qm_code, 'g' AS base_unit, 0.013 AS unit_price
    UNION ALL SELECT 'WP0572' AS qm_code, 'g' AS base_unit, 0.049 AS unit_price
    UNION ALL SELECT 'WP0571' AS qm_code, 'g' AS base_unit, 0.016 AS unit_price
    UNION ALL SELECT 'WP0565' AS qm_code, 'g' AS base_unit, 0.015 AS unit_price
    UNION ALL SELECT 'WP0557' AS qm_code, 'g' AS base_unit, 0.004 AS unit_price
    UNION ALL SELECT 'WP0556' AS qm_code, 'g' AS base_unit, 0.004 AS unit_price
    UNION ALL SELECT 'WP0555' AS qm_code, 'g' AS base_unit, 0.004 AS unit_price
    UNION ALL SELECT 'WP0553' AS qm_code, 'g' AS base_unit, 0.005 AS unit_price
    UNION ALL SELECT 'WP0552' AS qm_code, 'g' AS base_unit, 0.004 AS unit_price
    UNION ALL SELECT 'WP0551' AS qm_code, 'g' AS base_unit, 0.014 AS unit_price
    UNION ALL SELECT 'WP0549' AS qm_code, 'g' AS base_unit, 0.016 AS unit_price
    UNION ALL SELECT 'WP0540' AS qm_code, 'g' AS base_unit, 0.016 AS unit_price
    UNION ALL SELECT 'WP0733' AS qm_code, 'g' AS base_unit, 0.045 AS unit_price
    UNION ALL SELECT 'WP0698' AS qm_code, 'g' AS base_unit, 0.044 AS unit_price
    UNION ALL SELECT 'WP0645' AS qm_code, 'g' AS base_unit, 0.058 AS unit_price
    UNION ALL SELECT 'WP0631' AS qm_code, 'g' AS base_unit, 0.025 AS unit_price
    UNION ALL SELECT 'WP0577' AS qm_code, 'g' AS base_unit, 0.032 AS unit_price
    UNION ALL SELECT 'WP0537' AS qm_code, 'g' AS base_unit, 0.041 AS unit_price
    UNION ALL SELECT 'WP0536' AS qm_code, 'g' AS base_unit, 0.013 AS unit_price
    UNION ALL SELECT 'WP0535' AS qm_code, 'g' AS base_unit, 0.004 AS unit_price
    UNION ALL SELECT 'WP0534' AS qm_code, 'g' AS base_unit, 0.018 AS unit_price
    UNION ALL SELECT 'WP0533' AS qm_code, 'g' AS base_unit, 0.014 AS unit_price
    UNION ALL SELECT 'WP0531' AS qm_code, 'g' AS base_unit, 0.054 AS unit_price
    UNION ALL SELECT 'WP0530' AS qm_code, 'g' AS base_unit, 0.017 AS unit_price
    UNION ALL SELECT 'WP0382' AS qm_code, '支' AS base_unit, 3.5 AS unit_price
    UNION ALL SELECT 'WP0374' AS qm_code, 'g' AS base_unit, 0.035 AS unit_price
    UNION ALL SELECT 'WP0373' AS qm_code, 'g' AS base_unit, 0.01 AS unit_price
    UNION ALL SELECT 'WP0372' AS qm_code, 'g' AS base_unit, 0.017 AS unit_price
    UNION ALL SELECT 'WP0368' AS qm_code, 'g' AS base_unit, 0.004 AS unit_price
    UNION ALL SELECT 'WP0366' AS qm_code, 'g' AS base_unit, 0.013 AS unit_price
    UNION ALL SELECT 'WP0365' AS qm_code, 'g' AS base_unit, 0.035 AS unit_price
    UNION ALL SELECT 'WP0852' AS qm_code, '个' AS base_unit, 0.2 AS unit_price
    UNION ALL SELECT 'WP0675' AS qm_code, '个' AS base_unit, 0.22 AS unit_price
    UNION ALL SELECT 'WP0661' AS qm_code, '个' AS base_unit, 5.0 AS unit_price
    UNION ALL SELECT 'WP0651' AS qm_code, '个' AS base_unit, 1.2 AS unit_price
    UNION ALL SELECT 'WP0364' AS qm_code, 'g' AS base_unit, 0.08 AS unit_price
    UNION ALL SELECT 'WP0363' AS qm_code, 'g' AS base_unit, 0.08 AS unit_price
    UNION ALL SELECT 'WP0362' AS qm_code, 'g' AS base_unit, 0.025 AS unit_price
    UNION ALL SELECT 'WP0361' AS qm_code, 'g' AS base_unit, 0.12 AS unit_price
    UNION ALL SELECT 'WP0360' AS qm_code, 'g' AS base_unit, 0.004 AS unit_price
    UNION ALL SELECT 'WP0346' AS qm_code, 'g' AS base_unit, 0.079 AS unit_price
    UNION ALL SELECT 'WP0330' AS qm_code, '个' AS base_unit, 0.6 AS unit_price
    UNION ALL SELECT 'WP0327' AS qm_code, '个' AS base_unit, 0.5 AS unit_price
    UNION ALL SELECT 'WP0325' AS qm_code, '个' AS base_unit, 0.713 AS unit_price
    UNION ALL SELECT 'WP0320' AS qm_code, '个' AS base_unit, 0.2 AS unit_price
    UNION ALL SELECT 'WP0316' AS qm_code, '个' AS base_unit, 0.24 AS unit_price
    UNION ALL SELECT 'WP0314' AS qm_code, '个' AS base_unit, 0.44 AS unit_price
    UNION ALL SELECT 'WP0312' AS qm_code, '个' AS base_unit, 0.15 AS unit_price
    UNION ALL SELECT 'WP0302' AS qm_code, '个' AS base_unit, 0.44 AS unit_price
    UNION ALL SELECT 'WP0301' AS qm_code, '个' AS base_unit, 0.1 AS unit_price
    UNION ALL SELECT 'WP0768' AS qm_code, '根' AS base_unit, 0.075 AS unit_price
    UNION ALL SELECT 'WP0751' AS qm_code, 'ml' AS base_unit, 0.03 AS unit_price
    UNION ALL SELECT 'WP0750' AS qm_code, 'g' AS base_unit, 0.04 AS unit_price
    UNION ALL SELECT 'WP0674' AS qm_code, '片' AS base_unit, 0.52 AS unit_price
    UNION ALL SELECT 'WP0673' AS qm_code, 'g' AS base_unit, 0.055 AS unit_price
    UNION ALL SELECT 'WP0603' AS qm_code, '副' AS base_unit, 0.7 AS unit_price
    UNION ALL SELECT 'WP0602' AS qm_code, '副' AS base_unit, 0.7 AS unit_price
    UNION ALL SELECT 'WP0601' AS qm_code, 'g' AS base_unit, 0.045 AS unit_price
    UNION ALL SELECT 'WP0600' AS qm_code, '把' AS base_unit, 3.0 AS unit_price
    UNION ALL SELECT 'WP0430' AS qm_code, '卷' AS base_unit, 40.0 AS unit_price
    UNION ALL SELECT 'WP0319' AS qm_code, '根' AS base_unit, 0.075 AS unit_price
    UNION ALL SELECT 'WP0318' AS qm_code, '支' AS base_unit, 0.05 AS unit_price
    UNION ALL SELECT 'WP0313' AS qm_code, '支' AS base_unit, 0.05 AS unit_price
    UNION ALL SELECT 'WP0311' AS qm_code, '个' AS base_unit, 0.36 AS unit_price
    UNION ALL SELECT 'WP0310' AS qm_code, '张' AS base_unit, 0.05 AS unit_price
    UNION ALL SELECT 'WP0309' AS qm_code, '根' AS base_unit, 0.18 AS unit_price
    UNION ALL SELECT 'WP0306' AS qm_code, '个' AS base_unit, 0.517 AS unit_price
    UNION ALL SELECT 'WP0305' AS qm_code, '个' AS base_unit, 1.0 AS unit_price
    UNION ALL SELECT 'WP0304' AS qm_code, '个' AS base_unit, 0.19 AS unit_price
    UNION ALL SELECT 'WP0856' AS qm_code, 'g' AS base_unit, 0.014 AS unit_price
    UNION ALL SELECT 'WP0525' AS qm_code, '条' AS base_unit, 3.0 AS unit_price
    UNION ALL SELECT 'WP0524' AS qm_code, '条' AS base_unit, 3.0 AS unit_price
    UNION ALL SELECT 'WP0523' AS qm_code, '根' AS base_unit, 35.0 AS unit_price
    UNION ALL SELECT 'WP0516' AS qm_code, '个' AS base_unit, 45.0 AS unit_price
    UNION ALL SELECT 'WP0510' AS qm_code, '根' AS base_unit, 550.0 AS unit_price
    UNION ALL SELECT 'WP0509' AS qm_code, '根' AS base_unit, 36.0 AS unit_price
    UNION ALL SELECT 'WP0508' AS qm_code, '根' AS base_unit, 8.0 AS unit_price
    UNION ALL SELECT 'WP0505' AS qm_code, '个' AS base_unit, 0.32 AS unit_price
    UNION ALL SELECT 'WP0502' AS qm_code, 'ml' AS base_unit, 0.106 AS unit_price
    UNION ALL SELECT 'WP0500' AS qm_code, '盒' AS base_unit, 1.5 AS unit_price
    UNION ALL SELECT 'WP0497' AS qm_code, '条' AS base_unit, 3.0 AS unit_price
    UNION ALL SELECT 'WP0495' AS qm_code, '条' AS base_unit, 3.0 AS unit_price
    UNION ALL SELECT 'WP0494' AS qm_code, '包' AS base_unit, 20.0 AS unit_price
    UNION ALL SELECT 'WP0492' AS qm_code, 'g' AS base_unit, 0.035 AS unit_price
    UNION ALL SELECT 'WP0491' AS qm_code, '个' AS base_unit, 0.06 AS unit_price
    UNION ALL SELECT 'WP0490' AS qm_code, '包' AS base_unit, 2.833 AS unit_price
    UNION ALL SELECT 'WP0488' AS qm_code, 'ml' AS base_unit, 0.012 AS unit_price
    UNION ALL SELECT 'WP0487' AS qm_code, '副' AS base_unit, 0.7 AS unit_price
    UNION ALL SELECT 'WP0778' AS qm_code, '张' AS base_unit, 0.16 AS unit_price
    UNION ALL SELECT 'WP0764' AS qm_code, '张' AS base_unit, 0.02 AS unit_price
    UNION ALL SELECT 'WP0752' AS qm_code, '卷' AS base_unit, 22.0 AS unit_price
    UNION ALL SELECT 'WP0735' AS qm_code, '张' AS base_unit, 0.16 AS unit_price
    UNION ALL SELECT 'WP0693' AS qm_code, '个' AS base_unit, 4.0 AS unit_price
    UNION ALL SELECT 'WP0676' AS qm_code, '张' AS base_unit, 0.16 AS unit_price
    UNION ALL SELECT 'WP0647' AS qm_code, 'g' AS base_unit, 0.01 AS unit_price
    UNION ALL SELECT 'WP0646' AS qm_code, 'g' AS base_unit, 0.03 AS unit_price
    UNION ALL SELECT 'WP0608' AS qm_code, '个' AS base_unit, 3.0 AS unit_price
    UNION ALL SELECT 'WP0579' AS qm_code, '个' AS base_unit, 0.36 AS unit_price
    UNION ALL SELECT 'WP0575' AS qm_code, 'g' AS base_unit, 0.01 AS unit_price
    UNION ALL SELECT 'WP0564' AS qm_code, 'g' AS base_unit, 0.016 AS unit_price
    UNION ALL SELECT 'WP0562' AS qm_code, 'g' AS base_unit, 0.011 AS unit_price
    UNION ALL SELECT 'WP0561' AS qm_code, 'g' AS base_unit, 0.015 AS unit_price
    UNION ALL SELECT 'WP0559' AS qm_code, 'g' AS base_unit, 0.014 AS unit_price
    UNION ALL SELECT 'WP0558' AS qm_code, 'g' AS base_unit, 0.009 AS unit_price
    UNION ALL SELECT 'WP0548' AS qm_code, 'g' AS base_unit, 0.01 AS unit_price
    UNION ALL SELECT 'WP0526' AS qm_code, '卷' AS base_unit, 1.5 AS unit_price
    UNION ALL SELECT 'WP0499' AS qm_code, '张' AS base_unit, 0.036 AS unit_price
    UNION ALL SELECT 'WP0486' AS qm_code, '卷' AS base_unit, 5.0 AS unit_price
    UNION ALL SELECT 'WP0399' AS qm_code, '张' AS base_unit, 0.1 AS unit_price
    UNION ALL SELECT 'WP0396' AS qm_code, '张' AS base_unit, 0.1 AS unit_price
    UNION ALL SELECT 'WP0395' AS qm_code, '张' AS base_unit, 0.2 AS unit_price
    UNION ALL SELECT 'WP0388' AS qm_code, '张' AS base_unit, 0.1 AS unit_price
    UNION ALL SELECT 'WP0332' AS qm_code, '张' AS base_unit, 0.24 AS unit_price
    UNION ALL SELECT 'WP0944' AS qm_code, 'g' AS base_unit, 0.08 AS unit_price
) AS src
WHERE src.unit_price >= 0
  AND NOT EXISTS (
    SELECT 1 FROM material_inventory_rule r WHERE r.material_id = src.qm_code AND r.del_flag = 0
  )
  AND EXISTS (
    SELECT 1 FROM material m WHERE m.qm_code = src.qm_code AND m.del_flag = 0
  );

SELECT ROW_COUNT() AS 新增规则行数;
