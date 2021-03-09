DROP DATABASE IF EXISTS `web_message_center`;
CREATE DATABASE `web_message_center` CHARACTER SET 'utf8mb4' COLLATE 'utf8mb4_general_ci';

USE `web_message_center`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for basic_config
-- ----------------------------
DROP TABLE IF EXISTS `basic_config`;
CREATE TABLE `basic_config`  (
  `id` bigint(11) NOT NULL AUTO_INCREMENT,
  `name_server_addr` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `access_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `secret_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for consumer_config
-- ----------------------------
DROP TABLE IF EXISTS `consumer_config`;
CREATE TABLE `consumer_config`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `consumer_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '消费者名称（此消息的名称）唯一',
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '功能描述',
  `topic` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `instance_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'MQ实例名称',
  `group_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '消费组名称',
  `tag` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `module_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '实际被消费者调用的处理服务',
  `process_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '实际被消费者调用的处理服务url',
  `is_inner_processor` tinyint(1) NULL DEFAULT NULL COMMENT '是否是微服务间的内部调用1-内部调用 0-外部调用',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `index_consumer_config_topic`(`topic`) USING BTREE,
  INDEX `index_consumer_config_group_id`(`group_id`) USING BTREE,
  INDEX `index_consumer_config_tag`(`tag`) USING BTREE,
  INDEX `index_consumer_config_module_name`(`module_name`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '消费者配置表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for dead_message
-- ----------------------------
DROP TABLE IF EXISTS `dead_message`;
CREATE TABLE `dead_message`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `mq_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `consumer_config_id` bigint(20) NULL DEFAULT NULL,
  `message` json NULL COMMENT '消息内容JSON String',
  `dead_time` timestamp(0) NULL DEFAULT NULL COMMENT '送入死信表的时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `dead_message_consumer_config_id_key`(`consumer_config_id`) USING BTREE,
  CONSTRAINT `dead_message_consumer_config_id_key` FOREIGN KEY (`consumer_config_id`) REFERENCES `consumer_config` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for failed_message
-- ----------------------------
DROP TABLE IF EXISTS `failed_message`;
CREATE TABLE `failed_message`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `mq_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'rocketMQID',
  `consumer_config_id` bigint(20) NULL DEFAULT NULL COMMENT 'consumer_config表id',
  `message` json NULL COMMENT '消息内容json',
  `create_time` timestamp(0) NULL DEFAULT NULL COMMENT '创建时间',
  `retry_times` int(8) NULL DEFAULT NULL COMMENT '重试次数',
  `next_retry_time` timestamp(0) NULL DEFAULT NULL COMMENT '下一次预计启动时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `failed_message_consumer_config_id_key`(`consumer_config_id`) USING BTREE,
  CONSTRAINT `failed_message_consumer_config_id_key` FOREIGN KEY (`consumer_config_id`) REFERENCES `consumer_config` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;

