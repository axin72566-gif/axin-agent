package com.axin.axinagent.agent.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 子任务模型：规划器分解大任务后产生的最小执行单元
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubTask {

    /**
     * 子任务序号（从 1 开始，决定执行顺序）
     */
    private int order;

    /**
     * 子任务标题（简洁描述）
     */
    private String title;

    /**
     * 子任务详细描述
     */
    private String description;

    /**
     * 执行该子任务的 Agent 类型
     * 可选值：RESEARCH（信息收集）、WRITER（内容生成）、MANUS（通用）
     */
    private AgentRole agentRole;

    /**
     * 依赖的子任务序号列表（逗号分隔，空字符串表示无依赖）
     */
    private String dependsOn;
}
