package com.axin.axinagent.advisor;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class Re2Advisor implements CallAdvisor, StreamAdvisor {
	@NotNull
	@Override
	public ChatClientResponse adviseCall(@NotNull ChatClientRequest chatClientRequest, @NotNull CallAdvisorChain callAdvisorChain) {
		ChatClientRequest enhancedRequest = enhancePrompt(chatClientRequest);
		return callAdvisorChain.nextCall(enhancedRequest);
	}

	@NotNull
	@Override
	public Flux<ChatClientResponse> adviseStream(@NotNull ChatClientRequest chatClientRequest, @NotNull StreamAdvisorChain streamAdvisorChain) {
		ChatClientRequest enhancedRequest = enhancePrompt(chatClientRequest);
		return streamAdvisorChain.nextStream(enhancedRequest);
	}

	@NotNull
	@Override
	public String getName() {
		return "Re2Advisor";
	}

	@Override
	public int getOrder() {
		return 1;
	}

	/**
	 * 增强 Prompt：应用 Re2 技术
	 * 将用户输入改写为 "原问题 + Read the question again: 原问题" 的格式
	 */
	private ChatClientRequest enhancePrompt(ChatClientRequest chatClientRequest) {
		Prompt prompt = chatClientRequest.prompt();

		// 获取所有消息
		List<Message> messages = new ArrayList<>(prompt.getInstructions());

		// 找到最后一条用户消息并增强
		if (!messages.isEmpty()) {
			Message lastMessage = messages.getLast();

			// 如果最后一条是用户消息，则进行 Re2 增强
			if (lastMessage instanceof UserMessage userMessage) {
				String originalContent = userMessage.getText();

				// 应用 Re2 模式：原问题 + 重读提示
				String re2EnhancedContent = String.format("""
						%s
						
						Read the question again: %s
						""", originalContent, originalContent);

				// 替换最后一条用户消息
				messages.set(messages.size() - 1, new UserMessage(re2EnhancedContent));

				log.info("Re2Advisor 已增强用户输入");
				log.debug("原始内容: {}", originalContent);
				log.debug("增强后内容: {}", re2EnhancedContent);
			}
		}

		// 创建新的 Prompt
		Prompt enhancedPrompt = Prompt.builder()
				.chatOptions(prompt.getOptions())
				.messages(messages)
				.build();

		// 返回新的请求
		return ChatClientRequest.builder()
				.prompt(enhancedPrompt)
				.context(chatClientRequest.context())
				.build();
	}
}
