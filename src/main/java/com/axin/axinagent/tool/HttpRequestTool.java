package com.axin.axinagent.tool;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.LinkedHashMap;
import java.util.Map;

public class HttpRequestTool {

	private static final int TIMEOUT_MS = 15000;
	private static final int MAX_RESPONSE_LENGTH = 5000;

	@Tool(description = "Send HTTP GET request")
	public String httpGet(
			@ToolParam(description = "Target URL") String url,
			@ToolParam(description = "Optional headers, format: key:value,key:value") String headers) {
		try {
			HttpRequest request = HttpRequest.get(url).timeout(TIMEOUT_MS);
			applyHeaders(request, headers);
			try (HttpResponse response = request.execute()) {
				return truncate(response.body(), MAX_RESPONSE_LENGTH);
			}
		} catch (Exception e) {
			return "HTTP GET error: " + e.getMessage();
		}
	}

	@Tool(description = "Send HTTP POST request with JSON body")
	public String httpPost(
			@ToolParam(description = "Target URL") String url,
			@ToolParam(description = "JSON body string") String jsonBody,
			@ToolParam(description = "Optional headers, format: key:value,key:value") String headers) {
		try {
			HttpRequest request = HttpRequest.post(url)
					.timeout(TIMEOUT_MS)
					.header("Content-Type", "application/json");
			applyHeaders(request, headers);
			request.body(jsonBody == null ? "" : jsonBody);
			try (HttpResponse response = request.execute()) {
				return truncate(response.body(), MAX_RESPONSE_LENGTH);
			}
		} catch (Exception e) {
			return "HTTP POST error: " + e.getMessage();
		}
	}

	private void applyHeaders(HttpRequest request, String headerText) {
		Map<String, String> headerMap = parseHeaders(headerText);
		headerMap.forEach(request::header);
	}

	private Map<String, String> parseHeaders(String headerText) {
		Map<String, String> headers = new LinkedHashMap<>();
		if (headerText == null || headerText.isBlank()) {
			return headers;
		}
		String[] pairs = headerText.split(",");
		for (String pair : pairs) {
			String[] kv = pair.split(":", 2);
			if (kv.length == 2 && !kv[0].isBlank()) {
				headers.put(kv[0].trim(), kv[1].trim());
			}
		}
		return headers;
	}

	private String truncate(String text, int maxLength) {
		if (text == null) {
			return "";
		}
		if (text.length() <= maxLength) {
			return text;
		}
		return text.substring(0, maxLength) + "...(内容已截断)";
	}
}
