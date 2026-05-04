package com.axin.axinagent.task;

import com.axin.axinagent.advisor.LogAdvisor;
import com.axin.axinagent.advisor.TraceTokenAdvisor;
import com.axin.axinagent.agent.AxinManus;
import com.axin.axinagent.agent.OrchestratorAgent;
import com.axin.axinagent.agent.PlannerAgent;
import com.axin.axinagent.agent.model.AgentState;
import com.axin.axinagent.config.RocketMqConfig;
import com.axin.axinagent.llm.ModelRouter;
import com.axin.axinagent.llm.ModelType;
import com.axin.axinagent.observability.AgentTraceService;
import com.axin.axinagent.observability.TaskContext;
import com.axin.axinagent.observability.model.TraceStepEntry;
import com.axin.axinagent.task.model.AgentTaskMessage;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

/**
 * RocketMQ Agent 任务消费者。
 * <p>
 * 消费 AGENT_TASK_TOPIC 中的消息，构建带进度回调的 AxinManus，
 * 每步执行后将结果推送至 Redis 进度缓冲 List，支持协作式取消。
 * <p>
 * 状态流转：PENDING → RUNNING → FINISHED / ERROR / CANCELLED / TIMEOUT
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = RocketMqConfig.AGENT_TASK_TOPIC,
        consumerGroup = RocketMqConfig.AGENT_TASK_CONSUMER_GROUP
)
public class AgentTaskConsumer implements RocketMQListener<AgentTaskMessage> {

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ModelRouter modelRouter;

    @Resource
    private AgentTaskService agentTaskService;

    @Resource
    private PlannerAgent plannerAgent;

    @Resource
    private LogAdvisor logAdvisor;

    @Resource
    private TraceTokenAdvisor traceTokenAdvisor;

    @Resource
    private AgentTraceService agentTraceService;

    @Override
    public void onMessage(AgentTaskMessage taskMessage) {
        String taskId = taskMessage.getTaskId();
        String mode = taskMessage.getMode() == null ? "SINGLE" : taskMessage.getMode();
        log.info("开始消费任务: taskId={}, mode={}", taskId, mode);

        TaskContext.setTaskId(taskId);

        // 1. 更新状态为 RUNNING
        agentTaskService.updateTaskState(taskId, AgentState.RUNNING, null);

        try {
            String taskResult;
            if ("MULTI".equalsIgnoreCase(mode)) {
                // 多 Agent 协作模式
                taskResult = runMultiAgentTask(taskId, taskMessage.getMessage());
            } else {
                // 单 Agent 模式（默认）
                taskResult = runSingleAgentTask(taskId, taskMessage.getMessage());
            }

            agentTaskService.updateTaskState(taskId, AgentState.FINISHED, null, taskResult);
            log.info("任务执行完成: taskId={}", taskId);

        } catch (TaskCancelledException e) {
            agentTaskService.updateTaskState(taskId, AgentState.CANCELLED, "任务已被用户取消");
            log.info("任务被取消中断: taskId={}", taskId);
        } catch (Exception e) {
            agentTaskService.updateTaskState(taskId, AgentState.ERROR, e.getMessage());
            log.error("任务执行异常: taskId={}", taskId, e);
            agentTaskService.pushProgress(taskId, "执行错误: " + e.getMessage());
        } finally {
            TaskContext.removeTaskId();
            // 推送结束标记，通知 SSE 侧关闭连接
            agentTaskService.pushProgress(taskId, RocketMqConfig.PROGRESS_END_MARKER);
        }
    }

    /**
     * 单 Agent 模式：构建带进度推送和取消检测的 AxinManus 实例执行任务。
     */
    private String runSingleAgentTask(String taskId, String message) {
        AxinManus axinManus = buildProgressAwareAgent(taskId);
        String result = axinManus.run(message);

        AgentState finalState = axinManus.getState();
        if (finalState == AgentState.CANCELLED) {
            agentTaskService.updateTaskState(taskId, AgentState.CANCELLED, "任务已被用户取消");
            log.info("任务已取消: taskId={}", taskId);
            throw new TaskCancelledException("任务 " + taskId + " 已被取消");
        }
        return result;
    }

    /**
     * 多 Agent 协作模式：通过 OrchestratorAgent 执行任务，进度实时推送至 Redis。
     */
    private String runMultiAgentTask(String taskId, String message) {
        OrchestratorAgent orchestrator = new OrchestratorAgent(
                plannerAgent,
                allTools,
                modelRouter.get(ModelType.PRIMARY),
                logAdvisor,
                traceTokenAdvisor,
                agentTraceService
        );

        return orchestrator.execute(message, progressMsg -> {
            // 每个进度消息前检测取消标记
            if (agentTaskService.isCancelled(taskId)) {
                throw new TaskCancelledException("任务 " + taskId + " 已被取消");
            }
            agentTaskService.pushProgress(taskId, progressMsg);
        });
    }

    /**
     * 构建支持进度推送和取消检测的 AxinManus 实例。
     * 重写 step() 方法，在每步执行后将结果推送至 Redis Progress List，
     * 同时在每步开始前检测取消标记。
     *
     * @param taskId 任务 ID
     * @return 增强的 AxinManus 实例
     */
    private AxinManus buildProgressAwareAgent(String taskId) {
        return new AxinManus(
                allTools,
                modelRouter.get(ModelType.PRIMARY),
                logAdvisor,
                traceTokenAdvisor,
                agentTraceService
        ) {

            @Override
            public String step() {
                long start = System.currentTimeMillis();
                // 每步执行前检测取消标记
                if (agentTaskService.isCancelled(taskId)) {
                    setState(AgentState.CANCELLED);
                    throw new TaskCancelledException("任务 " + taskId + " 已被取消");
                }

                boolean success = true;
                String stepResult;
                try {
                    // 执行实际步骤
                    stepResult = super.step();
                } catch (RuntimeException e) {
                    success = false;
                    throw e;
                } finally {
                    long end = System.currentTimeMillis();
                    TraceStepEntry stepEntry = TraceStepEntry.builder()
                            .taskId(taskId)
                            .step(getCurrentStep())
                            .type("STEP")
                            .startTime(start)
                            .endTime(end)
                            .durationMs(end - start)
                            .success(success)
                            .summary("Agent step loop")
                            .build();
                    agentTraceService.recordStep(stepEntry);
                }

                // 将步骤结果推送至 Redis 进度缓冲
                agentTaskService.pushProgress(taskId, stepResult);

                return stepResult;
            }
        };
    }

    /**
     * 任务取消异常，用于协作式取消时中断 Agent 执行循环。
     */
    static class TaskCancelledException extends RuntimeException {
        public TaskCancelledException(String message) {
            super(message);
        }
    }
}
