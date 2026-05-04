package com.axin.axinagent.task.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * RocketMQ 任务消息体：投递至消息队列的 Agent 任务载体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentTaskMessage implements Serializable {

    /**
     * 任务唯一标识（UUID）
     */
    private String taskId;

    /**
     * 用户原始输入消息
     */
    private String message;

    /**
     * 任务创建时间戳
     */
    private long createTime;

    /**
     * 执行模式：SINGLE（单 Agent，默认）/ MULTI（多 Agent 协作）
     */
    private String mode = "SINGLE";

    public AgentTaskMessage(String taskId, String message, long createTime) {
        this.taskId = taskId;
        this.message = message;
        this.createTime = createTime;
        this.mode = "SINGLE";
    }
}
