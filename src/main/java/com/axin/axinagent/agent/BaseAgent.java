package com.axin.axinagent.agent;

import cn.hutool.core.util.StrUtil;
import com.axin.axinagent.agent.model.AgentState;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 抽象基础代理类，管理代理状态和执行流程。
 * 提供状态转换、消息记忆管理和步骤循环的基础实现，子类须实现 {@link #step()} 方法。
 */
@Data
@Slf4j
public abstract class BaseAgent {

	private String name;
	private String systemPrompt;
	private String nextStepPrompt;

	private AgentState state = AgentState.IDLE;

	private int maxSteps = 10;
	private int currentStep = 0;

	private ChatClient chatClient;

	/** 自维护的会话消息上下文 */
	private List<Message> messageList = new ArrayList<>();

	/**
	 * 同步运行代理，返回所有步骤结果的拼接字符串。
	 *
	 * @param userPrompt 用户提示词
	 * @return 各步骤执行结果
	 */
	public String run(String userPrompt) {
		validateBeforeRun(userPrompt);
		state = AgentState.RUNNING;
		messageList.add(new UserMessage(userPrompt));
		List<String> results = new ArrayList<>();
		try {
			for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
				currentStep = i + 1;
				log.info("Executing step {}/{}", currentStep, maxSteps);
				results.add("Step " + currentStep + ": " + step());
			}
			if (state != AgentState.FINISHED) {
				state = AgentState.FINISHED;
				results.add("Terminated: Reached max steps (" + maxSteps + ")");
			}
			return String.join("\n", results);
		} catch (Exception e) {
			state = AgentState.ERROR;
			log.error("Error executing agent", e);
			return "执行错误: " + e.getMessage();
		} finally {
			cleanup();
		}
	}

	/**
	 * 异步流式运行代理，通过 SSE 逐步推送每个步骤结果。
	 *
	 * @param userPrompt 用户提示词
	 * @return SseEmitter 实例（5分钟超时）
	 */
	public SseEmitter runStream(String userPrompt) {
		SseEmitter emitter = new SseEmitter(300_000L);

		emitter.onTimeout(() -> {
			state = AgentState.ERROR;
			log.warn("SSE 连接超时");
			emitter.complete();
		});

		emitter.onCompletion(() -> {
			if (state == AgentState.RUNNING) {
				state = AgentState.FINISHED;
			}
			log.info("SSE 连接已关闭");
		});

		CompletableFuture.runAsync(() -> {
			try {
				validateBeforeRun(userPrompt);
				state = AgentState.RUNNING;
				messageList.add(new UserMessage(userPrompt));

				for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
					currentStep = i + 1;
					log.info("Executing step {}/{}", currentStep, maxSteps);
					sendSseMessage(emitter, "Step " + currentStep + ": " + step());
				}

				if (state != AgentState.FINISHED) {
					state = AgentState.FINISHED;
					sendSseMessage(emitter, "执行结束: 达到最大步骤 (" + maxSteps + ")");
				}
				emitter.complete();
			} catch (Exception e) {
				state = AgentState.ERROR;
				log.error("执行智能体失败", e);
				try {
					sendSseMessage(emitter, "执行错误: " + e.getMessage());
					emitter.complete();
				} catch (Exception ex) {
					emitter.completeWithError(ex);
				}
			} finally {
				cleanup();
			}
		});

		return emitter;
	}

	/**
	 * 执行单个步骤，由子类实现具体逻辑。
	 *
	 * @return 步骤执行结果
	 */
	public abstract String step();

	/**
	 * 运行前校验，状态非 IDLE 或提示词为空时抛出异常。
	 */
	private void validateBeforeRun(String userPrompt) {
		if (state != AgentState.IDLE) {
			throw new IllegalStateException("Cannot run agent from state: " + state);
		}
		if (StrUtil.isBlank(userPrompt)) {
			throw new IllegalArgumentException("Cannot run agent with empty user prompt");
		}
	}

	/** 发送标准 SSE 格式消息 */
	private void sendSseMessage(SseEmitter emitter, String message) throws IOException {
		emitter.send(SseEmitter.event().data(message));
	}

	/**
	 * 清理资源，子类可重写。
	 */
	protected void cleanup() {
	}
}

