package com.axin.axinagent.controller;

import com.axin.axinagent.common.BaseResponse;
import com.axin.axinagent.common.ResultUtils;
import com.axin.axinagent.observability.AgentTraceService;
import com.axin.axinagent.observability.CostCalculator;
import com.axin.axinagent.observability.MetricsService;
import com.axin.axinagent.observability.model.LlmCallRecord;
import com.axin.axinagent.observability.model.MetricsResult;
import com.axin.axinagent.observability.model.TaskTraceResult;
import com.axin.axinagent.observability.model.TraceStepEntry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 可观测性查询接口。
 */
@RestController
@RequestMapping("/observability")
@Tag(name = "Observability", description = "可观测性查询接口")
public class ObservabilityController {

    @Resource
    private AgentTraceService agentTraceService;

    @Resource
    private MetricsService metricsService;

    @Resource
    private CostCalculator costCalculator;

    @GetMapping("/trace/{taskId}")
    @Operation(summary = "查询任务 Trace", description = "查询指定 taskId 的步骤 Trace 与 LLM 调用明细")
    public BaseResponse<TaskTraceResult> getTaskTrace(@PathVariable String taskId) {
        List<TraceStepEntry> steps = agentTraceService.getStepTrace(taskId);
        List<LlmCallRecord> llmCalls = agentTraceService.getLlmCalls(taskId);

        long totalDurationMs = steps.stream()
                .map(TraceStepEntry::getDurationMs)
                .filter(v -> v != null && v > 0)
                .mapToLong(Long::longValue)
                .sum();

        int totalTokens = llmCalls.stream()
                .map(LlmCallRecord::getTotalTokens)
                .filter(v -> v != null && v > 0)
                .mapToInt(Integer::intValue)
                .sum();

        double estimatedCostYuan = costCalculator.estimateCostYuan(llmCalls);

        TaskTraceResult result = TaskTraceResult.builder()
                .taskId(taskId)
                .steps(steps)
                .llmCalls(llmCalls)
                .totalDurationMs(totalDurationMs)
                .totalTokens(totalTokens)
                .estimatedCostYuan(estimatedCostYuan)
                .build();

        return ResultUtils.success(result);
    }

    @GetMapping("/metrics")
    @Operation(summary = "查询全局 Metrics", description = "查询任务统计、工具调用统计、P95 延迟")
    public BaseResponse<MetricsResult> getMetrics() {
        return ResultUtils.success(metricsService.getMetrics());
    }

    @GetMapping("/cost/{taskId}")
    @Operation(summary = "查询任务 Cost", description = "查询指定 taskId 的 Token 使用与费用估算")
    public BaseResponse<Map<String, Object>> getTaskCost(@PathVariable String taskId) {
        List<LlmCallRecord> llmCalls = agentTraceService.getLlmCalls(taskId);
        int promptTokens = llmCalls.stream()
                .map(LlmCallRecord::getPromptTokens)
                .filter(v -> v != null && v > 0)
                .mapToInt(Integer::intValue)
                .sum();
        int completionTokens = llmCalls.stream()
                .map(LlmCallRecord::getCompletionTokens)
                .filter(v -> v != null && v > 0)
                .mapToInt(Integer::intValue)
                .sum();
        int totalTokens = promptTokens + completionTokens;
        double estimatedCostYuan = costCalculator.estimateCostYuan(promptTokens, completionTokens);

        Map<String, Object> costSummary = new HashMap<>();
        costSummary.put("taskId", taskId);
        costSummary.put("llmCallCount", llmCalls.size());
        costSummary.put("promptTokens", promptTokens);
        costSummary.put("completionTokens", completionTokens);
        costSummary.put("totalTokens", totalTokens);
        costSummary.put("estimatedCostYuan", estimatedCostYuan);

        return ResultUtils.success(costSummary);
    }
}
