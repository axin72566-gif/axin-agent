package com.axin.axinagent.tool;

import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ToolRegistration {

	@Value("${search-api.api-key}")
	private String searchApiKey;

	@Bean
	public ToolCallback[] allTools() {
		WebSearchTool webSearchTool = new WebSearchTool(searchApiKey);
		WebScrapingTool webScrapingTool = new WebScrapingTool();
		PDFGenerationTool pdfGenerationTool = new PDFGenerationTool();
		TerminateTool terminateTool = new TerminateTool();
		return ToolCallbacks.from(
				webSearchTool,
				webScrapingTool,
				pdfGenerationTool,
				terminateTool
		);
	}
}