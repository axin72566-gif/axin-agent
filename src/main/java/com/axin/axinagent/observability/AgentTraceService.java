package com.axin.axinagent.observability;

import com.axin.axinagent.config.ObservabilityConfig;
import com.axin.axinagent.observability.model.LlmCallRecord;
import com.axin.axinagent.observability.model.TraceStepEntry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Agent Trace 数据写入与读取服务。
 */
@Slf4j
@Service
public class AgentTraceService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ObjectMapper objectMapper;

    public void recordStep(TraceStepEntry traceStepEntry) {
        if (traceStepEntry == null || traceStepEntry.getTaskId() == null) {
            return;
        }
        String key = ObservabilityConfig.REDIS_TRACE_STEP_PREFIX + traceStepEntry.getTaskId();
        pushJsonToList(key, traceStepEntry);
        expireTaskKey(key);
    }

    public void recordLlmCall(LlmCallRecord llmCallRecord) {
        if (llmCallRecord == null || llmCallRecord.getTaskId() == null) {
            return;
        }
        String key = ObservabilityConfig.REDIS_TRACE_LLM_PREFIX + llmCallRecord.getTaskId();
        pushJsonToList(key, llmCallRecord);
        expireTaskKey(key);
    }

    public void recordLatency(long durationMs) {
        stringRedisTemplate.opsForList().rightPush(ObservabilityConfig.REDIS_METRICS_LATENCY_KEY, String.valueOf(durationMs));
    }

    public void recordToolCall(String toolName, boolean success) {
        if (toolName == null || toolName.isBlank()) {
            return;
        }
        String key = ObservabilityConfig.REDIS_METRICS_TOOL_PREFIX + toolName;
        stringRedisTemplate.opsForHash().increment(key, "total", 1L);
        if (success) {
            stringRedisTemplate.opsForHash().increment(key, "success", 1L);
        } else {
            stringRedisTemplate.opsForHash().increment(key, "fail", 1L);
        }
    }

    public List<TraceStepEntry> getStepTrace(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return Collections.emptyList();
        }
        String key = ObservabilityConfig.REDIS_TRACE_STEP_PREFIX + taskId;
        return readList(key, TraceStepEntry.class);
    }

    public List<LlmCallRecord> getLlmCalls(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return Collections.emptyList();
        }
        String key = ObservabilityConfig.REDIS_TRACE_LLM_PREFIX + taskId;
        return readList(key, LlmCallRecord.class);
    }

    private <T> void pushJsonToList(String key, T data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            stringRedisTemplate.opsForList().rightPush(key, json);
        } catch (JsonProcessingException e) {
            log.warn("写入 Trace 数据序列化失败: {}", e.getMessage());
        }
    }

    private <T> List<T> readList(String key, Class<T> clazz) {
        List<String> raw = stringRedisTemplate.opsForList().range(key, 0, -1);
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        List<T> result = new ArrayList<>(raw.size());
        for (String item : raw) {
            if (item == null || item.isBlank()) {
                continue;
            }
            try {
                result.add(objectMapper.readValue(item, clazz));
            } catch (JsonProcessingException e) {
                log.warn("读取 Trace 数据反序列化失败: {}", e.getMessage());
            }
        }
        return result;
    }

    private void expireTaskKey(String key) {
        stringRedisTemplate.expire(key, ObservabilityConfig.REDIS_TTL_SECONDS, TimeUnit.SECONDS);
    }
}
