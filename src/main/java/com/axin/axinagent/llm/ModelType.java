package com.axin.axinagent.llm;

/**
 * 模型类型。
 */
public enum ModelType {

    /** 主力模型：用于复杂推理、Agent ReAct 循环 */
    PRIMARY,

    /** 轻量模型：用于任务规划、分类、意图路由 */
    LIGHT
}
