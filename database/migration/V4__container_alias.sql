-- V4: 容器表增加别名字段

ALTER TABLE container_config ADD COLUMN alias VARCHAR(50) DEFAULT NULL COMMENT '别称，胶囊展示用' AFTER container_name;
