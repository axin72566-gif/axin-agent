package com.axin.axinagent.agent;

import com.axin.axinagent.advisor.LogAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

/**
 * 信息收集 Agent：专注于搜索、网页抓取、数据汇总。
 * <p>
 * 仅注册搜索类工具（WebSearchTool、WebScrapingTool、TerminateTool），
 * 专职执行信息收集类子任务，保持单一职责。
 */
public class ResearchAgent extends ToolCallAgent {

    private static final String SYSTEM_PROMPT = """
            You are a professional research assistant. Your sole responsibility is to gather, collect, and summarize information.
            Focus on:
            - Searching the web for relevant information
            - Scraping web pages for detailed content
            - Summarizing and organizing the collected information clearly
            
            Do NOT generate reports or write final content. Your output is raw research material for the writer.
            When you have gathered sufficient information, use the terminate tool to stop.
            """;

    private static final String NEXT_STEP_PROMPT = """
            Based on the research task, search for relevant information using available tools.
            Collect comprehensive data and summarize findings. Use terminate when research is complete.
            """;

    public ResearchAgent(ToolCallback[] searchTools, ChatModel chatModel) {
        super(searchTools);
        setName("ResearchAgent");
        setSystemPrompt(SYSTEM_PROMPT);
        setNextStepPrompt(NEXT_STEP_PROMPT);
        setMaxSteps(10);
        setChatClient(ChatClient.builder(chatModel)
                .defaultAdvisors(new LogAdvisor())
                .build());
    }
}
