package com.axin.axinagent.observability.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LLM 单次调用记录。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmCallRecord {

    private String taskId;
    private Long callTime;
    private Long durationMs;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private String model;
}
