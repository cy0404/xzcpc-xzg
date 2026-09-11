-- =============================================================
-- 修复 2026-09-10 督导同步误建重复门店
-- 现象：43 家老店被"重建"（新记录同群 chat_id），issue 回调 getStoreByChatId 抛 TooManyResults 500
-- 原因：store_code 被企迈门店同步改写为 mendianxinxi-*，督导同步按「接口数字 id ↔ store_code」匹配不上
--       → 当新店重建，并从接口带回同一飞书群 chat_id
-- 处理：把本次误建的 43 条「幽灵店」（无任何 task/expense/loss 业务数据）软删，
--       并软删挂在幽灵店上的 40 条督导关系（老店各自的关系不受影响）
-- 幂等：WHERE del_flag = 0 保护，重复执行无副作用
-- ⚠️ 执行顺序：先在 server 发版「名称兜底匹配 + 建店同名防重」修复 → 再执行本脚本 → 再重跑督导同步
--       修复未生效前重跑督导同步会再次建出幽灵店
-- =============================================================

-- ① 预览：待软删的 43 条幽灵店（应全部创建于 2026-09-10 09:38 前后）
SELECT s.store_id, s.store_name, s.chat_id, s.created_at
FROM store_info s
WHERE s.del_flag = 0 AND s.store_id IN (
  'cmc4hov4sq4zkd16m4lh72j90i','cmhc1ilbvkbakzt9jzerrh0m54','cm5cg8sc82w3zazgbbiruqib28',
  'cm1pq072vfnrp2eq8ndfkvz7r0','cmlefs1sz4uuq9ihfmv6zg3fsi','cmncn6msgnpurzhky06s4vi1rm',
  'cms7je9stb15l7zavrp2c4mvmm','cmoqs6yvp1q6ewkqvmtfm5bafd','cmvpl4l7svba1ndwqlhogcc8co',
  'cm3s2zowfb0vzvcg11yue1uu0d','cmewpkt03azmwdrkkbxyu4jqfs','cmo3q7dfyp4nz33qz69avvg9t0',
  'cmp6n8onc2jdrjcj1i57coaqw7','cmisiicqaqtlv8nfaz4swfpil6','cmsdre1chnhafvn1pp72ha5eui',
  'cmwc7qbjx7ra0aanhutmv2eghr','cm5hzrdpocawkltxaduw2uziz0','cmfetxlxl4q9j3x0iq1xengg1r',
  'cmv7lena2apn08q1ivs5ik4nav','cmy5igsar9t6neafawymvmz7xh','cm4xsugxb9039xpqf0wkj7kow7',
  'cmdkcw4p9rt5ifiwbjuac91zfr','cm7rqpmp7r143mv2h5a3u0gjtw','cm6gm3m9drs4o6cvmrxvxfr3zv',
  'cm5j95ybif31lofzpirc9b3eya','cm9ofienjpdo4f2tqrv7ohrvpw','cmr6xvl5idakoc4f1yvvarf1ia',
  'cmyhm5dnoyyv96dat3lejqitpm','cm7vzfjvolfasqhzm3k4t8l5so','cmz7a4cwveftyo8r22i3ztee4w',
  'cmjk7qhnxlxphefc9gclakvi24','cmn2acb3urtwfji9a9xjsqjc37','cmyf457ogytb6j1ercxd4jpeax',
  'cmici80pv0sf5w7nd6lj9dhy5y','cm0nwwehh4byni6ogzquslw11o','cmqic817tz5153qfbn129wvy4b',
  'cmaq9xyu6ff63k08fp8q4mteb9','cm33stpn87te6uv08jqc9buj68','cml3glc28zwkvuow40am3wzx7t',
  'cmmrilhpdfuj1on2w9m6vy5zi0','cmmtrplorm92hm8z1is335katb','cmg2soc92hemuldo6xdwwfwzu3',
  'cmls9jkslbaik2n22p24qmxaxx'
);

-- ② 软删幽灵门店
UPDATE store_info SET del_flag = 1, updated_at = NOW()
WHERE del_flag = 0 AND store_id IN (
  'cmc4hov4sq4zkd16m4lh72j90i','cmhc1ilbvkbakzt9jzerrh0m54','cm5cg8sc82w3zazgbbiruqib28',
  'cm1pq072vfnrp2eq8ndfkvz7r0','cmlefs1sz4uuq9ihfmv6zg3fsi','cmncn6msgnpurzhky06s4vi1rm',
  'cms7je9stb15l7zavrp2c4mvmm','cmoqs6yvp1q6ewkqvmtfm5bafd','cmvpl4l7svba1ndwqlhogcc8co',
  'cm3s2zowfb0vzvcg11yue1uu0d','cmewpkt03azmwdrkkbxyu4jqfs','cmo3q7dfyp4nz33qz69avvg9t0',
  'cmp6n8onc2jdrjcj1i57coaqw7','cmisiicqaqtlv8nfaz4swfpil6','cmsdre1chnhafvn1pp72ha5eui',
  'cmwc7qbjx7ra0aanhutmv2eghr','cm5hzrdpocawkltxaduw2uziz0','cmfetxlxl4q9j3x0iq1xengg1r',
  'cmv7lena2apn08q1ivs5ik4nav','cmy5igsar9t6neafawymvmz7xh','cm4xsugxb9039xpqf0wkj7kow7',
  'cmdkcw4p9rt5ifiwbjuac91zfr','cm7rqpmp7r143mv2h5a3u0gjtw','cm6gm3m9drs4o6cvmrxvxfr3zv',
  'cm5j95ybif31lofzpirc9b3eya','cm9ofienjpdo4f2tqrv7ohrvpw','cmr6xvl5idakoc4f1yvvarf1ia',
  'cmyhm5dnoyyv96dat3lejqitpm','cm7vzfjvolfasqhzm3k4t8l5so','cmz7a4cwveftyo8r22i3ztee4w',
  'cmjk7qhnxlxphefc9gclakvi24','cmn2acb3urtwfji9a9xjsqjc37','cmyf457ogytb6j1ercxd4jpeax',
  'cmici80pv0sf5w7nd6lj9dhy5y','cm0nwwehh4byni6ogzquslw11o','cmqic817tz5153qfbn129wvy4b',
  'cmaq9xyu6ff63k08fp8q4mteb9','cm33stpn87te6uv08jqc9buj68','cml3glc28zwkvuow40am3wzx7t',
  'cmmrilhpdfuj1on2w9m6vy5zi0','cmmtrplorm92hm8z1is335katb','cmg2soc92hemuldo6xdwwfwzu3',
  'cmls9jkslbaik2n22p24qmxaxx'
);

-- ③ 软删挂在幽灵店上的督导关系（约 40 条；老店的关系行不受影响）
UPDATE supervisor_store_access SET del_flag = 1
WHERE del_flag = 0 AND store_id IN (
  'cmc4hov4sq4zkd16m4lh72j90i','cmhc1ilbvkbakzt9jzerrh0m54','cm5cg8sc82w3zazgbbiruqib28',
  'cm1pq072vfnrp2eq8ndfkvz7r0','cmlefs1sz4uuq9ihfmv6zg3fsi','cmncn6msgnpurzhky06s4vi1rm',
  'cms7je9stb15l7zavrp2c4mvmm','cmoqs6yvp1q6ewkqvmtfm5bafd','cmvpl4l7svba1ndwqlhogcc8co',
  'cm3s2zowfb0vzvcg11yue1uu0d','cmewpkt03azmwdrkkbxyu4jqfs','cmo3q7dfyp4nz33qz69avvg9t0',
  'cmp6n8onc2jdrjcj1i57coaqw7','cmisiicqaqtlv8nfaz4swfpil6','cmsdre1chnhafvn1pp72ha5eui',
  'cmwc7qbjx7ra0aanhutmv2eghr','cm5hzrdpocawkltxaduw2uziz0','cmfetxlxl4q9j3x0iq1xengg1r',
  'cmv7lena2apn08q1ivs5ik4nav','cmy5igsar9t6neafawymvmz7xh','cm4xsugxb9039xpqf0wkj7kow7',
  'cmdkcw4p9rt5ifiwbjuac91zfr','cm7rqpmp7r143mv2h5a3u0gjtw','cm6gm3m9drs4o6cvmrxvxfr3zv',
  'cm5j95ybif31lofzpirc9b3eya','cm9ofienjpdo4f2tqrv7ohrvpw','cmr6xvl5idakoc4f1yvvarf1ia',
  'cmyhm5dnoyyv96dat3lejqitpm','cm7vzfjvolfasqhzm3k4t8l5so','cmz7a4cwveftyo8r22i3ztee4w',
  'cmjk7qhnxlxphefc9gclakvi24','cmn2acb3urtwfji9a9xjsqjc37','cmyf457ogytb6j1ercxd4jpeax',
  'cmici80pv0sf5w7nd6lj9dhy5y','cm0nwwehh4byni6ogzquslw11o','cmqic817tz5153qfbn129wvy4b',
  'cmaq9xyu6ff63k08fp8q4mteb9','cm33stpn87te6uv08jqc9buj68','cml3glc28zwkvuow40am3wzx7t',
  'cmmrilhpdfuj1on2w9m6vy5zi0','cmmtrplorm92hm8z1is335katb','cmg2soc92hemuldo6xdwwfwzu3',
  'cmls9jkslbaik2n22p24qmxaxx'
);

-- ④ 校验一：应返回 0 行（不再有同群绑多店；广南店那组见下方说明）
SELECT chat_id, COUNT(*) AS c
FROM store_info
WHERE del_flag = 0 AND chat_id IS NOT NULL AND chat_id <> ''
GROUP BY chat_id HAVING c > 1;

-- ⑤ 校验二：43 条幽灵店应全部已软删（del_flag=1 → 返回 0）
SELECT COUNT(*) FROM store_info
WHERE del_flag = 0 AND store_id IN (
  'cmc4hov4sq4zkd16m4lh72j90i','cmhc1ilbvkbakzt9jzerrh0m54','cm5cg8sc82w3zazgbbiruqib28',
  'cm1pq072vfnrp2eq8ndfkvz7r0','cmlefs1sz4uuq9ihfmv6zg3fsi','cmncn6msgnpurzhky06s4vi1rm',
  'cms7je9stb15l7zavrp2c4mvmm','cmoqs6yvp1q6ewkqvmtfm5bafd','cmvpl4l7svba1ndwqlhogcc8co',
  'cm3s2zowfb0vzvcg11yue1uu0d','cmewpkt03azmwdrkkbxyu4jqfs','cmo3q7dfyp4nz33qz69avvg9t0',
  'cmp6n8onc2jdrjcj1i57coaqw7','cmisiicqaqtlv8nfaz4swfpil6','cmsdre1chnhafvn1pp72ha5eui',
  'cmwc7qbjx7ra0aanhutmv2eghr','cm5hzrdpocawkltxaduw2uziz0','cmfetxlxl4q9j3x0iq1xengg1r',
  'cmv7lena2apn08q1ivs5ik4nav','cmy5igsar9t6neafawymvmz7xh','cm4xsugxb9039xpqf0wkj7kow7',
  'cmdkcw4p9rt5ifiwbjuac91zfr','cm7rqpmp7r143mv2h5a3u0gjtw','cm6gm3m9drs4o6cvmrxvxfr3zv',
  'cm5j95ybif31lofzpirc9b3eya','cm9ofienjpdo4f2tqrv7ohrvpw','cmr6xvl5idakoc4f1yvvarf1ia',
  'cmyhm5dnoyyv96dat3lejqitpm','cm7vzfjvolfasqhzm3k4t8l5so','cmz7a4cwveftyo8r22i3ztee4w',
  'cmjk7qhnxlxphefc9gclakvi24','cmn2acb3urtwfji9a9xjsqjc37','cmyf457ogytb6j1ercxd4jpeax',
  'cmici80pv0sf5w7nd6lj9dhy5y','cm0nwwehh4byni6ogzquslw11o','cmqic817tz5153qfbn129wvy4b',
  'cmaq9xyu6ff63k08fp8q4mteb9','cm33stpn87te6uv08jqc9buj68','cml3glc28zwkvuow40am3wzx7t',
  'cmmrilhpdfuj1on2w9m6vy5zi0','cmmtrplorm92hm8z1is335katb','cmg2soc92hemuldo6xdwwfwzu3',
  'cmls9jkslbaik2n22p24qmxaxx'
);

-- =============================================================
-- ⚠️ 未包含在清理范围内的特殊一组（双方都有业务数据，需人工确认）：
--   象子茶铺茶广南店            store_id=cmpaocuy400ob3pmb6mzv6ppu（2026-06-10 建，13 条业务数据）
--   象子茶铺茶广南店暂停营业     store_id=cmrj0npl707i23pq3pnpuvc0c（2026-07-15 建，2 条业务数据）
--   两者同群 oc_3af29202a5f0f4b3ab71e7597ba75d24。若确认"暂停营业"店不再使用：
--     UPDATE store_info SET del_flag = 1 WHERE store_id = 'cmrj0npl707i23pq3pnpuvc0c';
--     UPDATE supervisor_store_access SET del_flag = 1 WHERE store_id = 'cmrj0npl707i23pq3pnpuvc0c' AND del_flag = 0;
--   若两店都保留，则至少给其中一条清空 chat_id 避免同群双绑：
--     UPDATE store_info SET chat_id = NULL WHERE store_id = 'cmrj0npl707i23pq3pnpuvc0c';
-- =============================================================
