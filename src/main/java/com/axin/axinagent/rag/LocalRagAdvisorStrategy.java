package com.axin.axinagent.rag;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * 本地 Redis Stack 向量检索策略
 */
@Component
@ConditionalOnBean(name = "loveAppRagLocalAdvisor")
public class LocalRagAdvisorStrategy implements RagAdvisorStrategy {

    /**
     * 注入由 LoveAppRagLocalAdvisor 配置类生成的 Advisor Bean
     */
    @Resource
    private Advisor loveAppRagLocalAdvisor;

    @Override
    public RagStrategy getStrategy() {
        return RagStrategy.LOCAL;
    }

    @Override
    public Advisor getAdvisor() {
        return loveAppRagLocalAdvisor;
    }
}
