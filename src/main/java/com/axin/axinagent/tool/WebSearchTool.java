package com.axin.axinagent.tool;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class WebSearchTool {

	private static final String SEARCH_API_URL = "https://www.searchapi.io/api/v1/search";

	@Value("${search-api.api-key}")
	private String apiKey;

	public WebSearchTool() {
	}

	@Tool(description = "Search for information from Baidu Search Engine")
	public String searchWeb(
			@ToolParam(description = "Search query keyword") String query) {
		Map<String, Object> paramMap = new HashMap<>();
		paramMap.put("q", query);
		paramMap.put("api_key", apiKey);
		paramMap.put("engine", "baidu");
		try {
			String response = HttpUtil.get(SEARCH_API_URL, paramMap);
			JSONObject jsonObject = JSONUtil.parseObj(response);
			JSONArray organicResults = jsonObject.getJSONArray("organic_results");
			List<Object> objects = organicResults.subList(0, Math.min(5, organicResults.size()));
			return objects.stream().map(obj -> {
				JSONObject tmpJSONObject = (JSONObject) obj;
				return tmpJSONObject.toString();
			}).collect(Collectors.joining(","));
		} catch (Exception e) {
			return "Error searching Baidu: " + e.getMessage();
		}
	}
}

