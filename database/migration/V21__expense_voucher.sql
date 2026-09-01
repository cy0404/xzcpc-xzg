-- V21: expense_voucher 凭证子表（一单多张，最多9张）
-- 背景：支出登记支持多张凭证（发票/收据/付款截图/现场照片），主表 voucher_url 冗余首张，报表契约不变。
-- 兼容：expense_id 存 EXP... / SPM... 业务号（VARCHAR(50) 够用）。

CREATE TABLE IF NOT EXISTS expense_voucher (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    expense_id  VARCHAR(50)  NOT NULL COMMENT '支出业务ID（EXP... 或 SPM...）',
    voucher_url VARCHAR(500) NOT NULL COMMENT '凭证图片URL',
    sort_no     INT DEFAULT 0 COMMENT '展示顺序',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag    INT DEFAULT 0,
    INDEX idx_expense_id (expense_id)
) COMMENT '支出凭证子表（一单多张，主表 voucher_url 冗余首张）';
