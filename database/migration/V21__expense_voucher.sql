-- V21: 支出凭证多张：voucher_url 逗号拼接存储（最多9张，单张URL~150字符 → 扩到 2000）
-- 背景：支出登记支持多张凭证（发票/收据/付款截图/现场照片），主表 voucher_url 改为逗号拼接，首张为主凭证。
-- 报表接口/历史消费方只读首张（无需改动契约）；小程序 detail 接口由后端拆分成数组返回。
ALTER TABLE expense_record MODIFY voucher_url VARCHAR(2000) NULL COMMENT '凭证图片URL（多张逗号拼接，首张=主凭证）';
ALTER TABLE self_purchase_material MODIFY voucher_url VARCHAR(2000) NULL COMMENT '凭证图片URL（多张逗号拼接，首张=主凭证）';
