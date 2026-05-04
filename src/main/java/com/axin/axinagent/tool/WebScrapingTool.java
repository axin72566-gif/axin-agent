package com.axin.axinagent.tool;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Slf4j
public class WebScrapingTool {

	private static final int TIMEOUT = 10000;
	private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
	private static final int MAX_LENGTH = 3000;

	@Tool(description = "抓取网页的文本内容")
	public String scrapeWebPage(@ToolParam(description = "要抓取的网页URL") String url) {
		try {
			Document doc = Jsoup.connect(url)
					.userAgent(USER_AGENT)
					.timeout(TIMEOUT)
					.get();
			log.info("成功抓取网页: {}", url);
			String text = doc.text();
			if (text.length() > MAX_LENGTH) {
				text = text.substring(0, MAX_LENGTH) + "...(内容已截断)";
			}
			return text;
		} catch (IOException e) {
			log.error("抓取网页失败: {}, 错误: {}", url, e.getMessage());
			return "抓取网页失败: " + e.getMessage();
		}
	}
}
