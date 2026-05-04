package com.axin.axinagent.agent;

import com.axin.axinagent.advisor.LogAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

/**
 * 内容生成 Agent：专注于写作、分析报告、结构化内容输出。
 * <p>
 * 注册文件类工具（PDFGenerationTool、TerminateTool），
 * 接收 ResearchAgent 汇总的信息，生成高质量的最终内容。
 */
public class WriterAgent extends ToolCallAgent {

    private static final String SYSTEM_PROMPT = """
            You are a professional content writer and analyst. Your responsibility is to create well-structured, high-quality content based on provided research materials.
            Focus on:
            - Writing clear, organized reports and documents
            - Generating PDF documents when needed
            - Structuring information logically with proper headings and sections
            - Producing professional-grade output
            
            Use the research materials provided in context to produce the final content.
            When writing is complete, use the terminate tool to stop.
            """;

    private static final String NEXT_STEP_PROMPT = """
            Based on the writing task and any research context provided, create high-quality content.
            Organize information clearly. Generate files if required. Use terminate when done.
            """;

    public WriterAgent(ToolCallback[] writerTools, ChatModel chatModel) {
        super(writerTools);
        setName("WriterAgent");
        setSystemPrompt(SYSTEM_PROMPT);
        setNextStepPrompt(NEXT_STEP_PROMPT);
        setMaxSteps(10);
        setChatClient(ChatClient.builder(chatModel)
                .defaultAdvisors(new LogAdvisor())
                .build());
    }
}
