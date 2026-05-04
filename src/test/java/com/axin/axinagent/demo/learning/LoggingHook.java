package com.axin.axinagent.demo.learning;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.AgentHook;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

// 一次 agent 开头一次 结束一次
@HookPositions({HookPosition.BEFORE_AGENT, HookPosition.AFTER_AGENT})
public class LoggingHook extends AgentHook {
	@Override
	public String getName() { return "logging"; }

	@Override
	public CompletableFuture<Map<String, Object>> beforeAgent(OverAllState state, RunnableConfig config) {
		System.out.println("--------------- LoggingHook Agent 开始执行");
		return CompletableFuture.completedFuture(Map.of());
	}

	@Override
	public CompletableFuture<Map<String, Object>> afterAgent(OverAllState state, RunnableConfig config) {
		System.out.println("---------------- LoggingHook Agent 执行完成");
		return CompletableFuture.completedFuture(Map.of());
	}
}
