-- ============================================================
-- 门店类型字段 + 直营店标记（周盘仅直营店参与，2026-08-31）
-- 业务链路：订货周期配置只对直营店开放 → 自动生成/手动创建自然只覆盖直营店
-- 名单依据：企迈控制台督导 CSV（region_name='直营店'，2026-07-31 导出，督导罗正彩）
-- 执行库：store_inventory（正式）/ store_inventory_test（测试）均需执行（在对应库下执行即可，脚本无 USE）
-- 幂等：ALTER 一次性；UPDATE 按 store_id 匹配，可重复执行
-- ============================================================

-- 1) 门店类型列（本地维护，企迈侧变更后重新导出 CSV 更新；新同步门店默认加盟）
ALTER TABLE store_info
    ADD COLUMN store_type VARCHAR(20) NOT NULL DEFAULT 'franchise' COMMENT '门店类型: direct直营|franchise加盟' AFTER store_code;

-- 2) 直营店标记（16 家，来自企迈控制台 region_name='直营店'）
UPDATE store_info SET store_type = 'direct' WHERE store_id = 'cmpaoctij00az3pmbfl6c71mz';  -- 翠湖店
UPDATE store_info SET store_type = 'direct' WHERE store_id = 'cmpaoctko00be3pmbvpeiogtc';  -- 福德店
UPDATE store_info SET store_type = 'direct' WHERE store_id = 'cmpaoctm800bt3pmbdo8azaqf';  -- 南亚风情店
UPDATE store_info SET store_type = 'direct' WHERE store_id = 'cmpaoctmr00by3pmb20hdtxl5';  -- 翰林大观店
UPDATE store_info SET store_type = 'direct' WHERE store_id = 'cmpaoctqy00cx3pmb43nouafn';  -- 高山铺店
UPDATE store_info SET store_type = 'direct' WHERE store_id = 'cmpaoctrs00d23pmb0nygzz46';  -- 仕林街店
UPDATE store_info SET store_type = 'direct' WHERE store_id = 'cmpaoctsq00dc3pmbhjnibzpd';  -- 弥勒金辰店
UPDATE store_info SET store_type = 'direct' WHERE store_id = 'cmpaoctv800e13pmbw3w2ahxu';  -- 新亚洲店
UPDATE store_info SET store_type = 'direct' WHERE store_id = 'cmpaoctvq00e63pmbobapy64w';  -- 呈贡七彩云南第壹城店
UPDATE store_info SET store_type = 'direct' WHERE store_id = 'cmpaoctz100f03pmbuyaf4h73';  -- 弥勒印象街店
UPDATE store_info SET store_type = 'direct' WHERE store_id = 'cmpaocu5500ge3pmb70lbe792';  -- 滇池名门店
UPDATE store_info SET store_type = 'direct' WHERE store_id = 'cmpaocu9f00hi3pmbmpvq836q';  -- 福保店
UPDATE store_info SET store_type = 'direct' WHERE store_id = 'cmpaocud700ih3pmbe6opynv0';  -- 新迎新城店
UPDATE store_info SET store_type = 'direct' WHERE store_id = 'cmpaocun900l93pmbczh07a1b';  -- 万宏国际店
UPDATE store_info SET store_type = 'direct' WHERE store_id = 'cmpwa93tr010v3pq3hzddyxs2';  -- 南屏街世纪广场店
UPDATE store_info SET store_type = 'direct' WHERE store_id = 'cmpwa91ya010f3pq343o6pqxk';  -- 昆明建工新城店

-- 3) 校验（执行后应看到 16 行）
-- SELECT COUNT(*) FROM store_info WHERE store_type = 'direct' AND del_flag = 0;
