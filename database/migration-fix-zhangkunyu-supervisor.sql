-- ============================================================
-- 修复：张坤玉督导关系未同步（16 家店停留在旧督导"罗正彩"）
--
-- 根因：接口已把 16 家店分配给张坤玉（userId=dbge788d），
--       但 admin_permission 中张坤玉（id=90）的 user_id 为 NULL，
--       督导同步按「接口 userId ↔ admin_permission.user_id」匹配，
--       匹配不上 → 跳过写库 → 本地保留旧 auto 行（罗正彩）。
--
-- 同步附带的显示名精简：name 去掉"实习培训师"前缀和手机号，只留"张坤玉"
--   （同步 upsert 时会用 admin_permission.name 覆盖 ssa.admin_name / store_info.supervisor_name）
--
-- 步骤：
--   1) 补齐 admin_permission.user_id → 恢复每日 2 点自动同步
--   2) 精简 admin_permission.name 为"张坤玉"
--   3) 软删 16 家店现有 auto 行（罗正彩）
--   4) 插入张坤玉的 auto 行（立即生效，幂等防重）
--   5) 更新 store_info.supervisor_name
--
-- 执行：在 store_inventory（生产库）执行
-- 验证：执行后 SELECT ... WHERE store_code IN (16家) 应全部为张坤玉
-- ============================================================

-- ---------- 1) 补齐张坤玉的飞书 user_id（根治每日同步） ----------
UPDATE admin_permission
SET user_id = 'dbge788d'
WHERE id = 90
  AND user_id IS NULL
  AND name LIKE '%张坤玉%';

-- ---------- 2) 显示名精简为"张坤玉" ----------
UPDATE admin_permission
SET name = '张坤玉'
WHERE id = 90
  AND name LIKE '实习培训师 张坤玉%';

-- ---------- 3) 软删 16 家店现有的 auto 督导行（罗正彩） ----------
UPDATE supervisor_store_access s
JOIN store_info si ON s.store_id = si.store_id
SET s.del_flag = 1
WHERE si.store_code IN (193, 190, 199, 241, 221, 207, 189, 172,
                        317, 266, 167, 200, 179, 233, 351, 175)
  AND si.del_flag = 0
  AND s.del_flag = 0
  AND s.source = 'auto';

-- ---------- 4) 插入张坤玉的 auto 行（16 家店，幂等） ----------
INSERT INTO supervisor_store_access (open_id, admin_name, store_id, store_name, source, created_at, del_flag)
SELECT 'ou_f39e493a59b813dfa910ed7ac23026fb',
       '张坤玉',
       si.store_id,
       si.store_name,
       'auto',
       NOW(),
       0
FROM store_info si
WHERE si.store_code IN (193, 190, 199, 241, 221, 207, 189, 172,
                        317, 266, 167, 200, 179, 233, 351, 175)
  AND si.del_flag = 0
  AND NOT EXISTS (
        SELECT 1 FROM supervisor_store_access s2
        WHERE s2.open_id = 'ou_f39e493a59b813dfa910ed7ac23026fb'
          AND s2.store_id = si.store_id
          AND s2.del_flag = 0
      );

-- ---------- 5) 更新 store_info.supervisor_name ----------
UPDATE store_info
SET supervisor_name = '张坤玉'
WHERE store_code IN (193, 190, 199, 241, 221, 207, 189, 172,
                     317, 266, 167, 200, 179, 233, 351, 175)
  AND del_flag = 0;

-- ============================================================
-- 执行后验证（应返回 16 行，全部为张坤玉）：
--   SELECT si.store_code, si.store_name, si.supervisor_name
--   FROM store_info si WHERE si.store_code IN (193,190,199,241,221,207,189,172,317,266,167,200,179,233,351,175)
--   AND si.del_flag = 0 ORDER BY si.store_code;
-- ============================================================
