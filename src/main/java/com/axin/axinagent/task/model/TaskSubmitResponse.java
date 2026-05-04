package com.axin.axinagent.task.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务提交接口响应体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskSubmitResponse {

    /**
     * 任务唯一标识，客户端使用此 ID 订阅进度和查询状态
     */
    private String taskId;

    /**
     * 提示信息
     */
    private String message;
}
