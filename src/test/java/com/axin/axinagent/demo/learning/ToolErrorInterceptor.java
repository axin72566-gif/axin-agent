package com.axin.axinagent.demo.learning;

import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;

public class ToolErrorInterceptor extends ToolInterceptor {
	@Override
	public ToolCallResponse interceptToolCall(ToolCallRequest request, ToolCallHandler handler) {
		System.out.println("----------------执行工具错误拦截器-------");
		try {
			return handler.call(request);
		} catch (Exception e) {
			System.out.println("----------------工具执行异常-------");
			return ToolCallResponse.of(request.getToolCallId(), request.getToolName(),
					"Tool failed: " + e.getMessage());
		}
	}

	@Override
	public String getName() {
		return "ToolErrorInterceptor";
	}
}
