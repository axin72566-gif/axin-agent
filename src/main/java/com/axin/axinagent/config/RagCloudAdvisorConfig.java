package com.axin.axinagent.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentRetriever;
import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentRetrieverOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 基于 DashScope 云端知识库的 RAG Advisor 配置
 */
@Slf4j
@Configuration
public class RagCloudAdvisorConfig {

	@Value("${spring.ai.dashscope.api-key}")
	private String dashScopeApiKey;

	@Bean
	public Advisor loveAppRagCloudAdvisor() {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(dashScopeApiKey)
				.build();

		DocumentRetriever documentRetriever = new DashScopeDocumentRetriever(dashScopeApi,
				DashScopeDocumentRetrieverOptions.builder()
						.indexName("axin-agent")
						.build());

		return RetrievalAugmentationAdvisor.builder()
				.documentRetriever(documentRetriever)
				.build();
	}
}
