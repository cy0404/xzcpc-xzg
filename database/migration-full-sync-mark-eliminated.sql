-- ============================================================
-- 全量同步后：淘汰品类状态归一（del_flag=2 → 1）
-- 前置：① 先执行 backup-material-tables-20260829.sql 备份物料三表
--       ② 再跑全量 sync（APP_SYNC_INSERT_NEW=true + APP_SYNC_DELETE_ABSENT=true）
-- 说明：del_flag 约定 0=有效 1=删除（@TableLogic delval=1）；2=人工"淘汰"标记。
--       sync 的复活逻辑只处理 del_flag=1，del_flag=2 永不复活（走存量分支只补字段），
--       所以全量同步后仍为 2 的 = 接口确实已淘汰的品类，统一归一为 1。
-- 用法：在生产库（store_inventory）执行
-- ============================================================

-- 1) 预览：将被归一的淘汰品类数量（应为全量 sync 后残留的 del_flag=2 行数）
SELECT COUNT(*) AS will_mark FROM material WHERE del_flag = 2;

-- 2) 执行：淘汰品类状态改为 1（系统标准删除标记）
UPDATE material SET del_flag = 1 WHERE del_flag = 2;

-- 3) 验证：应无 del_flag=2 残留，且 0/1 分布符合预期
SELECT COUNT(*) AS remain_2 FROM material WHERE del_flag = 2;
SELECT del_flag, COUNT(*) AS cnt FROM material GROUP BY del_flag ORDER BY del_flag;

-- 如需恢复：见 backup-material-tables-20260829.sql 尾部恢复 SQL
