package com.axin.axinagent.demo.learning;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

@Component
public class ReactLearning {

	@Resource
	private DashScopeChatModel dashScopeChatModel;

	public void test() throws GraphRunnerException {
		// 创建工具回调
		ToolCallback searchTool = FunctionToolCallback.builder("search", new SearchTool())
				.description("搜索工具")
				.inputType(String.class)
				.build();

		// 创建 Agent
		ReactAgent agent = ReactAgent.builder()
				.name("my_agent")
				.model(dashScopeChatModel)
				.tools(searchTool)
				.hooks(new LoggingHook(), new MessageTrimmingHook())
				.interceptors(new ToolErrorInterceptor(), new GuardrailInterceptor())
				.build();

		AssistantMessage response = agent.call("请使用工具搜索"DashScope"");
		System.out.println(response);
	}
}
