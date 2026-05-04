package com.axin.axinagent.observability.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 步骤 Trace 记录。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TraceStepEntry {

    private String taskId;
    private Integer step;
    private String type;
    private Long startTime;
    private Long endTime;
    private Long durationMs;
    private String toolName;
    private Boolean success;
    private String summary;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
}
