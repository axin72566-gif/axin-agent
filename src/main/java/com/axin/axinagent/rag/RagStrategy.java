package com.axin.axinagent.rag;

/**
 * RAG 检索策略枚举
 */
public enum RagStrategy {

    /**
     * 本地 Redis Stack 向量存储检索
     */
    LOCAL,

    /**
     * 云端 DashScope 知识库检索
     */
    CLOUD
}
