package com.axin.axinagent.infrastructure.db.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 任务持久化实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("agent_task")
public class AgentTaskEntity {

    @TableId(value = "task_id", type = IdType.INPUT)
    private String taskId;

    @TableField("state")
    private String state;

    @TableField("message")
    private String message;

    @TableField("mode")
    private String mode;

    @TableField("error_message")
    private String errorMessage;

    @TableField("result")
    private String result;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
