package com.axin.axinagent.agent.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 任务计划：规划器输出的结构化执行计划，包含有序子任务列表
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskPlan {

    /**
     * 原始用户任务描述
     */
    private String originalTask;

    /**
     * 任务摘要分析（规划器对任务的理解）
     */
    private String summary;

    /**
     * 有序子任务列表（按 order 字段升序排列）
     */
    private List<SubTask> subTasks;
}
