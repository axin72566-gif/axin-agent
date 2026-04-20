package com.axin.axinagent.advisor;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
@Slf4j
public class LogAdvisor implements CallAdvisor, StreamAdvisor {

	@NotNull
	@Override
	public ChatClientResponse adviseCall(@NotNull ChatClientRequest chatClientRequest, @NotNull CallAdvisorChain callAdvisorChain) {
		log.info("------------------------------------------LogAdvisor--------------------------------------------");

		// 1. 记录用户输入
		String userMessage = chatClientRequest.prompt().getUserMessage().getText();
		log.info("用户输入: {}", userMessage);

		// 继续执行链，调用模型
		ChatClientResponse response = callAdvisorChain.nextCall(chatClientRequest);

		// 2. 记录 AI 响应
		try {
			String aiText = response.chatResponse().getResult().getOutput().getText();
			log.info("AI 响应: {}", aiText);
		} catch (Exception e) {
			log.warn("LogAdvisor 记录响应异常: {}", e.getMessage());
		}

		return response;
	}

	@NotNull
	@Override
	public Flux<ChatClientResponse> adviseStream(@NotNull ChatClientRequest chatClientRequest, @NotNull StreamAdvisorChain streamAdvisorChain) {
		log.info("------------------------------------------LogAdvisor (流式)--------------------------------------------");

		// 1. 记录用户输入
		String userMessage = chatClientRequest.prompt().getUserMessage().getText();
		log.info("流式请求 - 用户输入: {}", userMessage);

		// 5. 继续执行流式链
		return streamAdvisorChain.nextStream(chatClientRequest);
	}

	@NotNull
	@Override
	public String getName() {
		return "LogAdvisor";
	}

	@Override
	public int getOrder() {
		return 0;
	}
}
