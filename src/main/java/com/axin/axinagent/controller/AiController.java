package com.axin.axinagent.controller;

import com.axin.axinagent.advisor.LogAdvisor;
import com.axin.axinagent.agent.AxinManus;
import com.axin.axinagent.llm.ModelRouter;
import com.axin.axinagent.llm.ModelType;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/ai")
public class AiController {

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ModelRouter modelRouter;

    @Resource
    private LogAdvisor logAdvisor;

    /**
     * 同步聊天（带工具调用和会话记忆）。
     *
     * @param message 用户消息
     * @param chatId  会话 ID（可选，不传则每次新建会话）
     * @return AI 响应文本
     */
    @GetMapping("/chat/sync")
    public String chatSync(String message, String chatId) {
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(20)
                .build();

        ChatClient chatClient = ChatClient.builder(modelRouter.get(ModelType.PRIMARY))
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        logAdvisor
                )
                .build();

        String conversationId = chatId != null ? chatId : "default";
        return chatClient.prompt()
                .user(message)
                .tools(allTools)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
    }

    /**
     * 流式聊天（SSE Flux 方式，适合简单对话）。
     *
     * @param message 用户消息
     * @param chatId  会话 ID（可选）
     * @return 流式文本
     */
    @GetMapping(value = "/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatSse(String message, String chatId) {
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(20)
                .build();

        ChatClient chatClient = ChatClient.builder(modelRouter.get(ModelType.PRIMARY))
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        logAdvisor
                )
                .build();

        String conversationId = chatId != null ? chatId : "default";
        return chatClient.prompt()
                .user(message)
                .tools(allTools)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content();
    }

    /**
     * Agent ReAct 流式执行（SSE Emitter 方式）。
     * AxinManus 自主规划、使用工具、多步推理，每步结果实时推送。
     *
     * @param message 用户消息
     * @return SseEmitter 实例
     */
    @GetMapping("/agent/sse")
    public SseEmitter agentSse(String message) {
        AxinManus axinManus = new AxinManus(
                allTools,
                modelRouter.get(ModelType.PRIMARY),
                logAdvisor
        );
        return axinManus.runStream(message);
    }
}
