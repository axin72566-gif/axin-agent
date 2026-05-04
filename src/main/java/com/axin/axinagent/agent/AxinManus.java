package com.axin.axinagent.agent;

import com.axin.axinagent.advisor.LogAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 通用 AI Agent，整合所有可用工具，自主规划并完成复杂任务。
 */
@Component
public class AxinManus extends ToolCallAgent {

    private static final String SYSTEM_PROMPT = """
            You are AxinManus, an all-capable AI assistant, aimed at solving any task presented by the user.
            You have various tools at your disposal that you can call upon to efficiently complete complex requests.
            Reply in Chinese unless the user speaks another language.
            """;

    private static final String NEXT_STEP_PROMPT = """
            Based on user needs, proactively select the most appropriate tool or combination of tools.
            For complex tasks, you can break down the problem and use different tools step by step to solve it.
            After using each tool, clearly explain the execution results and suggest the next steps.
            If you want to stop the interaction at any point, use the `terminate` tool/function call.
            """;

    public AxinManus(@Qualifier("allTools") ToolCallback[] allTools,
                     ChatModel dashscopeChatModel,
                     LogAdvisor logAdvisor) {
        super(allTools);
        setName("AxinManus");
        setSystemPrompt(SYSTEM_PROMPT);
        setNextStepPrompt(NEXT_STEP_PROMPT);
        setMaxSteps(20);

        ChatClient.Builder builder = ChatClient.builder(dashscopeChatModel);
        if (logAdvisor != null) {
            builder.defaultAdvisors(logAdvisor);
        } else {
            builder.defaultAdvisors(new LogAdvisor());
        }

        setChatClient(builder.build());
    }
}
