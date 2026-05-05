package com.axin.axinagent.agent;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.axin.axinagent.agent.model.AgentState;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 支持工具调用的 ReAct 代理，具体实现了 think 和 act 方法。
 * 自维护消息上下文，禁用 Spring AI 内置的自动工具调用机制。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class ToolCallAgent extends ReActAgent {

    private static final String TERMINATE_TOOL_NAME = "doTerminate";

    private final ToolCallback[] availableTools;
    private final ToolCallingManager toolCallingManager;

    private final ChatOptions chatOptions;

    private ChatResponse toolCallChatResponse;

    public ToolCallAgent(ToolCallback[] availableTools) {
        super();
        this.availableTools = availableTools;
        this.toolCallingManager = ToolCallingManager.builder().build();
        this.chatOptions = DashScopeChatOptions.builder()
                .internalToolExecutionEnabled(false)
                .build();
    }

    /**
     * 调用 LLM 进行推理，判断是否需要执行工具调用。
     *
     * @return true 表示 LLM 决定调用工具，false 表示直接返回结果
     */
    @Override
    public boolean think() {
        if (getNextStepPrompt() != null && !getNextStepPrompt().isEmpty()) {
            getMessageList().add(new UserMessage(getNextStepPrompt()));
        }

        Prompt prompt = new Prompt(getMessageList(), chatOptions);
        try {
            ChatResponse chatResponse = getChatClient().prompt(prompt)
                    .system(getSystemPrompt())
                    .toolCallbacks(availableTools)
                    .call()
                    .chatResponse();

            this.toolCallChatResponse = chatResponse;
	        AssistantMessage assistantMessage =null;
	        if (chatResponse != null) {
		        assistantMessage = chatResponse.getResult().getOutput();
	        }
	        List<AssistantMessage.ToolCall> toolCallList =null;
	        if (assistantMessage != null) {
		        toolCallList=assistantMessage.getToolCalls();
	        }

	        if (assistantMessage != null) {
		        log.info("{} 的思考: {}", getName(), truncate(assistantMessage.getText()));
	        }
	        if (toolCallList != null) {
		        log.info("{} 选择了 {} 个工具", getName(), toolCallList.size());
	        }

	        if (toolCallList != null && !toolCallList.isEmpty()) {
		        String toolCallInfo = toolCallList.stream()
				        .map(tc -> String.format("工具名称：%s，参数：%s", tc.name(), tc.arguments()))
				        .collect(Collectors.joining("\n"));
		        log.info(toolCallInfo);
		        return true;
	        }
	        getMessageList().add(assistantMessage);
	        return false;
        } catch (Exception e) {
            log.error("{} 的思考过程遇到了问题: {}", getName(), e.getMessage());
            getMessageList().add(new AssistantMessage("处理时遇到错误: " + e.getMessage()));
            return false;
        }
    }

    /**
     * 执行 LLM 决定的工具调用并处理结果。
     *
     * @return 工具执行结果摘要
     */
    @Override
    public String act() {
        if (!toolCallChatResponse.hasToolCalls()) {
            return "没有工具调用";
        }

        try {
            Prompt prompt = new Prompt(getMessageList(), chatOptions);
            ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, toolCallChatResponse);

            setMessageList(toolExecutionResult.conversationHistory());

            ToolResponseMessage toolResponseMessage =
                    (ToolResponseMessage) CollUtil.getLast(toolExecutionResult.conversationHistory());

            String results = toolResponseMessage.getResponses().stream()
                    .map(r -> "工具 " + r.name() + " 完成了它的任务！结果: " + r.responseData())
                    .collect(Collectors.joining("\n"));

            boolean terminated = toolResponseMessage.getResponses().stream()
                    .anyMatch(r -> TERMINATE_TOOL_NAME.equals(r.name()));
            if (terminated) {
                setState(AgentState.FINISHED);
            }

            log.info(results);
            return results;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        if (text.length() <= 200) {
            return text;
        }
        return text.substring(0, 200);
    }
}
