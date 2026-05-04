package com.axin.axinagent.tool;

import com.axin.axinagent.memory.EpisodicMemoryEvent;
import com.axin.axinagent.memory.EpisodicMemoryService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 情节记忆工具。
 */
public class EpisodicMemoryTool {

	private final EpisodicMemoryService episodicMemoryService;

	public EpisodicMemoryTool(EpisodicMemoryService episodicMemoryService) {
		this.episodicMemoryService = episodicMemoryService;
	}

	@Tool(description = "保存重要情节记忆事件")
	public String saveEpisodicMemory(
			@ToolParam(description = "Agent 名称") String agentName,
			@ToolParam(description = "用户 ID") String userId,
			@ToolParam(description = "事件类型") String eventType,
			@ToolParam(description = "事件摘要") String summary,
			@ToolParam(description = "关键词，逗号分隔") String keywords,
			@ToolParam(description = "重要性评分，1-10") Integer importance) {
		EpisodicMemoryEvent event = episodicMemoryService.saveEvent(agentName, userId, eventType, summary, keywords, importance);
		return "情节记忆已保存，eventId=" + event.getId() + ", importance=" + event.getImportance();
	}

	@Tool(description = "检索用户的历史情节记忆")
	public String searchEpisodicMemory(
			@ToolParam(description = "用户 ID") String userId,
			@ToolParam(description = "关键词，可为空") String keyword,
			@ToolParam(description = "返回条数") Integer limit) {
		int finalLimit = limit == null ? 5 : Math.max(1, limit);
		List<EpisodicMemoryEvent> events = episodicMemoryService.searchByKeyword(userId, keyword, finalLimit);
		if (events.isEmpty()) {
			return "未检索到相关情节记忆";
		}
		return events.stream()
				.map(e -> "[" + e.getCreatedAt() + "]"
						+ "(" + e.getEventType() + ",重要性=" + e.getImportance() + ") "
						+ e.getSummary()
						+ (e.getKeywords() == null ? "" : " | 关键词: " + e.getKeywords()))
				.collect(Collectors.joining("\n"));
	}
}
