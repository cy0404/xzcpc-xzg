-- ============================================================
-- 督导门店访问权限映射表
-- 替代 store_info.supervisor_name 的硬过滤，支持 1:N 映射
-- 督导领导 openId 可映射全部门店，普通督导映射各自门店
-- ============================================================

-- 0) 统一 collation（解决 JOIN 时 utf8mb4_unicode_ci vs utf8mb4_0900_ai_ci 冲突）
ALTER TABLE admin_permission CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE store_info CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE employee CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE owner_registration CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS supervisor_store_access (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    open_id     VARCHAR(100)  NOT NULL COMMENT '关联 admin_permission.open_id',
    admin_name  VARCHAR(100)  NOT NULL COMMENT '督导/管理员姓名（冗余）',
    store_id    VARCHAR(50)   NOT NULL COMMENT '可访问的门店ID',
    store_name  VARCHAR(200)  DEFAULT NULL COMMENT '门店名称（冗余）',
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    del_flag    INT           DEFAULT 0 COMMENT '删除标记',
    UNIQUE INDEX uk_openid_store (open_id, store_id),
    INDEX idx_open_id (open_id),
    INDEX idx_store_id (store_id)
) COMMENT '督导门店访问权限映射' ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================
-- 初始化：从 store_info.supervisor_name 同步已有督导的门店映射
-- 最后一条 SELECT 列出未匹配的督导（需手动确认 openId 后补齐）
-- ============================================================

-- 1) 将 store_info 中的督导通过 admin_permission.name 匹配 openId
INSERT INTO supervisor_store_access (open_id, admin_name, store_id, store_name)
SELECT ap.open_id, ap.name, s.store_id, s.store_name
FROM store_info s
JOIN admin_permission ap ON ap.name = s.supervisor_name AND ap.del_flag = 0
WHERE s.del_flag = 0 AND s.supervisor_name IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM supervisor_store_access a
    WHERE a.open_id = ap.open_id AND a.store_id = s.store_id
  );

-- 2) 列出 store_info 中有督导但 admin_permission 中找不到的（需手动处理）
SELECT s.supervisor_name, COUNT(*) AS store_count
FROM store_info s
WHERE s.del_flag = 0 AND s.supervisor_name IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM admin_permission ap
    WHERE ap.name = s.supervisor_name AND ap.del_flag = 0
  )
GROUP BY s.supervisor_name;

-- 3) 校验各 openId 的门店数
SELECT a.open_id, a.admin_name, COUNT(*) AS store_count
FROM supervisor_store_access a
GROUP BY a.open_id, a.admin_name
ORDER BY store_count DESC;
