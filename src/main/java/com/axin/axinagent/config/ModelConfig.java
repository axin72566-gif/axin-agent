package com.axin.axinagent.config;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;


/**
 * 模型层配置：基于默认 DashScope 模型克隆出主力/轻量两个 ChatModel Bean。
 */
@Configuration
@EnableConfigurationProperties(ModelProperties.class)
public class ModelConfig {

    @Primary
    @Bean("primaryChatModel")
    public ChatModel primaryChatModel(DashScopeChatModel dashScopeChatModel, ModelProperties modelProperties) {

        DashScopeChatOptions options = buildOptions(modelProperties.getPrimary());
        return dashScopeChatModel.mutate()
                .defaultOptions(options)
                .build();
    }

    @Bean("lightChatModel")
    public ChatModel lightChatModel(DashScopeChatModel dashScopeChatModel, ModelProperties modelProperties) {
        DashScopeChatOptions options = buildOptions(modelProperties.getLight());
        return dashScopeChatModel.mutate()
                .defaultOptions(options)
                .build();
    }

    private DashScopeChatOptions buildOptions(ModelProperties.ModelSpec spec) {
        DashScopeChatOptions.DashScopeChatOptionsBuilder builder = DashScopeChatOptions.builder();
        if (spec != null) {
            if (spec.getModelName() != null && !spec.getModelName().isBlank()) {
                builder.model(spec.getModelName());
            }
            if (spec.getTemperature() != null) {
                builder.temperature(spec.getTemperature());
            }
            if (spec.getMaxTokens() != null) {
                builder.maxToken(spec.getMaxTokens());
            }
        }
        return builder.build();
    }
}
