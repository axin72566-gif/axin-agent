package com.axin.axinagent.memory;

import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 长期记忆服务：基于 Redis VectorStore 的通用语义记忆。
 */
@Service
@ConditionalOnBean(VectorStore.class)
public class LongTermMemoryService {

	@Resource
	private VectorStore vectorStore;

	public void store(String userId, String agentName, String content) {
		if (!StringUtils.hasText(userId) || !StringUtils.hasText(content)) {
			return;
		}
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("userId", userId);
		metadata.put("agentName", agentName);
		metadata.put("source", "long_term");
		Document document = Document.builder()
				.text(content)
				.metadata(metadata)
				.build();
		vectorStore.add(List.of(document));
	}

	public List<String> search(String userId, String query, int topK) {
		SearchRequest request = SearchRequest.builder()
				.query(query)
				.topK(Math.max(topK, 1))
				.filterExpression("source == 'long_term' && userId == '" + escapeFilter(userId) + "'")
				.build();
		return vectorStore.similaritySearch(request).stream().map(Document::getText).collect(Collectors.toList());
	}

	public List<String> search(String userId, String agentName, String query, int topK) {
		SearchRequest request = SearchRequest.builder()
				.query(query)
				.topK(Math.max(topK, 1))
				.filterExpression("source == 'long_term' && userId == '" + escapeFilter(userId)
						+ "' && agentName == '" + escapeFilter(agentName) + "'")
				.build();
		return vectorStore.similaritySearch(request).stream().map(Document::getText).collect(Collectors.toList());
	}

	private String escapeFilter(String value) {
		if (value == null) {
			return "";
		}
		return value.replace("'", "\\'");
	}
}
