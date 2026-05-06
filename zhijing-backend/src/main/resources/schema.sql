-- 知境 数据库初始化脚本
CREATE DATABASE IF NOT EXISTS zhijing DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE zhijing;

CREATE TABLE IF NOT EXISTS `user` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `device_id`  VARCHAR(128) NOT NULL COMMENT '设备唯一标识',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_device_id` (`device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE IF NOT EXISTS `sync_record` (
    `id`        BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`   BIGINT        NOT NULL COMMENT '用户ID',
    `data_type` VARCHAR(64)   NOT NULL COMMENT '数据类型：ENTITY / RELATION',
    `data_json` LONGTEXT      NOT NULL COMMENT '同步数据（JSON）',
    `synced_at` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '同步时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='同步记录表';
