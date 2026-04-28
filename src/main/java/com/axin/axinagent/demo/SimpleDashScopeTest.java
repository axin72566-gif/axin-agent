package com.axin.axinagent.demo;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.function.BiFunction;

/**
 * DashScope ReactAgent 独立测试（演示用途，非生产代码）。
 * API Key 通过环境变量 DASHSCOPE_API_KEY 注入。
 */
public class SimpleDashScopeTest {

	public static void main(String[] args) {
		String apiKey = System.getenv("DASHSCOPE_API_KEY");
		if (apiKey == null || apiKey.isBlank()) {
			throw new IllegalStateException("环境变量 DASHSCOPE_API_KEY 未设置");
		}

		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(apiKey)
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		class WeatherTool implements BiFunction<String, ToolContext, String> {
			@Override
			public String apply(String city, ToolContext toolContext) {
				return "It's always sunny in " + city + "!";
			}
		}

		ToolCallback weatherTool = FunctionToolCallback.builder("get_weather", new WeatherTool())
				.description("Get weather for a given city")
				.inputType(String.class)
				.build();

		ReactAgent agent = ReactAgent.builder()
				.name("weather_agent")
				.model(chatModel)
				.systemPrompt("You are a helpful assistant")
				.tools(weatherTool)
				.saver(new MemorySaver())
				.build();

		try {
			AssistantMessage response = agent.call("what is the weather in San Francisco, use Chinese");
			System.out.println(response.getText());
		} catch (GraphRunnerException e) {
			throw new RuntimeException(e);
		}
	}
}
