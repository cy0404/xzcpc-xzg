DELETE FROM task_material_summary;
DELETE FROM task_zone_material;
DELETE FROM task_zone;
DELETE FROM task;
INSERT INTO template (template_name, status, biz_code) VALUES ('月盘任务模板(临时)', 1, 'TEMP');
UPDATE template SET template_id = LAST_INSERT_ID() WHERE id = LAST_INSERT_ID();
SET @tmpl_id = LAST_INSERT_ID();
INSERT INTO template_zone (template_id, zone_name) VALUES (@tmpl_id, '6月盘点');
INSERT INTO task (store_id, store_name, xiaochengxuid, warehouse_code, template_id, task_name, task_month, deadline, status, created_by, biz_code) VALUES ('cmpaoctij00az3pmbfl6c71mz', '象子茶铺茶翠湖店', '196065', 'MDCK000001', @tmpl_id, '2026年6月月盘', '2026-06', '2026-07-02 23:59:59', 'not_started', '总部', 'TEMP');
UPDATE task SET task_id = LAST_INSERT_ID(), biz_code = CONCAT('TASK', LPAD(LAST_INSERT_ID(), 8, '0')) WHERE id = LAST_INSERT_ID();
SET @task_id = LAST_INSERT_ID();
INSERT INTO task_zone (task_id, zone_name, sort_no, source_type, biz_code) VALUES (@task_id, '6月盘点', 1, 'template', 'TEMP');
UPDATE task_zone SET task_zone_id = LAST_INSERT_ID(), biz_code = CONCAT('TKZ', LPAD(LAST_INSERT_ID(), 8, '0')) WHERE id = LAST_INSERT_ID();
SET @zone_id = LAST_INSERT_ID();
INSERT INTO task_zone_material (task_id, task_zone_id, material_id, material_name, spec, unit, inventory_unit, sort_no, input_qty, input_status, biz_code) VALUES (@task_id, @zone_id, 'cmpdj8km501r63pmixhitwdzs', '冰勃朗', '1kg/12瓶/件', '瓶', 'g', 1, 31860.0, 'entered', 'TEMP'); -- 冰勃朗: 31.86瓶*1000.0=31860.0g
UPDATE task_zone_material SET task_zone_material_id = LAST_INSERT_ID(), biz_code = CONCAT('TZM', LPAD(LAST_INSERT_ID(), 8, '0')) WHERE id = LAST_INSERT_ID();
INSERT INTO task_zone_material (task_id, task_zone_id, material_id, material_name, spec, unit, inventory_unit, sort_no, input_qty, input_status, biz_code) VALUES (@task_id, @zone_id, 'cmpdj8kkp01qm3pmifea0r080', '悦鲜活鲜奶', '950ml/12瓶/件', '瓶', 'ml', 2, 15000.5, 'entered', 'TEMP'); -- 悦鲜活鲜奶: 15.79瓶*950.0=15000.5ml
UPDATE task_zone_material SET task_zone_material_id = LAST_INSERT_ID(), biz_code = CONCAT('TZM', LPAD(LAST_INSERT_ID(), 8, '0')) WHERE id = LAST_INSERT_ID();
INSERT INTO task_zone_material (task_id, task_zone_id, material_id, material_name, spec, unit, inventory_unit, sort_no, input_qty, input_status, biz_code) VALUES (@task_id, @zone_id, 'cmpdj8kw401ud3pmi7jru2nvj', '黑糖糖浆', '2kg/10瓶/件', '瓶', 'g', 3, 5260.0, 'entered', 'TEMP'); -- 黑糖糖浆: 2.63瓶*2000.0=5260.0g
UPDATE task_zone_material SET task_zone_material_id = LAST_INSERT_ID(), biz_code = CONCAT('TZM', LPAD(LAST_INSERT_ID(), 8, '0')) WHERE id = LAST_INSERT_ID();
INSERT INTO task_zone_material (task_id, task_zone_id, material_id, material_name, spec, unit, inventory_unit, sort_no, input_qty, input_status, biz_code) VALUES (@task_id, @zone_id, 'cmpdj8klr01r13pmike5hg2ce', '安佳淡奶油', '1L/瓶', '瓶', 'ml', 4, 4250.0, 'entered', 'TEMP'); -- 安佳淡奶油: 4.25瓶*1000.0=4250.0ml
UPDATE task_zone_material SET task_zone_material_id = LAST_INSERT_ID(), biz_code = CONCAT('TZM', LPAD(LAST_INSERT_ID(), 8, '0')) WHERE id = LAST_INSERT_ID();
INSERT INTO task_zone_material (task_id, task_zone_id, material_id, material_name, spec, unit, inventory_unit, sort_no, input_qty, input_status, biz_code) VALUES (@task_id, @zone_id, 'cmpdj8ksi01sz3pmicehinp5h', '冷冻树番茄', '1kg/瓶', '瓶', 'g', 5, 4600.0, 'entered', 'TEMP'); -- 冷冻树番茄: 4.6瓶*1000.0=4600.0g
UPDATE task_zone_material SET task_zone_material_id = LAST_INSERT_ID(), biz_code = CONCAT('TZM', LPAD(LAST_INSERT_ID(), 8, '0')) WHERE id = LAST_INSERT_ID();
INSERT INTO task_zone_material (task_id, task_zone_id, material_id, material_name, spec, unit, inventory_unit, sort_no, input_qty, input_status, biz_code) VALUES (@task_id, @zone_id, 'cmpdj8ime01033pmiec1kdptn', '速冻柳橙汁', '950ml/瓶', '瓶', 'ml', 6, 2080.5, 'entered', 'TEMP'); -- 速冻柳橙汁: 2.19瓶*950.0=2080.5ml
UPDATE task_zone_material SET task_zone_material_id = LAST_INSERT_ID(), biz_code = CONCAT('TZM', LPAD(LAST_INSERT_ID(), 8, '0')) WHERE id = LAST_INSERT_ID();
INSERT INTO task_zone_material (task_id, task_zone_id, material_id, material_name, spec, unit, inventory_unit, sort_no, input_qty, input_status, biz_code) VALUES (@task_id, @zone_id, 'cmpdj8knt01rq3pmiw3mms5b6', '维生素C', '1kg/包', '包', 'g', 7, 2600.0, 'entered', 'TEMP'); -- 维生素C: 2.6包*1000.0=2600.0g
UPDATE task_zone_material SET task_zone_material_id = LAST_INSERT_ID(), biz_code = CONCAT('TZM', LPAD(LAST_INSERT_ID(), 8, '0')) WHERE id = LAST_INSERT_ID();
INSERT INTO task_zone_material (task_id, task_zone_id, material_id, material_name, spec, unit, inventory_unit, sort_no, input_qty, input_status, biz_code) VALUES (@task_id, @zone_id, 'cmpdj8kne01rl3pmi49o0vtc7', '炒米', '500g/瓶', '瓶', 'g', 8, 500.0, 'entered', 'TEMP'); -- 炒米: 1.0瓶*500.0=500.0g
UPDATE task_zone_material SET task_zone_material_id = LAST_INSERT_ID(), biz_code = CONCAT('TZM', LPAD(LAST_INSERT_ID(), 8, '0')) WHERE id = LAST_INSERT_ID();
INSERT INTO task_zone_material (task_id, task_zone_id, material_id, material_name, spec, unit, inventory_unit, sort_no, input_qty, input_status, biz_code) VALUES (@task_id, @zone_id, 'cmpdj8kmi01rb3pmi3mk9bxfx', '酸奶粉', '1kg/包', '包', 'g', 9, 1944.0, 'entered', 'TEMP'); -- 酸奶粉: 1.944包*1000.0=1944.0g
UPDATE task_zone_material SET task_zone_material_id = LAST_INSERT_ID(), biz_code = CONCAT('TZM', LPAD(LAST_INSERT_ID(), 8, '0')) WHERE id = LAST_INSERT_ID();
INSERT INTO task_zone_material (task_id, task_zone_id, material_id, material_name, spec, unit, inventory_unit, sort_no, input_qty, input_status, biz_code) VALUES (@task_id, @zone_id, 'cmpdj8ku001tj3pmizx5xl24m', '安佳黄油', '227g/盒', '盒', 'g', 10, 2092.94, 'entered', 'TEMP'); -- 安佳黄油: 9.22盒*227.0=2092.94g
UPDATE task_zone_material SET task_zone_material_id = LAST_INSERT_ID(), biz_code = CONCAT('TZM', LPAD(LAST_INSERT_ID(), 8, '0')) WHERE id = LAST_INSERT_ID();
INSERT INTO task_zone_material (task_id, task_zone_id, material_id, material_name, spec, unit, inventory_unit, sort_no, input_qty, input_status, biz_code) VALUES (@task_id, @zone_id, 'cmpdj8kpj01sa3pmih7xe7b0b', '大麦若叶粉', '250g/包', '包', 'g', 11, 1000.0, 'entered', 'TEMP'); -- 大麦若叶粉: 4.0包*250.0=1000.0g
UPDATE task_zone_material SET task_zone_material_id = LAST_INSERT_ID(), biz_code = CONCAT('TZM', LPAD(LAST_INSERT_ID(), 8, '0')) WHERE id = LAST_INSERT_ID();
INSERT INTO task_zone_material (task_id, task_zone_id, material_id, material_name, spec, unit, inventory_unit, sort_no, input_qty, input_status, biz_code) VALUES (@task_id, @zone_id, 'cmpdj8iky00zj3pmi12hb98lb', '洱宝话梅', '160g/包', '包', 'g', 12, 1860.8, 'entered', 'TEMP'); -- 洱宝话梅: 11.63包*160.0=1860.8g
UPDATE task_zone_material SET task_zone_material_id = LAST_INSERT_ID(), biz_code = CONCAT('TZM', LPAD(LAST_INSERT_ID(), 8, '0')) WHERE id = LAST_INSERT_ID();
INSERT INTO task_zone_material (task_id, task_zone_id, material_id, material_name, spec, unit, inventory_unit, sort_no, input_qty, input_status, biz_code) VALUES (@task_id, @zone_id, 'cmpdj8igq00xv3pmi7d642fox', '酸角果泥果酱', '1kg/瓶', '瓶', 'g', 13, 8700.0, 'entered', 'TEMP'); -- 酸角果泥果酱: 8.7瓶*1000.0=8700.0g
UPDATE task_zone_material SET task_zone_material_id = LAST_INSERT_ID(), biz_code = CONCAT('TZM', LPAD(LAST_INSERT_ID(), 8, '0')) WHERE id = LAST_INSERT_ID();
INSERT INTO task_zone_material (task_id, task_zone_id, material_id, material_name, spec, unit, inventory_unit, sort_no, input_qty, input_status, biz_code) VALUES (@task_id, @zone_id, 'cmpdj8kvc01u33pmiu4uyeb07', '山野滇红茶', '500g/包', '包', 'g', 14, 1270.0, 'entered', 'TEMP'); -- 山野滇红茶: 2.54包*500.0=1270.0g
UPDATE task_zone_material SET task_zone_material_id = LAST_INSERT_ID(), biz_code = CONCAT('TZM', LPAD(LAST_INSERT_ID(), 8, '0')) WHERE id = LAST_INSERT_ID();
INSERT INTO task_zone_material (task_id, task_zone_id, material_id, material_name, spec, unit, inventory_unit, sort_no, input_qty, input_status, biz_code) VALUES (@task_id, @zone_id, 'cmpdj8kvq01u83pmianns495x', '普洱茶', '500g/包', '包', 'g', 15, 1950.0, 'entered', 'TEMP'); -- 普洱茶: 3.9包*500.0=1950.0g
UPDATE task_zone_material SET task_zone_material_id = LAST_INSERT_ID(), biz_code = CONCAT('TZM', LPAD(LAST_INSERT_ID(), 8, '0')) WHERE id = LAST_INSERT_ID();
INSERT INTO task_zone_material (task_id, task_zone_id, material_id, material_name, spec, unit, inventory_unit, sort_no, input_qty, input_status, biz_code) VALUES (@task_id, @zone_id, 'cmpdj8l0801vr3pmie62s070l', '纸质小料杯', '50个/捆', '个', '个', 16, 23300.0, 'entered', 'TEMP'); -- 纸质小料杯: 466.0个*50.0=23300.0个
UPDATE task_zone_material SET task_zone_material_id = LAST_INSERT_ID(), biz_code = CONCAT('TZM', LPAD(LAST_INSERT_ID(), 8, '0')) WHERE id = LAST_INSERT_ID();
INSERT INTO task_zone_material (task_id, task_zone_id, material_id, material_name, spec, unit, inventory_unit, sort_no, input_qty, input_status, biz_code) VALUES (@task_id, @zone_id, 'cmpdj8kzh01vh3pmiw3qbsvee', '98PET杯+盖', '1000个/件', '个', '个', 17, 1190000.0, 'entered', 'TEMP'); -- 98PET杯+盖: 1190.0个*1000.0=1190000.0个
UPDATE task_zone_material SET task_zone_material_id = LAST_INSERT_ID(), biz_code = CONCAT('TZM', LPAD(LAST_INSERT_ID(), 8, '0')) WHERE id = LAST_INSERT_ID();
INSERT INTO task_zone_material (task_id, task_zone_id, material_id, material_name, spec, unit, inventory_unit, sort_no, input_qty, input_status, biz_code) VALUES (@task_id, @zone_id, 'cmpdj8l0k01vw3pmir4vcscq9', 'PP500粗吸管', '100根/20包/件', '根', '根', 18, 1317.0, 'entered', 'TEMP'); -- PP500粗吸管: 1317.0根*1=1317.0根
UPDATE task_zone_material SET task_zone_material_id = LAST_INSERT_ID(), biz_code = CONCAT('TZM', LPAD(LAST_INSERT_ID(), 8, '0')) WHERE id = LAST_INSERT_ID();
INSERT INTO task_zone_material (task_id, task_zone_id, material_id, material_name, spec, unit, inventory_unit, sort_no, input_qty, input_status, biz_code) VALUES (@task_id, @zone_id, 'cmpdj8l1z01wg3pmid4jlicdd', '半球杯盖', '1000个/件', '个', '个', 19, 816000.0, 'entered', 'TEMP'); -- 半球杯盖: 816.0个*1000.0=816000.0个
UPDATE task_zone_material SET task_zone_material_id = LAST_INSERT_ID(), biz_code = CONCAT('TZM', LPAD(LAST_INSERT_ID(), 8, '0')) WHERE id = LAST_INSERT_ID();
INSERT INTO task_zone_material (task_id, task_zone_id, material_id, material_name, spec, unit, inventory_unit, sort_no, input_qty, input_status, biz_code) VALUES (@task_id, @zone_id, 'cmpdj8l3o01x53pmizumq9omd', '无纺布四杯袋', '400个/件', '个', '个', 20, 278400.0, 'entered', 'TEMP'); -- 无纺布四杯袋: 696.0个*400.0=278400.0个
UPDATE task_zone_material SET task_zone_material_id = LAST_INSERT_ID(), biz_code = CONCAT('TZM', LPAD(LAST_INSERT_ID(), 8, '0')) WHERE id = LAST_INSERT_ID();
INSERT INTO task_zone_material (task_id, task_zone_id, material_id, material_name, spec, unit, inventory_unit, sort_no, input_qty, input_status, biz_code) VALUES (@task_id, @zone_id, 'cmpdj8jnz01et3pmi9p2zal3l', '一次性防尘帽', '100个/包', '个', '个', 21, 17000.0, 'entered', 'TEMP'); -- 一次性防尘帽: 170.0个*100.0=17000.0个
UPDATE task_zone_material SET task_zone_material_id = LAST_INSERT_ID(), biz_code = CONCAT('TZM', LPAD(LAST_INSERT_ID(), 8, '0')) WHERE id = LAST_INSERT_ID();