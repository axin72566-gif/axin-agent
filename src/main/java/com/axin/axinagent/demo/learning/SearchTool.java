package com.axin.axinagent.demo.learning;

import org.springframework.ai.chat.model.ToolContext;

import java.util.function.BiFunction;

public class SearchTool implements BiFunction<String, ToolContext, String> {
	@Override
	public String apply(String query, ToolContext context) {
		// 实现搜索逻辑
		System.out.println("---------- 搜索工具调用-----------");
		return "搜索结果: " + query;
	}
}