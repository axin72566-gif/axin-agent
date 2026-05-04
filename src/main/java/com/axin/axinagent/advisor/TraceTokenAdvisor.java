package com.axin.axinagent.advisor;

import com.axin.axinagent.observability.AgentTraceService;
import com.axin.axinagent.observability.TaskContext;
import com.axin.axinagent.observability.model.LlmCallRecord;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * LLM 调用 Trace + Token 统计 Advisor（同步调用）。
 */
@Slf4j
@Component
public class TraceTokenAdvisor implements CallAdvisor {

    @Resource
    private AgentTraceService agentTraceService;

    @NotNull
    @Override
    public ChatClientResponse adviseCall(@NotNull ChatClientRequest chatClientRequest,
                                         @NotNull CallAdvisorChain callAdvisorChain) {
        long start = System.currentTimeMillis();
        ChatClientResponse response = callAdvisorChain.nextCall(chatClientRequest);
        long duration = System.currentTimeMillis() - start;

        agentTraceService.recordLatency(duration);

        String taskId = TaskContext.getTaskId();
        if (taskId == null || taskId.isBlank()) {
            return response;
        }

        try {
            ChatResponse chatResponse = response.chatResponse();
            Object metadata = chatResponse == null ? null : chatResponse.getMetadata();
            Object usage = invokeNoArg(metadata, "getUsage");

            int promptTokens = extractToken(usage, "getPromptTokens", "getInputTokens", "getPromptTokenCount");
            int completionTokens = extractToken(usage, "getCompletionTokens", "getOutputTokens", "getCompletionTokenCount");
            int totalTokens = extractToken(usage, "getTotalTokens", "getTotalTokenCount");
            if (totalTokens <= 0) {
                totalTokens = Math.max(0, promptTokens) + Math.max(0, completionTokens);
            }

            String model = extractString(metadata, "getModel", "getModelName");
            if (model == null || model.isBlank()) {
                model = "unknown";
            }

            LlmCallRecord record = LlmCallRecord.builder()
                    .taskId(taskId)
                    .callTime(start)
                    .durationMs(duration)
                    .promptTokens(Math.max(promptTokens, 0))
                    .completionTokens(Math.max(completionTokens, 0))
                    .totalTokens(Math.max(totalTokens, 0))
                    .model(model)
                    .build();
            agentTraceService.recordLlmCall(record);
        } catch (Exception e) {
            log.warn("TraceTokenAdvisor 记录调用数据失败: {}", e.getMessage());
        }

        return response;
    }

    @NotNull
    @Override
    public String getName() {
        return "TraceTokenAdvisor";
    }

    @Override
    public int getOrder() {
        return 1;
    }

    private int extractToken(Object usage, String... methodNames) {
        if (usage == null) {
            return 0;
        }
        for (String methodName : methodNames) {
            Object value = invokeNoArg(usage, methodName);
            Integer parsed = parseInt(value);
            if (parsed != null) {
                return parsed;
            }
        }
        return 0;
    }

    private String extractString(Object target, String... methodNames) {
        if (target == null) {
            return null;
        }
        for (String methodName : methodNames) {
            Object value = invokeNoArg(target, methodName);
            if (value != null) {
                String s = String.valueOf(value);
                if (!s.isBlank()) {
                    return s;
                }
            }
        }
        return null;
    }

    private Object invokeNoArg(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Exception e) {
            return null;
        }
    }

    private Integer parseInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }
}
