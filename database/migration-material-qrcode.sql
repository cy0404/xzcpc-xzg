-- 物料二维码功能，存储图片路径
ALTER TABLE material ADD COLUMN qr_code VARCHAR(500) DEFAULT NULL COMMENT '物料二维码图片路径';
