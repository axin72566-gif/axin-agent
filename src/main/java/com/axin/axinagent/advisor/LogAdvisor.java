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

/**
 * 日志 Advisor，记录每次请求的用户输入和 AI 响应（同步 + 流式）。
 */
@Component
@Slf4j
public class LogAdvisor implements CallAdvisor, StreamAdvisor {

	@NotNull
	@Override
	public ChatClientResponse adviseCall(@NotNull ChatClientRequest chatClientRequest,
	                                     @NotNull CallAdvisorChain callAdvisorChain) {
		log.info("------ LogAdvisor ------");
		log.info("用户输入: {}", chatClientRequest.prompt().getUserMessage().getText());

		ChatClientResponse response = callAdvisorChain.nextCall(chatClientRequest);

		try {
			log.info("AI 响应: {}", response.chatResponse().getResult().getOutput().getText());
		} catch (Exception e) {
			log.warn("LogAdvisor 记录响应异常: {}", e.getMessage());
		}
		return response;
	}

	@NotNull
	@Override
	public Flux<ChatClientResponse> adviseStream(@NotNull ChatClientRequest chatClientRequest,
	                                             @NotNull StreamAdvisorChain streamAdvisorChain) {
		log.info("------ LogAdvisor (流式) ------");
		log.info("流式请求 - 用户输入: {}", chatClientRequest.prompt().getUserMessage().getText());
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
