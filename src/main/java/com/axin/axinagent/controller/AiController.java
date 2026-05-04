package com.axin.axinagent.controller;

import com.axin.axinagent.advisor.LogAdvisor;
import com.axin.axinagent.advisor.TraceTokenAdvisor;
import com.axin.axinagent.agent.AxinManus;
import com.axin.axinagent.agent.OrchestratorAgent;
import com.axin.axinagent.agent.PlannerAgent;
import com.axin.axinagent.app.LoveApp;
import com.axin.axinagent.common.BaseResponse;
import com.axin.axinagent.common.ResultUtils;
import com.axin.axinagent.infrastructure.db.entity.AgentTaskEntity;
import com.axin.axinagent.llm.ModelRouter;
import com.axin.axinagent.llm.ModelType;
import com.axin.axinagent.observability.AgentTraceService;
import com.axin.axinagent.task.AgentTaskService;
import com.axin.axinagent.task.ProgressSseService;
import com.axin.axinagent.task.model.AgentTaskStatus;
import com.axin.axinagent.task.model.TaskSubmitResponse;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.annotation.Resource;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;

@RestController
@RequestMapping("/ai")
public class AiController {

    @Resource
    private LoveApp loveApp;

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ModelRouter modelRouter;

    @Resource
    private AgentTaskService agentTaskService;

    @Resource
    private ProgressSseService progressSseService;

    @Resource
    private PlannerAgent plannerAgent;

    @Resource
    private LogAdvisor logAdvisor;

    @Resource
    private TraceTokenAdvisor traceTokenAdvisor;

    @Resource
    private AgentTraceService agentTraceService;

    /**
     * 同步聊天
     *
     * @param message 用户消息
     * @param chatId  会话 ID
     * @return 模型回复
     */
    @GetMapping("/love_app/chat/sync")
    public String doChatWithLoveAppSync(String message, String chatId) {
        return loveApp.doChat(message, chatId);
    }

    /**
     * 流式聊天（SSE，MediaType 方式）
     *
     * @param message 用户消息
     * @param chatId  会话 ID
     * @return Flux 文本流
     */
    @GetMapping(value = "/love_app/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithLoveAppSSE(String message, String chatId) {
        return loveApp.doChatByStream(message, chatId);
    }

    /**
     * 流式聊天（SseEmitter 方式，适合需要手动控制 SSE 生命周期的场景）
     *
     * @param message 用户消息
     * @param chatId  会话 ID
     * @return SseEmitter 实例
     */
    @GetMapping("/love_app/chat/sse/emitter")
    public SseEmitter doChatWithLoveAppSseEmitter(String message, String chatId) {
        SseEmitter emitter = new SseEmitter(180_000L);
        loveApp.doChatByStream(message, chatId)
                .subscribe(
                        chunk -> {
                            try {
                                emitter.send(chunk);
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        },
                        emitter::completeWithError,
                        emitter::complete
                );
        return emitter;
    }

    /**
     * 流式调用 Manus 超级智能体（同步直连，保留原接口）
     *
     * @param message 用户消息
     * @return SseEmitter 实例
     */
    @GetMapping("/manus/chat")
    public SseEmitter doChatWithManus(String message) {
        AxinManus axinManus = new AxinManus(
                allTools,
                modelRouter.get(ModelType.PRIMARY),
                logAdvisor,
                traceTokenAdvisor,
                agentTraceService
        );
        return axinManus.runStream(message);
    }

    // ======================== 任务调度层接口（单 Agent）========================

    /**
     * 提交 Agent 异步任务（单 Agent 模式）。
     * 任务投递到 RocketMQ 后立即返回 taskId，客户端使用 taskId 订阅进度。
     *
     * @param message 用户消息
     * @return 任务提交响应（含 taskId）
     */
    @PostMapping("/manus/task/submit")
    public BaseResponse<TaskSubmitResponse> submitTask(@RequestParam String message) {
        TaskSubmitResponse response = agentTaskService.submitTask(message, "SINGLE");
        return ResultUtils.success(response);
    }

    /**
     * 查询任务当前状态。
     *
     * @param taskId 任务 ID
     * @return 任务状态信息
     */
    @GetMapping("/manus/task/status/{taskId}")
    public BaseResponse<?> getTaskStatus(@PathVariable String taskId) {
        AgentTaskStatus status = agentTaskService.getTaskStatus(taskId);
        if (status == null) {
            return ResultUtils.error(404, "任务不存在: " + taskId);
        }
        return ResultUtils.success(status);
    }

    /**
     * 查询任务历史列表（DB 分页，按更新时间倒序）。
     */
    @GetMapping("/manus/task/history")
    public BaseResponse<IPage<AgentTaskEntity>> listTaskHistory(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long pageSize) {
        return ResultUtils.success(agentTaskService.listTaskHistory(current, pageSize));
    }

    /**
     * 订阅任务实时执行进度（SSE 长连接）。
     * 服务端实时推送每步执行结果，任务完成时推送结束标记并关闭连接。
     *
     * @param taskId 任务 ID
     * @return SseEmitter 实例
     */
    @GetMapping("/manus/task/progress/{taskId}")
    public SseEmitter subscribeTaskProgress(@PathVariable String taskId) {
        return progressSseService.subscribeProgress(taskId);
    }

    /**
     * 取消正在执行的任务。
     * 向 Redis 写入取消标记，消费端在下一步执行前检测并停止。
     *
     * @param taskId 任务 ID
     * @return 操作结果
     */
    @PostMapping("/manus/task/cancel/{taskId}")
    public BaseResponse<?> cancelTask(@PathVariable String taskId) {
        boolean success = agentTaskService.cancelTask(taskId);
        if (!success) {
            return ResultUtils.error(404, "任务不存在: " + taskId);
        }
        return ResultUtils.success("取消请求已发送，任务将在当前步骤完成后停止");
    }

    // ======================== Agent 执行层接口（多 Agent 协作）========================

    /**
     * 提交多 Agent 协作异步任务。
     * 先由 PlannerAgent 分解任务，再由 OrchestratorAgent 协调多 Agent 执行，
     * 通过 RocketMQ 异步化，立即返回 taskId，客户端订阅进度。
     *
     * @param message 用户消息
     * @return 任务提交响应（含 taskId）
     */
    @PostMapping("/orchestrator/task/submit")
    public BaseResponse<TaskSubmitResponse> submitMultiAgentTask(@RequestParam String message) {
        TaskSubmitResponse response = agentTaskService.submitTask(message, "MULTI");
        return ResultUtils.success(response);
    }

    /**
     * 同步执行多 Agent 协作任务（SSE 流式返回，适合调试）。
     * 直接在请求线程执行，通过 SSE 实时推送规划和每步进度。
     *
     * @param message 用户消息
     * @return SseEmitter 实例
     */
    @GetMapping("/orchestrator/chat")
    public SseEmitter doChatWithOrchestrator(String message) {
        SseEmitter emitter = new SseEmitter(600_000L);

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                OrchestratorAgent orchestrator = new OrchestratorAgent(
                        plannerAgent,
                        allTools,
                        modelRouter.get(ModelType.PRIMARY),
                        logAdvisor,
                        traceTokenAdvisor,
                        agentTraceService
                );
                orchestrator.execute(message, progressMsg -> {
                    try {
                        emitter.send(SseEmitter.event().data(progressMsg));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().data("执行错误: " + e.getMessage()));
                    emitter.complete();
                } catch (IOException ex) {
                    emitter.completeWithError(ex);
                }
            }
        });

        return emitter;
    }
}
