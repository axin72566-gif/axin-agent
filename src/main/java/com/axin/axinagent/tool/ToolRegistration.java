package com.axin.axinagent.tool;

import com.axin.axinagent.memory.EpisodicMemoryService;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Arrays;
import java.util.List;

@Configuration
public class ToolRegistration {

    @Value("${search-api.api-key}")
    private String searchApiKey;

    @Primary
    @Bean("allTools")
    public ToolCallback[] allTools(EpisodicMemoryService episodicMemoryService, PDFGenerationTool pdfGenerationTool) {
        WebSearchTool webSearchTool = new WebSearchTool(searchApiKey);
        WebScrapingTool webScrapingTool = new WebScrapingTool();
        TerminateTool terminateTool = new TerminateTool();
        EpisodicMemoryTool episodicMemoryTool = new EpisodicMemoryTool(episodicMemoryService);
        FileSystemTool fileSystemTool = new FileSystemTool();
        HttpRequestTool httpRequestTool = new HttpRequestTool();
        CodeExecutionTool codeExecutionTool = new CodeExecutionTool();
        return ToolCallbacks.from(
                webSearchTool,
                webScrapingTool,
                pdfGenerationTool,
                episodicMemoryTool,
                fileSystemTool,
                httpRequestTool,
                codeExecutionTool,
                terminateTool
        );
    }

    @Bean("searchTools")
    public ToolCallback[] searchTools(@Qualifier("allTools") ToolCallback[] allTools) {
        return filterTools(allTools, List.of("webSearchTool", "webScrapingTool", "terminateTool"));
    }

    @Bean("writerTools")
    public ToolCallback[] writerTools(@Qualifier("allTools") ToolCallback[] allTools) {
        return filterTools(allTools, List.of("pdfGenerationTool", "fileSystemTool", "terminateTool"));
    }

    @Bean("fileTools")
    public ToolCallback[] fileTools(@Qualifier("allTools") ToolCallback[] allTools) {
        return filterTools(allTools, List.of("fileSystemTool", "pdfGenerationTool", "terminateTool"));
    }

    private ToolCallback[] filterTools(ToolCallback[] allTools, List<String> targetNames) {
        return Arrays.stream(allTools)
                .filter(tool -> targetNames.stream()
                        .anyMatch(name -> name.equalsIgnoreCase(tool.getToolDefinition().name())))
                .toArray(ToolCallback[]::new);
    }
}