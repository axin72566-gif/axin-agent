package com.axin.axinagent.observability.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 任务完整 Trace 查询结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskTraceResult {

    private String taskId;
    private List<TraceStepEntry> steps;
    private List<LlmCallRecord> llmCalls;
    private Long totalDurationMs;
    private Integer totalTokens;
    private Double estimatedCostYuan;
}
