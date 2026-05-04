package com.axin.axinagent.tool;

import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class ToolRegistration {

    @Value("${search-api.api-key}")
    private String searchApiKey;

    @Primary
    @Bean("allTools")
    public ToolCallback[] allTools(PDFGenerationTool pdfGenerationTool) {
        return ToolCallbacks.from(
                new WebSearchTool(searchApiKey),
                new WebScrapingTool(),
                pdfGenerationTool,
                new FileSystemTool(),
                new HttpRequestTool(),
                new CodeExecutionTool(),
                new TerminateTool()
        );
    }
}