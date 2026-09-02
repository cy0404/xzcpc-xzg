-- ============================================================
-- 物料图片支持：4 张表加 image_url 列
-- 数据来源：xinfo 接口 imageUrls（JSON 数组字符串）首图 URL（企迈 CDN，images.qmai.cn）
-- 链路：material（同步写入）→ template_zone_material（分派快照）
--       → task_zone_material（任务快照）→ task_material_summary（提交汇总）
-- 执行：测试库 + 生产库各一次；存量回填另跑 migration-backfill-material-image.sql
-- ============================================================

ALTER TABLE material
  ADD COLUMN image_url VARCHAR(500) NULL COMMENT '物料图片（xinfo imageUrls 首图，企迈 CDN）' AFTER qr_code;

ALTER TABLE template_zone_material
  ADD COLUMN image_url VARCHAR(500) NULL COMMENT '物料图片快照（分派物料时从 material 复制）';

ALTER TABLE task_zone_material
  ADD COLUMN image_url VARCHAR(500) NULL COMMENT '物料图片快照（创建任务时从模板复制）';

ALTER TABLE task_material_summary
  ADD COLUMN image_url VARCHAR(500) NULL COMMENT '物料图片快照（提交汇总时从任务快照复制）';
