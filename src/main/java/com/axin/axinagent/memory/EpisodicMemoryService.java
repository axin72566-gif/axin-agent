package com.axin.axinagent.memory;

import jakarta.annotation.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 情节记忆服务。
 */
@Service
public class EpisodicMemoryService {

	@Resource
	private EpisodicMemoryRepository episodicMemoryRepository;

	public EpisodicMemoryEvent saveEvent(String agentName,
	                                     String userId,
	                                     String eventType,
	                                     String summary,
	                                     String keywords,
	                                     Integer importance) {
		EpisodicMemoryEvent event = new EpisodicMemoryEvent();
		event.setAgentName(agentName);
		event.setUserId(userId);
		event.setEventType(eventType);
		event.setSummary(summary);
		event.setKeywords(keywords);
		event.setImportance(normalizeImportance(importance));
		return episodicMemoryRepository.save(event);
	}

	public List<EpisodicMemoryEvent> findByUserId(String userId, int limit) {
		return episodicMemoryRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, Math.max(limit, 1)));
	}

	public List<EpisodicMemoryEvent> findByAgentAndUser(String agentName, String userId, int limit) {
		return episodicMemoryRepository.findByAgentNameAndUserIdOrderByCreatedAtDesc(agentName, userId,
				PageRequest.of(0, Math.max(limit, 1)));
	}

	public List<EpisodicMemoryEvent> searchByKeyword(String userId, String keyword, int limit) {
		if (!StringUtils.hasText(keyword)) {
			return findByUserId(userId, limit);
		}
		return episodicMemoryRepository.searchByKeyword(userId, keyword.trim(), PageRequest.of(0, Math.max(limit, 1)));
	}

	public List<EpisodicMemoryEvent> findTopImportantEvents(String userId, int limit) {
		return episodicMemoryRepository.findByUserIdOrderByImportanceDescCreatedAtDesc(userId,
				PageRequest.of(0, Math.max(limit, 1)));
	}

	private int normalizeImportance(Integer importance) {
		if (importance == null) {
			return 5;
		}
		return Math.max(1, Math.min(10, importance));
	}
}
