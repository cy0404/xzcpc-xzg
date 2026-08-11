-- ============ 释迦果 验收标准 — 测试数据 ============
USE store_inventory;

-- 释迦果 水果验收标准（请替换 material_id 和图片URL）
INSERT INTO loss_standard (material_id, material_name, material_category, standard_type, title, description, media_urls, status)
VALUES (
  'MATERIAL_ID_HERE',   -- TODO: 替换为释迦果的 material_id
  '释迦果',
  '水果蔬菜',            -- TODO: 确认物料分类
  'fruit_check',
  '释迦果验收标准',
  '1. 根部发黑不可用\n2. 发黑且有酵感\n3. 表皮发黑、发霉\n4. 花斑高于50%',
  'IMG_URL_1,IMG_URL_2,IMG_URL_3,IMG_URL_4',  -- TODO: 替换为飞书文档中4张不合格图片的URL
  1
);
