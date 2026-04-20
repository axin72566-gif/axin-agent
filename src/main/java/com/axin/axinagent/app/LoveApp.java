package com.axin.axinagent.app;

import com.axin.axinagent.advisor.LogAdvisor;
import com.axin.axinagent.advisor.Re2Advisor;
import com.axin.axinagent.chatmemory.FileBasedChatMemory;
import com.axin.axinagent.model.LoveReport;
import com.axin.axinagent.rag.RagStrategy;
import com.axin.axinagent.rag.RagStrategyContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

@Component
@Slf4j
public class LoveApp {

	/**
	 * 系统提示
	 */
	private static final String SYSTEM_PROMPT="扮演深耕恋爱心理领域的专家。开场向用户表明身份，告知用户可倾诉恋爱难题。" +
	                                          "围绕单身、恋爱、已婚三种状态提问：单身状态询问社交圈拓展及追求心仪对象的困扰；" +
	                                          "恋爱状态询问沟通、习惯差异引发的矛盾；已婚状态询问家庭责任与亲属关系处理的问题。" +
	                                          "引导用户详述事情经过、对方反应及自身想法，以便给出专属解决方案。";

	private final ChatClient chatClient;

	@Resource
	private RagStrategyContext ragStrategyContext;

	@Resource
	private ToolCallback[] toolCallbacks;

	@Resource
	private ToolCallbackProvider toolCallbackProvider;

	/**
	 * 构造函数 内存持久化记忆
	 * @param dashScopeChatModel 大模型
	 */
	public LoveApp(ChatModel dashScopeChatModel) {
		ChatMemory chatMemory = MessageWindowChatMemory.builder()
				.chatMemoryRepository(new InMemoryChatMemoryRepository())
				.maxMessages(20)
				.build();
		chatClient = ChatClient.builder(dashScopeChatModel)
				.defaultSystem(SYSTEM_PROMPT)
				.defaultAdvisors(
						MessageChatMemoryAdvisor.builder(chatMemory).build(),
						// 记录日志
						new LogAdvisor(),
						new Re2Advisor()
				)
				.build();
	}

	/**
	 * 普通聊天
	 * @param message 输入
	 * @param chatId 聊天id
	 * @return 响应
	 */
	public String doChat(String message, String chatId) {
		ChatResponse response = chatClient
				.prompt()
				.user(message)
				.advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
				.call()
				.chatResponse();
		assert response != null;
		return response.getResult().getOutput().getText();
	}

	/**
	 * 结构化报告
	 * @param message 输入
	 * @param chatId 聊天id
	 * @return 结构化报告
	 */
	public LoveReport doChatWithReport(String message, String chatId) {
		return chatClient
				.prompt()
				.user(message)
				.advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
				.call()
				.entity(LoveReport.class);
	}

	/**
	 * RAG增强器（策略模式，支持本地Redis/云端DashScope切换）
	 * @param message  输入
	 * @param chatId   聊天id
	 * @param strategy RAG检索策略（LOCAL / CLOUD）
	 * @return 响应
	 */
	public LoveReport doChatWithRag(String message, String chatId, RagStrategy strategy) {
		Advisor ragAdvisor = ragStrategyContext.getAdvisor(strategy);
		LoveReport loveReport = chatClient
				.prompt()
				.user(message)
				.advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
				.advisors(ragAdvisor)
				.call()
				.entity(LoveReport.class);
		assert loveReport != null;
		return loveReport;
	}

	/**
	 * 工具调用
	 * @param message 输入
	 * @param chatId 聊天id
	 * @return 响应
	 */
	public String doChatWithTools(String message, String chatId) {
		ChatResponse response = chatClient
				.prompt()
				.user(message)
				.advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
				.toolCallbacks(toolCallbacks)
				.call()
				.chatResponse();
		assert response != null;
		return response.getResult().getOutput().getText();
	}

	/**
	 * MCP工具调用
	 * @param message 输入
	 * @param chatId 聊天id
	 * @return 响应（包含工具返回的完整URL列表）
	 */
	public String doChatWithMcp(String message, String chatId) {
		ChatResponse response = chatClient
				.prompt()
				.system(SYSTEM_PROMPT)
				.user(message)
				.advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
				.toolCallbacks(toolCallbackProvider)
				.call()
				.chatResponse();
		assert response != null;
		return response.getResult().getOutput().getText();
	}

	/**
	 * 流式聊天
	 * @param message 输入
	 * @param chatId 聊天id
	 * @return 流式响应
	 */
	public Flux<String> doChatByStream(String message, String chatId) {
		return chatClient
				.prompt()
				.user(message)
				.advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
				.stream()
				.content();
	}

//	/**
//	 * 构造函数 文件持久化记忆
//	 * @param dashscopeChatModel 大模型
//	 */
//	public LoveApp(ChatModel dashscopeChatModel) {
//		// 初始化基于文件的对话记忆
//		String fileDir = System.getProperty("user.dir") + "/chat-memory";
//		ChatMemory chatMemory = new FileBasedChatMemory(fileDir);
//		chatClient = ChatClient.builder(dashscopeChatModel)
//				.defaultSystem(SYSTEM_PROMPT)
//				.defaultAdvisors(
//						MessageChatMemoryAdvisor.builder(chatMemory).build()
//				)
//				.build();
//	}

}