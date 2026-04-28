package com.axin.axinagent.controller;

import com.axin.axinagent.agent.AxinManus;
import com.axin.axinagent.app.LoveApp;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;

@RestController
@RequestMapping("/ai")
public class AiController {

	@Resource
	private LoveApp loveApp;

	@Resource
	private ToolCallback[] allTools;

	@Resource
	private ChatModel dashscopeChatModel;

	/**
	 * 同步聊天
	 *
	 * @param message 用户消息
	 * @param chatId  会话 ID
	 * @return 模型回复
	 */
	@GetMapping("/love_app/chat/sync")
	public String doChatWithLoveAppSync(String message, String chatId) {
		return loveApp.doChat(message, chatId);
	}

	/**
	 * 流式聊天（SSE，MediaType 方式）
	 *
	 * @param message 用户消息
	 * @param chatId  会话 ID
	 * @return Flux 文本流
	 */
	@GetMapping(value = "/love_app/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<String> doChatWithLoveAppSSE(String message, String chatId) {
		return loveApp.doChatByStream(message, chatId);
	}

	/**
	 * 流式聊天（SseEmitter 方式，适合需要手动控制 SSE 生命周期的场景）
	 *
	 * @param message 用户消息
	 * @param chatId  会话 ID
	 * @return SseEmitter 实例
	 */
	@GetMapping("/love_app/chat/sse/emitter")
	public SseEmitter doChatWithLoveAppSseEmitter(String message, String chatId) {
		SseEmitter emitter = new SseEmitter(180_000L);
		loveApp.doChatByStream(message, chatId)
				.subscribe(
						chunk -> {
							try {
								emitter.send(chunk);
							} catch (IOException e) {
								emitter.completeWithError(e);
							}
						},
						emitter::completeWithError,
						emitter::complete
				);
		return emitter;
	}

	/**
	 * 流式调用 Manus 超级智能体
	 *
	 * @param message 用户消息
	 * @return SseEmitter 实例
	 */
	@GetMapping("/manus/chat")
	public SseEmitter doChatWithManus(String message) {
		AxinManus axinManus = new AxinManus(allTools, dashscopeChatModel);
		return axinManus.runStream(message);
	}
}

