package com.axin.axinagent.demo.learning;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import com.alibaba.cloud.ai.graph.agent.hook.messages.UpdatePolicy;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

// 2. MessagesModelHook - 在模型调用前后执行（例如：消息修剪），专门用于操作消息列表，使用更简单，更推荐。
// 区别于AgentHook，MessagesModelHook在一次agent调用中可能会调用多次，也就是每次 reasoning-acting 迭代都会执行
@HookPositions({HookPosition.BEFORE_MODEL})
public class MessageTrimmingHook extends MessagesModelHook {

	// 最大消息数量
	private static final int MAX_MESSAGES = 10;

	@Override
	public String getName() {
		return "message_trimming";
	}

	@Override
	public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
		System.out.println("--------------- MessageTrimmingHook beforeModel 修剪信息开始");
		if (previousMessages.size() > MAX_MESSAGES) {
			// 只保留最后 MAX_MESSAGES 条消息
			List<Message> trimmedMessages = previousMessages.subList(
					previousMessages.size() - MAX_MESSAGES,
					previousMessages.size()
			);
			return new AgentCommand(trimmedMessages, UpdatePolicy.REPLACE);
		}
		// 消息数量未超过限制，直接返回原消息列表
		return new AgentCommand(previousMessages);
	}
}
