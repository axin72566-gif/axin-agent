package com.axin.axinagent.agent;

import cn.hutool.core.util.StrUtil;
import com.axin.axinagent.agent.model.AgentState;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 抽象基础代理类，管理代理状态和执行流程。
 * 提供状态转换、消息记忆管理和步骤循环的基础实现，子类须实现 {@link #step()} 方法。
 */
@Data
@Slf4j
public abstract class BaseAgent {

    private String name;
    private String systemPrompt;
    private String nextStepPrompt;

    private AgentState state = AgentState.IDLE;

    private int maxSteps = 10;
    private int currentStep = 0;

    /** 消息列表滑动窗口大小，超过时保留最新的消息（第1条 system/user 消息始终保留）*/
    private int maxMessageWindowSize = 40;

    /** 是否启用截断摘要压缩 */
    private boolean summarizeEnabled = true;

    /** 工作记忆摘要（随窗口截断不断累计更新） */
    private String workingMemorySummary;

    private ChatClient chatClient;

    /** 自维护的会话消息上下文 */
    private List<Message> messageList = new ArrayList<>();

    /**
     * 同步运行代理，返回所有步骤结果的拼接字符串。
     *
     * @param userPrompt 用户提示词
     * @return 各步骤执行结果
     */
    public String run(String userPrompt) {
        validateBeforeRun(userPrompt);

        state = AgentState.RUNNING;
        messageList.add(new UserMessage(userPrompt));
        List<String> results = new ArrayList<>();
        try {
            for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                currentStep = i + 1;
                log.info("Executing step {}/{}", currentStep, maxSteps);
                trimMessageList();
                results.add("Step " + currentStep + ": " + step());
            }
            if (state != AgentState.FINISHED) {
                state = AgentState.FINISHED;
                results.add("Terminated: Reached max steps (" + maxSteps + ")");
            }
            return String.join("\n", results);
        } catch (Exception e) {
            state = AgentState.ERROR;
            log.error("Error executing agent", e);
            return "执行错误: " + e.getMessage();
        } finally {
            cleanup();
        }
    }

    /**
     * 异步流式运行代理，通过 SSE 逐步推送每个步骤结果。
     *
     * @param userPrompt 用户提示词
     * @return SseEmitter 实例（5分钟超时）
     */
    public SseEmitter runStream(String userPrompt) {
        SseEmitter emitter = new SseEmitter(300_000L);

        emitter.onTimeout(() -> {
            state = AgentState.ERROR;
            log.warn("SSE 连接超时");
            emitter.complete();
        });

        emitter.onCompletion(() -> {
            if (state == AgentState.RUNNING) {
                state = AgentState.FINISHED;
            }
            log.info("SSE 连接已关闭");
        });

        CompletableFuture.runAsync(() -> {
            try {
                validateBeforeRun(userPrompt);
                state = AgentState.RUNNING;
                messageList.add(new UserMessage(userPrompt));

                for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                    currentStep = i + 1;
                    log.info("Executing step {}/{}", currentStep, maxSteps);
                    trimMessageList();
                    sendSseMessage(emitter, "Step " + currentStep + ": " + step());
                }

                if (state != AgentState.FINISHED) {
                    state = AgentState.FINISHED;
                    sendSseMessage(emitter, "执行结束: 达到最大步骤 (" + maxSteps + ")");
                }
                emitter.complete();
            } catch (Exception e) {
                state = AgentState.ERROR;
                log.error("执行智能体失败", e);
                try {
                    sendSseMessage(emitter, "执行错误: " + e.getMessage());
                    emitter.complete();
                } catch (Exception ex) {
                    emitter.completeWithError(ex);
                }
            } finally {
                cleanup();
            }
        });

        return emitter;
    }

    /**
     * 执行单个步骤，由子类实现具体逻辑。
     *
     * @return 步骤执行结果
     */
    public abstract String step();

    /**
     * 运行前校验，状态非 IDLE 或提示词为空时抛出异常。
     */
    private void validateBeforeRun(String userPrompt) {
        if (state != AgentState.IDLE) {
            throw new IllegalStateException("Cannot run agent from state: " + state);
        }
        if (StrUtil.isBlank(userPrompt)) {
            throw new IllegalArgumentException("Cannot run agent with empty user prompt");
        }
    }

    /** 发送标准 SSE 格式消息 */
    private void sendSseMessage(SseEmitter emitter, String message) throws IOException {
        emitter.send(SseEmitter.event().data(message));
    }

    /**
     * 滑动窗口截断消息列表：保留第 1 条（初始用户消息）+ 最新消息。
     * 若开启摘要压缩，则会将被截断历史总结为 SystemMessage 注入，避免语义硬丢失。
     */
    private void trimMessageList() {
        if (messageList.size() <= maxMessageWindowSize) {
            return;
        }
        Message firstMessage = messageList.get(0);
        boolean canSummarize = summarizeEnabled && chatClient != null && maxMessageWindowSize > 2;
        int tailSize = canSummarize ? maxMessageWindowSize - 2 : maxMessageWindowSize - 1;
        tailSize = Math.max(tailSize, 1);

        int tailStart = Math.max(1, messageList.size() - tailSize);
        List<Message> removedMessages = new ArrayList<>(messageList.subList(1, tailStart));
        List<Message> tail = new ArrayList<>(messageList.subList(tailStart, messageList.size()));

        messageList.clear();
        messageList.add(firstMessage);

        if (canSummarize && !removedMessages.isEmpty()) {
            String summary = summarizeMessages(removedMessages);
            if (StrUtil.isNotBlank(summary)) {
                workingMemorySummary = mergeSummary(workingMemorySummary, summary);
                messageList.add(new SystemMessage("历史摘要: " + workingMemorySummary));
            }
        }

        messageList.addAll(tail);

        if (messageList.size() > maxMessageWindowSize) {
            List<Message> compact = new ArrayList<>();
            compact.add(messageList.get(0));
            compact.addAll(messageList.subList(messageList.size() - (maxMessageWindowSize - 1), messageList.size()));
            messageList = compact;
        }
        log.info("消息列表已截断，当前保留 {} 条", messageList.size());
    }

    private String summarizeMessages(List<Message> removedMessages) {
        String history = removedMessages.stream()
                .map(m -> m.getMessageType() + ": " + m.getText())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
        if (StrUtil.isBlank(history)) {
            return "";
        }
        try {
            return chatClient.prompt()
                    .system("你是记忆压缩助手。请将给定对话提炼为简短中文摘要，保留事实、结论、待办和约束，控制在120字以内。")
                    .user(history)
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("生成工作记忆摘要失败: {}", e.getMessage());
            return "";
        }
    }

    private String mergeSummary(String oldSummary, String newSummary) {
        if (StrUtil.isBlank(oldSummary)) {
            return newSummary;
        }
        String merged = oldSummary + "；" + newSummary;
        if (merged.length() > 500) {
            return merged.substring(merged.length() - 500);
        }
        return merged;
    }

    /**
     * 清理资源，子类可重写。
     */
    protected void cleanup() {
    }
}
