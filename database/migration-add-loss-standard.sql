-- ============ 物料验收标准 — 建表 ============
-- 2026-08-03: 新增 loss_standard 表，用于存储水果验收标准和视频上传标准
-- 商家端在报损时选择物料后展示对应标准
USE store_inventory;

CREATE TABLE IF NOT EXISTS loss_standard (
  id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
  material_id         VARCHAR(64)  COMMENT '物料ID（水果验收标准按此匹配）',
  material_name       VARCHAR(128) COMMENT '物料名称',
  material_category   VARCHAR(128) COMMENT '物料分类（视频上传标准按此匹配）',
  standard_type       VARCHAR(32)  NOT NULL COMMENT '标准类型：fruit_check=水果验收标准 / video_upload=视频上传标准',
  title               VARCHAR(128) COMMENT '标准标题',
  description         TEXT         COMMENT '文字说明',
  media_urls          VARCHAR(2048) COMMENT '图片/视频URL，逗号分隔',
  sort_no             INT DEFAULT 0 COMMENT '排序号',
  store_id            VARCHAR(64)  COMMENT '门店ID（为空则全门店通用）',
  status              TINYINT DEFAULT 1 COMMENT '1=启用 0=停用',
  created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag            INT DEFAULT 0 COMMENT '删除标记',
  version             INT DEFAULT 0 COMMENT '乐观锁'
) COMMENT '物料验收标准' ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
