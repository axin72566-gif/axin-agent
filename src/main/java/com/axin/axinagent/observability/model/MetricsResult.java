package com.axin.axinagent.observability.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 全局可观测性指标结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricsResult {

    private Long total;
    private Long finished;
    private Long error;
    private Long cancelled;
    private Double completionRate;
    private Map<String, ToolMetrics> toolMetrics;
    private Long p95LatencyMs;
}
