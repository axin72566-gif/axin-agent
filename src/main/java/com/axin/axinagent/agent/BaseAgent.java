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
 * 抽象基础代理类，用于管理代理状态和执行流程。
 * 提供状态转换、内存管理和基于步骤的执行循环的基础功能。
 * 子类必须实现step方法。
 */
@Data
@Slf4j
public abstract class BaseAgent {

	// 核心属性
	private String name;

	// 提示
	private String systemPrompt;
	private String nextStepPrompt;

	// 状态
	private AgentState state = AgentState.IDLE;

	// 执行控制
	private int maxSteps = 10;
	private int currentStep = 0;

	// LLM
	private ChatClient chatClient;

	// Memory（需要自主维护会话上下文）
	private List<Message> messageList = new ArrayList<>();

	/**
	 * 运行代理
	 *
	 * @param userPrompt 用户提示词
	 * @return 执行结果
	 */
	public String run(String userPrompt) {
		if (this.state != AgentState.IDLE) {
			throw new RuntimeException("Cannot run agent from state: " + this.state);
		}
		if (StrUtil.isBlank(userPrompt)) {
			throw new RuntimeException("Cannot run agent with empty user prompt");
		}
		// 更改状态
		state = AgentState.RUNNING;
		// 记录消息上下文
		messageList.add(new UserMessage(userPrompt));
		// 保存结果列表
		List<String> results = new ArrayList<>();
		try {
			for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
				int stepNumber = i + 1;
				currentStep = stepNumber;
				log.info("Executing step {}/{}", stepNumber, maxSteps);
				// 单步执行
				String stepResult = step();
				String result = "Step " + stepNumber + ": " + stepResult;
				results.add(result);
			}
			// 检查是否超出步骤限制
			if (currentStep >= maxSteps) {
				state = AgentState.FINISHED;
				results.add("Terminated: Reached max steps (" + maxSteps + ")");
			}
			return String.join("\n", results);
		} catch (Exception e) {
			state = AgentState.ERROR;
			log.error("Error executing agent", e);
			return "执行错误" + e.getMessage();
		} finally {
			// 清理资源
			this.cleanup();
		}
	}

	/**
	 * 运行代理（流式输出）
	 *
	 * @param userPrompt 用户提示词
	 * @return SseEmitter实例
	 */
	public SseEmitter runStream(String userPrompt) {
		// 1. 创建 SSE 发射器（5分钟超时）
		SseEmitter emitter = new SseEmitter(300000L);

		// 2. 异步执行（不阻塞主线程）
		CompletableFuture.runAsync(() -> {
			try {
				// 状态校验
				if (this.state != AgentState.IDLE) {
					sendSseMessage(emitter, "错误：无法从状态运行代理: " + this.state);
					return;
				}

				// 参数校验
				if (StrUtil.isBlank(userPrompt)) {
					sendSseMessage(emitter, "错误：不能使用空提示词运行代理");
					return;
				}

				// 初始化状态
				this.state = AgentState.RUNNING;
				this.messageList.add(new UserMessage(userPrompt));

				// 循环执行步骤
				for (int i = 0; i < maxSteps && this.state != AgentState.FINISHED; i++) {
					currentStep = i + 1;
					log.info("Executing step {}/{}", currentStep, maxSteps);

					// 执行单步
					String stepResult = step();
					sendSseMessage(emitter, "Step " + currentStep + ": " + stepResult);
				}

				// 结束判断
				if (currentStep >= maxSteps) {
					this.state = AgentState.FINISHED;
					sendSseMessage(emitter, "执行结束: 达到最大步骤 (" + maxSteps + ")");
				}

				// 正常完成
				emitter.complete();

			} catch (Exception e) {
				// 异常处理
				this.state = AgentState.ERROR;
				log.error("执行智能体失败", e);
				try {
					sendSseMessage(emitter, "执行错误: " + e.getMessage());
					emitter.complete();
				} catch (Exception ex) {
					emitter.completeWithError(ex);
				}
			} finally {
				// 统一资源释放
				this.cleanup();
			}
		});

		// 3. 连接超时回调
		emitter.onTimeout(() -> {
			this.state = AgentState.ERROR;
			log.warn("SSE 连接超时");
			emitter.complete();
		});

		// 4. 连接关闭回调
		emitter.onCompletion(() -> {
			if (this.state == AgentState.RUNNING) {
				this.state = AgentState.FINISHED;
			}
			log.info("SSE 连接已关闭");
		});

		return emitter;
	}

	/**
	 * 发送标准 SSE 格式消息
	 */
	private void sendSseMessage(SseEmitter emitter, String message) throws IOException {
		emitter.send(SseEmitter.event().data(message));
	}


	/**
	 * 执行单个步骤
	 *
	 * @return 步骤执行结果
	 */
	public abstract String step();

	/**
	 * 清理资源
	 */
	protected void cleanup() {
		// 子类可以重写此方法来清理资源
	}
}

