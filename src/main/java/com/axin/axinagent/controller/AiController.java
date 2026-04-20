package com.axin.axinagent.controller;

import com.axin.axinagent.agent.AxinManus;
import com.axin.axinagent.app.LoveApp;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
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
	 * 同步调用 LoveApp 进行聊天
	 * @param message 用户消息
	 * @param chatId 会话 ID
	 * @return 模型回复
	 */
	@GetMapping("/love_app/chat/sync")
	public String doChatWithLoveAppSync(String message, String chatId) {
		return loveApp.doChat(message, chatId);
	}

	/**
	 * 异步调用 LoveApp 进行聊天，返回 Flux 数据流
	 * @param message 用户消息
	 * @param chatId 会话 ID
	 * @return Flux 数据流，包含模型回复的每条消息
	 */
	@GetMapping(value = "/love_app/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<String> doChatWithLoveAppSSEMediaType(String message, String chatId) {
		return loveApp.doChatByStream(message, chatId);
	}

	/**
	 * 异步调用 LoveApp 进行聊天，返回 ServerSentEvent 数据流
	 * @param message 用户消息
	 * @param chatId 会话 ID
	 * @return ServerSentEvent 数据流，包含模型回复的每条消息
	 */
	@GetMapping(value = "/love_app/chat/sse")
	public Flux<ServerSentEvent<String>> doChatWithLoveAppSSEEvent(String message, String chatId) {
		return loveApp.doChatByStream(message, chatId)
				.map(chunk -> ServerSentEvent.<String>builder()
						.data(chunk)
						.build());
	}

	/**
	 * 异步调用 LoveApp 进行聊天，返回 SseEmitter 数据流
	 * @param message 用户消息
	 * @param chatId 会话 ID
	 * @return SseEmitter 数据流，用于实时接收模型回复的每条消息
	 */
	@GetMapping("/love_app/chat/sse/emitter")
	public SseEmitter doChatWithLoveAppSseEmitter(String message, String chatId) {
		// 创建一个超时时间较长的 SseEmitter
		SseEmitter emitter = new SseEmitter(180000L); // 3分钟超时
		// 获取 Flux 数据流并直接订阅
		loveApp.doChatByStream(message, chatId)
				.subscribe(
						// 处理每条消息
						chunk -> {
							try {
								emitter.send(chunk);
							} catch (IOException e) {
								emitter.completeWithError(e);
							}
						},
						// 处理错误
						emitter::completeWithError,
						// 处理完成
						emitter::complete
				);
		// 返回emitter
		return emitter;
	}

	/**
	 * 流式调用 Manus 超级智能体
	 *
	 * @param message 用户消息
	 * @return SseEmitter 数据流，用于实时接收模型回复的每条消息
	 */
	@GetMapping("/manus/chat")
	public SseEmitter doChatWithManus(String message) {
		AxinManus axinManus = new AxinManus(allTools, dashscopeChatModel);
		return axinManus.runStream(message);
	}



}

