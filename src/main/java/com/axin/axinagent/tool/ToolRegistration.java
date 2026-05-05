package com.axin.axinagent.tool;

import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class ToolRegistration {

    @Primary
    @Bean("allTools")
    public ToolCallback[] allTools(
            WebSearchTool webSearchTool,
            WebScrapingTool webScrapingTool,
            FileSystemTool fileSystemTool,
            HttpRequestTool httpRequestTool,
            CodeExecutionTool codeExecutionTool,
            TerminateTool terminateTool
    ) {
        return ToolCallbacks.from(
                webSearchTool,
                webScrapingTool,
                fileSystemTool,
                httpRequestTool,
                codeExecutionTool,
                terminateTool
        );
    }
}