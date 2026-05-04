package com.axin.axinagent.agent;

import com.axin.axinagent.advisor.LogAdvisor;
import com.axin.axinagent.advisor.TraceTokenAdvisor;
import com.axin.axinagent.agent.model.AgentRole;
import com.axin.axinagent.agent.model.SubTask;
import com.axin.axinagent.agent.model.TaskPlan;
import com.axin.axinagent.observability.AgentTraceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 主控 Agent（Orchestrator）：协调多 Agent 协作完成复杂任务。
 */
@Slf4j
public class OrchestratorAgent {

    /** 进度回调接口，用于任务调度层（RocketMQ 消费端）推送实时进度 */
    public interface ProgressCallback {
        void onProgress(String message);
    }

    private final PlannerAgent plannerAgent;
    private final ToolCallback[] allTools;
    private final ToolCallback[] searchTools;
    private final ToolCallback[] writerTools;
    private final ChatModel chatModel;
    private final LogAdvisor logAdvisor;
    private final TraceTokenAdvisor traceTokenAdvisor;
    private final AgentTraceService agentTraceService;

    /** 搜索类工具名称（用于过滤出 ResearchAgent 工具集） */
    private static final List<String> SEARCH_TOOL_NAMES = List.of(
            "webSearchTool", "webScrapingTool", "terminateTool"
    );

    /** 写作类工具名称（用于过滤出 WriterAgent 工具集） */
    private static final List<String> WRITER_TOOL_NAMES = List.of(
            "pdfGenerationTool", "fileSystemTool", "terminateTool"
    );

    public OrchestratorAgent(PlannerAgent plannerAgent,
                             ToolCallback[] allTools,
                             ChatModel chatModel) {
        this(plannerAgent, allTools, chatModel, null, null, null);
    }

    public OrchestratorAgent(PlannerAgent plannerAgent,
                             ToolCallback[] allTools,
                             ChatModel chatModel,
                             LogAdvisor logAdvisor,
                             TraceTokenAdvisor traceTokenAdvisor,
                             AgentTraceService agentTraceService) {
        this.plannerAgent = plannerAgent;
        this.allTools = allTools;
        this.chatModel = chatModel;
        this.logAdvisor = logAdvisor;
        this.traceTokenAdvisor = traceTokenAdvisor;
        this.agentTraceService = agentTraceService;
        this.searchTools = filterTools(allTools, SEARCH_TOOL_NAMES);
        this.writerTools = filterTools(allTools, WRITER_TOOL_NAMES);
    }

    /**
     * 执行多 Agent 协作任务（同步阻塞）。
     */
    public String execute(String userTask) {
        return execute(userTask, null);
    }

    /**
     * 执行多 Agent 协作任务，支持进度回调。
     */
    public String execute(String userTask, ProgressCallback callback) {
        log.info("[Orchestrator] 开始执行任务: {}", userTask);
        pushProgress(callback, "正在规划任务...");

        // Step 1: 规划任务
        TaskPlan plan = plannerAgent.plan(userTask);
        pushProgress(callback, String.format("任务规划完成，共 %d 个子任务：%s",
                plan.getSubTasks().size(), plan.getSummary()));

        log.info("[Orchestrator] 任务规划完成: {}", plan.getSummary());
        plan.getSubTasks().forEach(t ->
                log.info("  子任务 {}: [{}] {} -> {}", t.getOrder(), t.getAgentRole(), t.getTitle(), t.getDescription()));

        // Step 2: 按顺序执行子任务，存储结果 (order -> result)
        Map<Integer, String> resultMap = new LinkedHashMap<>();
        List<String> allResults = new ArrayList<>();

        for (SubTask subTask : plan.getSubTasks()) {
            // 检查依赖是否已完成
            if (!checkDependencies(subTask, resultMap)) {
                String errMsg = String.format("子任务 %d [%s] 依赖未满足，跳过执行", subTask.getOrder(), subTask.getTitle());
                log.warn("[Orchestrator] {}", errMsg);
                pushProgress(callback, errMsg);
                resultMap.put(subTask.getOrder(), "跳过：依赖未满足");
                continue;
            }

            // 构建子任务 prompt（注入前置结果作为上下文）
            String subTaskPrompt = buildSubTaskPrompt(subTask, resultMap);

            pushProgress(callback, String.format("▶ 子任务 %d/%d [%s]: %s",
                    subTask.getOrder(), plan.getSubTasks().size(),
                    subTask.getAgentRole(), subTask.getTitle()));
            log.info("[Orchestrator] 执行子任务 {}: [{}] {}", subTask.getOrder(), subTask.getAgentRole(), subTask.getTitle());

            // Step 3: 分派给对应 Agent 执行
            String result = dispatchSubTask(subTask, subTaskPrompt, callback);
            resultMap.put(subTask.getOrder(), result);

            String progressMsg = String.format("✅ 子任务 %d 完成: %s", subTask.getOrder(), subTask.getTitle());
            pushProgress(callback, progressMsg);
            allResults.add(String.format("=== 子任务 %d：%s ===\n%s", subTask.getOrder(), subTask.getTitle(), result));
            log.info("[Orchestrator] 子任务 {} 执行完成", subTask.getOrder());
        }

        // Step 4: 汇总结果
        String finalResult = buildFinalReport(plan, allResults);
        pushProgress(callback, "所有子任务执行完毕，任务已完成。");
        log.info("[Orchestrator] 所有子任务执行完成");
        return finalResult;
    }

    /**
     * 根据子任务角色分派给对应 Agent 执行。
     */
    private String dispatchSubTask(SubTask subTask, String prompt, ProgressCallback callback) {
        AgentRole role = subTask.getAgentRole();
        if (role == null) {
            role = AgentRole.MANUS;
        }

        try {
            return switch (role) {
                case RESEARCH -> {
                    log.info("[Orchestrator] 分派给 ResearchAgent: {}", subTask.getTitle());
                    ResearchAgent agent = new ResearchAgent(searchTools, chatModel);
                    yield agent.run(prompt);
                }
                case WRITER -> {
                    log.info("[Orchestrator] 分派给 WriterAgent: {}", subTask.getTitle());
                    WriterAgent agent = new WriterAgent(writerTools, chatModel);
                    yield agent.run(prompt);
                }
                case MANUS -> {
                    log.info("[Orchestrator] 分派给 AxinManus: {}", subTask.getTitle());
                    AxinManus agent = new AxinManus(allTools, chatModel, logAdvisor, traceTokenAdvisor, agentTraceService);
                    yield agent.run(prompt);
                }
            };
        } catch (Exception e) {
            log.error("[Orchestrator] 子任务 {} 执行异常: {}", subTask.getOrder(), e.getMessage(), e);
            return "执行异常: " + e.getMessage();
        }
    }

    /**
     * 构建子任务的执行 prompt，将前置任务结果作为上下文注入。
     */
    private String buildSubTaskPrompt(SubTask subTask, Map<Integer, String> resultMap) {
        StringBuilder promptBuilder = new StringBuilder();

        // 注入前置任务结果（上下文）
        String dependsOn = subTask.getDependsOn();
        if (dependsOn != null && !dependsOn.isBlank()) {
            promptBuilder.append("=== 前置任务研究成果（请参考以下内容完成当前任务）===\n");
            Arrays.stream(dependsOn.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(orderStr -> {
                        try {
                            int depOrder = Integer.parseInt(orderStr);
                            String depResult = resultMap.get(depOrder);
                            if (depResult != null) {
                                promptBuilder.append(String.format("【子任务 %d 结果】\n%s\n\n", depOrder, depResult));
                            }
                        } catch (NumberFormatException ignored) {
                            // 忽略格式错误的依赖序号
                        }
                    });
            promptBuilder.append("=== 当前任务 ===\n");
        }

        promptBuilder.append(subTask.getDescription());
        return promptBuilder.toString();
    }

    /**
     * 检查子任务的依赖是否已全部完成。
     */
    private boolean checkDependencies(SubTask subTask, Map<Integer, String> resultMap) {
        String dependsOn = subTask.getDependsOn();
        if (dependsOn == null || dependsOn.isBlank()) {
            return true;
        }
        return Arrays.stream(dependsOn.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .allMatch(orderStr -> {
                    try {
                        int depOrder = Integer.parseInt(orderStr);
                        return resultMap.containsKey(depOrder);
                    } catch (NumberFormatException e) {
                        return true; // 格式错误的依赖视为已满足
                    }
                });
    }

    /**
     * 构建最终汇总报告。
     */
    private String buildFinalReport(TaskPlan plan, List<String> allResults) {
        StringBuilder report = new StringBuilder();
        report.append("=== 多 Agent 协作执行报告 ===\n");
        report.append("原始任务：").append(plan.getOriginalTask()).append("\n");
        report.append("任务摘要：").append(plan.getSummary()).append("\n\n");
        report.append(String.join("\n\n", allResults));
        return report.toString();
    }

    /**
     * 按工具名称过滤工具集合。
     */
    private ToolCallback[] filterTools(ToolCallback[] tools, List<String> targetNames) {
        return Arrays.stream(tools)
                .filter(tool -> {
                    String name = tool.getToolDefinition().name();
                    return targetNames.stream().anyMatch(name::equalsIgnoreCase);
                })
                .toArray(ToolCallback[]::new);
    }

    /**
     * 推送进度消息（null 安全）。
     */
    private void pushProgress(ProgressCallback callback, String message) {
        if (callback != null) {
            try {
                callback.onProgress(message);
            } catch (Exception e) {
                log.warn("[Orchestrator] 进度回调失败: {}", e.getMessage());
            }
        }
    }
}
