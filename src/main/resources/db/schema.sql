CREATE TABLE IF NOT EXISTS `agent_task` (
  `task_id` VARCHAR(64) NOT NULL COMMENT '任务ID',
  `state` VARCHAR(32) NOT NULL COMMENT '任务状态',
  `message` TEXT NULL COMMENT '用户输入消息',
  `mode` VARCHAR(16) NOT NULL DEFAULT 'SINGLE' COMMENT '执行模式 SINGLE/MULTI',
  `error_message` TEXT NULL COMMENT '错误信息',
  `result` LONGTEXT NULL COMMENT '任务最终结果',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`task_id`),
  KEY `idx_update_time` (`update_time`),
  KEY `idx_state_update_time` (`state`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Agent任务表';
