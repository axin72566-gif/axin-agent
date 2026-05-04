package com.axin.axinagent.config;

import com.axin.axinagent.chatmemory.FileBasedChatMemory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 记忆层统一配置。
 */
@Configuration
public class MemoryConfig {

	@Bean
	public FileBasedChatMemory fileBasedChatMemory() {
		return new FileBasedChatMemory("./chat-memory");
	}

	@Bean
	public ChatMemory defaultChatMemory(FileBasedChatMemory fileBasedChatMemory) {
		return MessageWindowChatMemory.builder()
				.chatMemoryRepository(fileBasedChatMemory)
				.maxMessages(20)
				.build();
	}
}
