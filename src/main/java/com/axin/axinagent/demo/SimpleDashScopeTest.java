package com.axin.axinagent.demo;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.function.BiFunction;

public class SimpleDashScopeTest {

	/**
	 * 密钥
	 */
	private static final String API_KEY = "sk-06700e91df384f1f8a5eb8f708a8183d";

	public static void main(String[] args) {

		// 初始化 ChatModel
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(API_KEY)
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		// 定义天气查询工具
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

		// 创建 agent
		ReactAgent agent = ReactAgent.builder()
				.name("weather_agent")
				.model(chatModel)
				.systemPrompt("You are a helpful assistant")
				.tools(weatherTool)
				.saver(new MemorySaver())
				.build();

		// 运行 agent
		AssistantMessage response;
		try {
			response = agent.call("what is the weather in San Francisco, use Chinese");
		} catch (GraphRunnerException e) {
			throw new RuntimeException(e);
		}
		System.out.println(response.getText());
	}
}
