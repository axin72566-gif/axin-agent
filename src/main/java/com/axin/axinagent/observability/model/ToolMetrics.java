package com.axin.axinagent.observability.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单工具指标。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolMetrics {

    private String toolName;
    private Long total;
    private Long success;
    private Long fail;
    private Double successRate;
}
