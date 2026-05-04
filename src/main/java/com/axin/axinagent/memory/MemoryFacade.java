package com.axin.axinagent.memory;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 统一记忆门面。
 */
@Component
public class MemoryFacade {

	@Resource
	private ChatMemory defaultChatMemory;

	@Autowired(required = false)
	private LongTermMemoryService longTermMemoryService;

	@Resource
	private EpisodicMemoryService episodicMemoryService;

	public void rememberShortTerm(String conversationId, List<Message> messages) {
		defaultChatMemory.add(conversationId, messages);
	}

	public List<Message> recallShortTerm(String conversationId, int lastN) {
		List<Message> allMessages = defaultChatMemory.get(conversationId);
		if (allMessages.size() <= Math.max(lastN, 1)) {
			return allMessages;
		}
		return allMessages.subList(allMessages.size() - Math.max(lastN, 1), allMessages.size());
	}


	public void clearShortTerm(String conversationId) {
		defaultChatMemory.clear(conversationId);
	}

	public void rememberLongTerm(String userId, String agentName, String content) {
		if (longTermMemoryService != null) {
			longTermMemoryService.store(userId, agentName, content);
		}
	}

	public List<String> recallLongTerm(String userId, String query, int topK) {
		if (longTermMemoryService != null) {
			return longTermMemoryService.search(userId, query, topK);
		}
		return Collections.emptyList();
	}

	public EpisodicMemoryEvent rememberEpisode(String agentName,
	                                           String userId,
	                                           String eventType,
	                                           String summary,
	                                           String keywords,
	                                           Integer importance) {
		return episodicMemoryService.saveEvent(agentName, userId, eventType, summary, keywords, importance);
	}

	public List<EpisodicMemoryEvent> searchEpisode(String userId, String keyword, int limit) {
		return episodicMemoryService.searchByKeyword(userId, keyword, limit);
	}

	public List<EpisodicMemoryEvent> topImportantEpisodes(String userId, int limit) {
		return episodicMemoryService.findTopImportantEvents(userId, limit);
	}
}

