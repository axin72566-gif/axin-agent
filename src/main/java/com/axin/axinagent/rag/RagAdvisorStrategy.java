package com.axin.axinagent.rag;

import org.springframework.ai.chat.client.advisor.api.Advisor;

/**
 * RAG Advisor 策略接口
 * 每个实现对应一种具体的检索后端
 */
public interface RagAdvisorStrategy {

    /**
     * 返回当前策略支持的枚举类型
     */
    RagStrategy getStrategy();

    /**
     * 返回对应的 RAG Advisor
     */
    Advisor getAdvisor();
}
