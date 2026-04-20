package com.axin.axinagent.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 基于 Redis Stack 向量存储的本地 RAG Advisor
 * 使用 VectorStoreDocumentRetriever 从 Redis 检索相似文档，增强 LLM 回答
 */
@Slf4j
@Configuration("loveAppRagLocalAdvisorConfig")
public class LoveAppRagLocalAdvisor {

    @Bean
    @ConditionalOnBean(VectorStore.class)
    public Advisor loveAppRagLocalAdvisor(VectorStore vectorStore) {
        // 构建向量检索器：从 Redis 中相似度检索 Top-4 文档
        VectorStoreDocumentRetriever retriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .topK(4)
                .build();

        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(retriever)
                .build();
    }
}
