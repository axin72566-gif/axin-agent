package com.axin.axinagentimagemcpserver;

import com.axin.axinagentimagemcpserver.mcptool.ImageSearchTool;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class AxinAgentImageMcpServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(AxinAgentImageMcpServerApplication.class, args);
	}

	/**
	 * 注册 mcp 工具
	 * @param imageSearchTool 图片搜索工具
	 * @return 工具回调提供者
	 */
	@Bean
	public ToolCallbackProvider imageSearchTools(ImageSearchTool imageSearchTool) {
		return MethodToolCallbackProvider.builder()
				.toolObjects(imageSearchTool)
				.build();
	}

}
