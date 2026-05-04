package com.axin.axinagent.observability;

import com.axin.axinagent.config.ObservabilityConfig;
import com.axin.axinagent.observability.model.LlmCallRecord;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Token 成本估算器。
 */
@Component
public class CostCalculator {

    public double estimateCostYuan(int promptTokens, int completionTokens) {
        double inputCost = (promptTokens / 1000.0D) * ObservabilityConfig.INPUT_PRICE_PER_1K;
        double outputCost = (completionTokens / 1000.0D) * ObservabilityConfig.OUTPUT_PRICE_PER_1K;
        return inputCost + outputCost;
    }

    public double estimateCostYuan(List<LlmCallRecord> records) {
        int promptTotal = records.stream()
                .map(LlmCallRecord::getPromptTokens)
                .filter(v -> v != null && v > 0)
                .mapToInt(Integer::intValue)
                .sum();
        int completionTotal = records.stream()
                .map(LlmCallRecord::getCompletionTokens)
                .filter(v -> v != null && v > 0)
                .mapToInt(Integer::intValue)
                .sum();
        return estimateCostYuan(promptTotal, completionTotal);
    }
}
