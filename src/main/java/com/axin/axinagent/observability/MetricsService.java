package com.axin.axinagent.observability;

import com.axin.axinagent.config.ObservabilityConfig;
import com.axin.axinagent.observability.model.MetricsResult;
import com.axin.axinagent.observability.model.ToolMetrics;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 全局指标统计服务。
 */
@Service
public class MetricsService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public void incrTaskTotal() {
        stringRedisTemplate.opsForHash().increment(ObservabilityConfig.REDIS_METRICS_TASK_KEY, "total", 1L);
    }

    public void incrTaskFinished() {
        stringRedisTemplate.opsForHash().increment(ObservabilityConfig.REDIS_METRICS_TASK_KEY, "finished", 1L);
    }

    public void incrTaskError() {
        stringRedisTemplate.opsForHash().increment(ObservabilityConfig.REDIS_METRICS_TASK_KEY, "error", 1L);
    }

    public void incrTaskCancelled() {
        stringRedisTemplate.opsForHash().increment(ObservabilityConfig.REDIS_METRICS_TASK_KEY, "cancelled", 1L);
    }

    public MetricsResult getMetrics() {
        Map<Object, Object> taskMap = stringRedisTemplate.opsForHash().entries(ObservabilityConfig.REDIS_METRICS_TASK_KEY);

        long total = parseLong(taskMap.get("total"));
        long finished = parseLong(taskMap.get("finished"));
        long error = parseLong(taskMap.get("error"));
        long cancelled = parseLong(taskMap.get("cancelled"));

        double completionRate = total == 0 ? 0D : (finished * 100.0D / total);

        return MetricsResult.builder()
                .total(total)
                .finished(finished)
                .error(error)
                .cancelled(cancelled)
                .completionRate(completionRate)
                .toolMetrics(getAllToolMetrics())
                .p95LatencyMs(calcP95Latency())
                .build();
    }

    private Map<String, ToolMetrics> getAllToolMetrics() {
        Set<String> keys = stringRedisTemplate.keys(ObservabilityConfig.REDIS_METRICS_TOOL_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, ToolMetrics> result = new HashMap<>();
        for (String key : keys) {
            Map<Object, Object> m = stringRedisTemplate.opsForHash().entries(key);
            long total = parseLong(m.get("total"));
            long success = parseLong(m.get("success"));
            long fail = parseLong(m.get("fail"));
            String toolName = key.substring(ObservabilityConfig.REDIS_METRICS_TOOL_PREFIX.length());
            double successRate = total == 0 ? 0D : (success * 100.0D / total);
            result.put(toolName, ToolMetrics.builder()
                    .toolName(toolName)
                    .total(total)
                    .success(success)
                    .fail(fail)
                    .successRate(successRate)
                    .build());
        }
        return result;
    }

    private long calcP95Latency() {
        List<String> rawLatency = stringRedisTemplate.opsForList().range(ObservabilityConfig.REDIS_METRICS_LATENCY_KEY, 0, -1);
        if (rawLatency == null || rawLatency.isEmpty()) {
            return 0L;
        }

        List<Long> latencies = new ArrayList<>(rawLatency.size());
        for (String item : rawLatency) {
            if (item == null || item.isBlank()) {
                continue;
            }
            try {
                latencies.add(Long.parseLong(item));
            } catch (NumberFormatException ignored) {
            }
        }

        if (latencies.isEmpty()) {
            return 0L;
        }

        latencies.sort(Comparator.naturalOrder());
        int n = latencies.size();
        int index = (int) Math.ceil(0.95D * n) - 1;
        index = Math.max(0, Math.min(index, n - 1));
        return latencies.get(index);
    }

    private long parseLong(Object val) {
        if (val == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(val));
        } catch (Exception e) {
            return 0L;
        }
    }
}
