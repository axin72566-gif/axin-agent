package com.axin.axinagent.task.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务状态查询响应 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTaskStatus {

    /**
     * 任务唯一标识
     */
    private String taskId;

    /**
     * 当前状态（AgentState.name()）
     */
    private String state;

    /**
     * 用户原始输入消息
     */
    private String message;

    /**
     * 任务创建时间戳
     */
    private long createTime;

    /**
     * 错误信息（仅 ERROR/FAILED 状态时存在）
     */
    private String errorMessage;
}
