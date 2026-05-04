package com.axin.axinagent.agent;

import com.axin.axinagent.agent.model.AgentRole;
import com.axin.axinagent.agent.model.SubTask;
import com.axin.axinagent.agent.model.TaskPlan;
import com.axin.axinagent.llm.ModelRouter;
import com.axin.axinagent.llm.ModelType;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务规划器：将复杂用户任务分解为有序子任务列表。
 * <p>
 * 调用 LLM 对原始任务进行分析，输出结构化 JSON 格式的执行计划，
 * 每个子任务标注执行角色（RESEARCH / WRITER / MANUS）和依赖关系。
 */
@Slf4j
@Component
public class PlannerAgent {

    private static final String PLANNER_SYSTEM_PROMPT = """
            You are a task planning expert. Your job is to analyze a complex user task and decompose it into an ordered list of sub-tasks.
            
            Rules:
            1. Break the task into 2-6 focused sub-tasks.
            2. Each sub-task must have a clear, actionable description.
            3. Assign an agentRole to each sub-task:
               - RESEARCH: for information gathering, web search, data collection
               - WRITER: for content generation, writing reports, analysis output
               - MANUS: for complex tasks that don't fit RESEARCH or WRITER
            4. Set dependsOn as empty string if no dependency, or comma-separated order numbers (e.g., "1,2") if depends on previous tasks.
            5. Return ONLY valid JSON in the exact format below, no markdown, no extra text.
            
            Output format:
            {
              "originalTask": "<user's original task>",
              "summary": "<your analysis of what needs to be done>",
              "subTasks": [
                {
                  "order": 1,
                  "title": "<short title>",
                  "description": "<detailed description of what to do>",
                  "agentRole": "RESEARCH",
                  "dependsOn": ""
                },
                {
                  "order": 2,
                  "title": "<short title>",
                  "description": "<detailed description>",
                  "agentRole": "WRITER",
                  "dependsOn": "1"
                }
              ]
            }
            """;

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public PlannerAgent(ModelRouter modelRouter) {
        this.chatClient = ChatClient.builder(modelRouter.get(ModelType.LIGHT)).build();
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * 对用户任务进行分解规划。
     *
     * @param userTask 用户原始任务描述
     * @return 结构化任务计划；若解析失败则返回单步兜底计划
     */
    public TaskPlan plan(String userTask) {
        log.info("[Planner] 开始规划任务: {}", userTask);
        try {
            String jsonResponse = chatClient.prompt()
                    .system(PLANNER_SYSTEM_PROMPT)
                    .user(userTask)
                    .call()
                    .content();

            log.info("[Planner] LLM 规划结果: {}", jsonResponse);

            // 提取 JSON（兼容 LLM 可能包裹 ```json ... ``` 的情况）
            String cleanJson = extractJson(jsonResponse);
            TaskPlan plan = objectMapper.readValue(cleanJson, TaskPlan.class);

            log.info("[Planner] 任务分解完成，共 {} 个子任务", plan.getSubTasks().size());
            return plan;

        } catch (Exception e) {
            log.warn("[Planner] 规划解析失败，使用兜底单步计划: {}", e.getMessage());
            return buildFallbackPlan(userTask);
        }
    }

    /**
     * 提取 JSON 字符串，兼容 LLM 输出带 ```json ... ``` 代码块的情况。
     */
    private String extractJson(String raw) {
        if (raw == null) return "{}";
        String trimmed = raw.trim();
        // 去除 markdown 代码块包裹
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('\n');
            int end = trimmed.lastIndexOf("```");
            if (start >= 0 && end > start) {
                return trimmed.substring(start + 1, end).trim();
            }
        }
        // 直接找第一个 { 到最后一个 }
        int first = trimmed.indexOf('{');
        int last = trimmed.lastIndexOf('}');
        if (first >= 0 && last > first) {
            return trimmed.substring(first, last + 1);
        }
        return trimmed;
    }

    /**
     * 兜底计划：当规划器解析失败时，生成一个单步 MANUS 任务计划。
     */
    private TaskPlan buildFallbackPlan(String userTask) {
        List<SubTask> subTasks = new ArrayList<>();
        subTasks.add(new SubTask(1, "执行任务", userTask, AgentRole.MANUS, ""));
        return new TaskPlan(userTask, "直接执行原始任务", subTasks);
    }
}
