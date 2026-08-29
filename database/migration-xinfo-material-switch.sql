-- ============================================================
-- 物料主数据切换 xinfo API — 历史关联重映射脚本
-- 背景：material 数据源从旧企迈拼音 API 切换到 xinfo API，
--       material_id 变为 xinfo items[].id（数字），qm_code 变为 items[].code
--       （前缀 RM 原料 / WP 半成品 / TJ 调味 / SF 成品）
-- 对应功能：material / material_inventory_rule / material_conversion_rule
--           每日凌晨 3 点由 MaterialSyncJob 全量同步（见 MaterialSyncService）
--
-- ⚠️ 执行顺序（重要）：
--   1. 先部署新代码并调用 POST /api/materials/sync（或等每日 3 点定时），
--      使 material 表写入 xinfo 新物料（qm_code 前缀 RM/WP/TJ/SF）、旧物料 del_flag=1
--   2. 再执行本脚本（生产库 + 测试库各一次）
--   3. ⑤兜底清理内置保护：新集合为空时 SIGNAL 中止，不会误删
--
-- 安全语义：所有按名称重映射均限定"名称在新集合内唯一"，重名物料不参与，
--           由业务后续人工确认（见 ⑥ 未匹配清单）
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- ① 临时表：xinfo 新物料集合（仅保留名称在集合内唯一的行，避免歧义映射）
-- ------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_xinfo_material;
CREATE TEMPORARY TABLE tmp_xinfo_material AS
SELECT m.material_id, m.material_name
FROM material m
WHERE m.del_flag = 0
  AND (m.qm_code LIKE 'RM%' OR m.qm_code LIKE 'WP%'
       OR m.qm_code LIKE 'TJ%' OR m.qm_code LIKE 'SF%')
  AND m.material_name IN (
      SELECT material_name FROM material
      WHERE del_flag = 0
        AND (qm_code LIKE 'RM%' OR qm_code LIKE 'WP%'
             OR qm_code LIKE 'TJ%' OR qm_code LIKE 'SF%')
      GROUP BY material_name HAVING COUNT(*) = 1
  );

-- 校验：新集合数量（应为本次同步入库的物料数；为 0 说明还没跑同步）
SELECT COUNT(*) AS xinfo_new_material_cnt FROM tmp_xinfo_material;

-- ------------------------------------------------------------
-- ② 唯一键冲突预检（4 张带唯一键的表）
--    冲突 = 更新后同一唯一键下出现 >1 行
--          （典型场景：分区/任务里既有旧 id 行，又已存在新 id 行）
--    若有结果输出，先人工合并处理，再继续执行 ③
-- ------------------------------------------------------------
-- 2a. template_zone_material：uk_zone_material (zone_id, material_id)
SELECT u.zone_id AS grp_key, u.material_id, COUNT(*) AS conflict_cnt
FROM (
    SELECT zone_id, material_id FROM template_zone_material
    UNION ALL
    SELECT tzm.zone_id, t.material_id
    FROM template_zone_material tzm
    JOIN tmp_xinfo_material t ON t.material_name = tzm.material_name
    WHERE tzm.del_flag = 0 AND tzm.material_id <> t.material_id
) u GROUP BY u.zone_id, u.material_id HAVING COUNT(*) > 1;

-- 2b. task_zone_material：uk_task_zone_material (task_zone_id, material_id)
SELECT u.task_zone_id AS grp_key, u.material_id, COUNT(*) AS conflict_cnt
FROM (
    SELECT task_zone_id, material_id FROM task_zone_material
    UNION ALL
    SELECT tzm.task_zone_id, t.material_id
    FROM task_zone_material tzm
    JOIN tmp_xinfo_material t ON t.material_name = tzm.material_name
    WHERE tzm.del_flag = 0 AND tzm.material_id <> t.material_id
) u GROUP BY u.task_zone_id, u.material_id HAVING COUNT(*) > 1;

-- 2c. task_material_summary：uk_task_material (task_id, material_id)
SELECT u.task_id AS grp_key, u.material_id, COUNT(*) AS conflict_cnt
FROM (
    SELECT task_id, material_id FROM task_material_summary
    UNION ALL
    SELECT tms.task_id, t.material_id
    FROM task_material_summary tms
    JOIN tmp_xinfo_material t ON t.material_name = tms.material_name
    WHERE tms.del_flag = 0 AND tms.material_id <> t.material_id
) u GROUP BY u.task_id, u.material_id HAVING COUNT(*) > 1;

-- 2d. inventory_difference：uk_task_material (task_id, material_id)
SELECT u.task_id AS grp_key, u.material_id, COUNT(*) AS conflict_cnt
FROM (
    SELECT task_id, material_id FROM inventory_difference
    UNION ALL
    SELECT d.task_id, t.material_id
    FROM inventory_difference d
    JOIN tmp_xinfo_material t ON t.material_name = d.material_name
    WHERE d.del_flag = 0 AND d.material_id <> t.material_id
) u GROUP BY u.task_id, u.material_id HAVING COUNT(*) > 1;

-- ------------------------------------------------------------
-- ③ 按名称重映射 material_id（快照表只改 id，名称/规格/单位快照保持不动）
-- ------------------------------------------------------------
-- 3a. 报损主表（多物料报损 material_name 为空 → 不参与匹配，天然安全）
UPDATE loss_report r
JOIN tmp_xinfo_material t ON t.material_name = r.material_name
SET r.material_id = t.material_id
WHERE r.del_flag = 0 AND r.material_id <> t.material_id;

-- 3b. 报损明细
UPDATE loss_report_item ri
JOIN tmp_xinfo_material t ON t.material_name = ri.material_name
SET ri.material_id = t.material_id
WHERE ri.del_flag = 0 AND ri.material_id <> t.material_id;

-- 3c. 模板分区物料
UPDATE template_zone_material tzm
JOIN tmp_xinfo_material t ON t.material_name = tzm.material_name
SET tzm.material_id = t.material_id
WHERE tzm.del_flag = 0 AND tzm.material_id <> t.material_id;

-- 3d. 任务分区物料
UPDATE task_zone_material tzm
JOIN tmp_xinfo_material t ON t.material_name = tzm.material_name
SET tzm.material_id = t.material_id
WHERE tzm.del_flag = 0 AND tzm.material_id <> t.material_id;

-- 3e. 任务物料汇总
UPDATE task_material_summary tms
JOIN tmp_xinfo_material t ON t.material_name = tms.material_name
SET tms.material_id = t.material_id
WHERE tms.del_flag = 0 AND tms.material_id <> t.material_id;

-- 3f. 盘点差异项
UPDATE inventory_difference d
JOIN tmp_xinfo_material t ON t.material_name = d.material_name
SET d.material_id = t.material_id
WHERE d.del_flag = 0 AND d.material_id <> t.material_id;

-- 3g. 自购食材
UPDATE self_purchase_material s
JOIN tmp_xinfo_material t ON t.material_name = s.material_name
SET s.material_id = t.material_id
WHERE s.del_flag = 0 AND s.material_id <> t.material_id;

-- ------------------------------------------------------------
-- ④ sys_config：牛油果泥物料 ID 更新为新 id
-- ------------------------------------------------------------
UPDATE sys_config sc
SET sc.config_value = (
    SELECT t.material_id FROM tmp_xinfo_material t
    WHERE t.material_name LIKE '%牛油果泥%' LIMIT 1
)
WHERE sc.config_key = 'feishu_avocado_material_id';

-- ------------------------------------------------------------
-- ⑤ 兜底清理（内置保护：xinfo 新集合为空时中止，防止误删）
-- ------------------------------------------------------------
DROP PROCEDURE IF EXISTS proc_xinfo_cleanup;
DELIMITER $$
CREATE PROCEDURE proc_xinfo_cleanup()
BEGIN
    -- 保护：新集合为空 → 说明还没跑同步，中止整个清理
    IF (SELECT COUNT(*) FROM material
        WHERE del_flag = 0
          AND (qm_code LIKE 'RM%' OR qm_code LIKE 'WP%'
               OR qm_code LIKE 'TJ%' OR qm_code LIKE 'SF%')) = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'xinfo 新集合为空：请先执行 POST /api/materials/sync 再运行本脚本';
    END IF;

    -- 5a. 非手工（material_id 非 M% 前缀）且 qm_code 不是 xinfo 前缀的旧物料 → 逻辑删除
    --     （依赖同步已完成：xinfo 物料全部带 RM/WP/TJ/SF 前缀 code）
    UPDATE material m
    SET m.del_flag = 1
    WHERE m.del_flag = 0
      AND m.material_id NOT LIKE 'M%'
      AND (m.qm_code IS NULL
           OR (m.qm_code NOT LIKE 'RM%' AND m.qm_code NOT LIKE 'WP%'
               AND m.qm_code NOT LIKE 'TJ%' AND m.qm_code NOT LIKE 'SF%'));

    -- 5b. 孤儿盘点规则（对应物料不存在或已逻辑删除）→ 逻辑删除
    UPDATE material_inventory_rule r
    LEFT JOIN material m ON m.material_id = r.material_id AND m.del_flag = 0
    SET r.del_flag = 1
    WHERE r.del_flag = 0 AND m.material_id IS NULL;

    -- 5c. 孤儿换算规则（对应盘点规则已删除）→ 逻辑删除
    UPDATE material_conversion_rule c
    LEFT JOIN material_inventory_rule r ON r.rule_id = c.rule_id AND r.del_flag = 0
    SET c.del_flag = 1
    WHERE c.del_flag = 0 AND r.rule_id IS NULL;
END$$
DELIMITER ;
CALL proc_xinfo_cleanup();
DROP PROCEDURE IF EXISTS proc_xinfo_cleanup;

-- ------------------------------------------------------------
-- ⑥ 未匹配清单（验收基线：应全部为空；有残留则业务人工确认）
-- ------------------------------------------------------------
-- 6a. 报损主表仍悬空旧 id
SELECT r.id, r.material_id, r.material_name
FROM loss_report r
LEFT JOIN material m ON m.material_id = r.material_id
WHERE r.del_flag = 0 AND r.material_id IS NOT NULL AND m.material_id IS NULL;

-- 6b. 报损明细仍悬空旧 id
SELECT ri.id, ri.report_id, ri.material_id, ri.material_name
FROM loss_report_item ri
LEFT JOIN material m ON m.material_id = ri.material_id
WHERE ri.del_flag = 0 AND ri.material_id IS NOT NULL AND m.material_id IS NULL;

-- 6c. 模板分区物料仍悬空
SELECT tzm.id, tzm.zone_id, tzm.material_id, tzm.material_name
FROM template_zone_material tzm
LEFT JOIN material m ON m.material_id = tzm.material_id
WHERE tzm.del_flag = 0 AND m.material_id IS NULL;

-- 6d. 任务分区物料仍悬空
SELECT tzm.id, tzm.task_zone_id, tzm.material_id, tzm.material_name
FROM task_zone_material tzm
LEFT JOIN material m ON m.material_id = tzm.material_id
WHERE tzm.del_flag = 0 AND m.material_id IS NULL;

-- 6e. 任务物料汇总仍悬空
SELECT tms.id, tms.task_id, tms.material_id, tms.material_name
FROM task_material_summary tms
LEFT JOIN material m ON m.material_id = tms.material_id
WHERE tms.del_flag = 0 AND m.material_id IS NULL;

-- 6f. 盘点差异项仍悬空
SELECT d.id, d.task_id, d.material_id, d.material_name
FROM inventory_difference d
LEFT JOIN material m ON m.material_id = d.material_id
WHERE d.del_flag = 0 AND m.material_id IS NULL;

-- 6g. 自购食材仍悬空
SELECT s.id, s.material_id, s.material_name
FROM self_purchase_material s
LEFT JOIN material m ON m.material_id = s.material_id
WHERE s.del_flag = 0 AND s.material_id IS NOT NULL AND m.material_id IS NULL;
