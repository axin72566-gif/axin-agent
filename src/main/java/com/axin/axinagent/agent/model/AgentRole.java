package com.axin.axinagent.agent.model;

/**
 * Agent 角色枚举：多 Agent 协作中每个 Agent 的专长定位
 */
public enum AgentRole {

    /**
     * 信息收集：专注搜索、抓取、汇总信息
     */
    RESEARCH,

    /**
     * 内容生成：专注写作、分析、输出结构化内容
     */
    WRITER,

    /**
     * 通用任务：无法明确分类的复杂任务，由 AxinManus 处理
     */
    MANUS
}
