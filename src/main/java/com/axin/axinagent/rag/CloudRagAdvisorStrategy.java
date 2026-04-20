package com.axin.axinagent.rag;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.stereotype.Component;

/**
 * 云端 DashScope 知识库检索策略
 */
@Component
public class CloudRagAdvisorStrategy implements RagAdvisorStrategy {

    /**
     * 注入由 LoveAppRagCloudAdvisor 配置类生成的 Advisor Bean
     */
    @Resource
    private Advisor loveAppRagCloudAdvisor;

    @Override
    public RagStrategy getStrategy() {
        return RagStrategy.CLOUD;
    }

    @Override
    public Advisor getAdvisor() {
        return loveAppRagCloudAdvisor;
    }
}
