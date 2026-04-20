package com.axin.axinagent.demo.learning;


import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;

// ModelInterceptor - 内容安全检查
public class GuardrailInterceptor extends ModelInterceptor {
	@Override
	public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
		// 前置：检查输入
		System.out.println("--------------- GuardrailInterceptor beforeModel 模型调用开始");

		// 执行 model 调用
		ModelResponse response = handler.call(request);

		// 后置：检查输出
		System.out.println("--------------- GuardrailInterceptor afterModel 模型调用结束");
		return response;
	}

	@Override
	public String getName() {
		return "GuardrailInterceptor";
	}
}
