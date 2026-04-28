package com.axin.axinagent.agent;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.axin.axinagent.agent.model.AgentState;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
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

	private static final String TERMINATE_TOOL_NAME = "terminateTool";

	private final ToolCallback[] availableTools;
	private final ToolCallingManager toolCallingManager;

	/** 禁用内置工具调用后使用的 ChatOptions */
	private final ChatOptions chatOptions;

	/** 保存了工具调用信息的最近一次响应，供 act() 使用 */
	private ChatResponse toolCallChatResponse;

	public ToolCallAgent(ToolCallback[] availableTools) {
		super();
		this.availableTools = availableTools;
		this.toolCallingManager = ToolCallingManager.builder().build();
		this.chatOptions = DashScopeChatOptions.builder().build();
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
					.tools(availableTools)
					.call()
					.chatResponse();

			this.toolCallChatResponse = chatResponse;
			AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
			List<AssistantMessage.ToolCall> toolCallList = assistantMessage.getToolCalls();

			log.info("{} 的思考: {}", getName(), assistantMessage.getText());
			log.info("{} 选择了 {} 个工具", getName(), toolCallList.size());

			if (!toolCallList.isEmpty()) {
				String toolCallInfo = toolCallList.stream()
						.map(tc -> String.format("工具名称：%s，参数：%s", tc.name(), tc.arguments()))
						.collect(Collectors.joining("\n"));
				log.info(toolCallInfo);
				// 有工具调用时不单独记录助手消息，executeToolCalls 会自动写入对话历史
				return true;
			} else {
				// 无工具调用，直接记录助手回复
				getMessageList().add(assistantMessage);
				return false;
			}
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

		Prompt prompt = new Prompt(getMessageList(), chatOptions);
		ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, toolCallChatResponse);

		// 将包含助手消息和工具结果的完整历史同步回消息列表
		setMessageList(toolExecutionResult.conversationHistory());

		ToolResponseMessage toolResponseMessage =
				(ToolResponseMessage) CollUtil.getLast(toolExecutionResult.conversationHistory());

		String results = toolResponseMessage.getResponses().stream()
				.map(r -> "工具 " + r.name() + " 完成了它的任务！结果: " + r.responseData())
				.collect(Collectors.joining("\n"));

		// 若调用了终止工具，则将状态置为 FINISHED
		boolean terminated = toolResponseMessage.getResponses().stream()
				.anyMatch(r -> TERMINATE_TOOL_NAME.equals(r.name()));
		if (terminated) {
			setState(AgentState.FINISHED);
		}

		log.info(results);
		return results;
	}
}

