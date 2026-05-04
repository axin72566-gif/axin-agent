package com.axin.axinagent.llm;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 模型路由器：按任务类型返回对应的 ChatModel。
 */
@Component
public class ModelRouter {

    private final ChatModel primaryChatModel;
    private final ChatModel lightChatModel;

    public ModelRouter(@Qualifier("primaryChatModel") ChatModel primaryChatModel,
                       @Qualifier("lightChatModel") ChatModel lightChatModel) {
        this.primaryChatModel = primaryChatModel;
        this.lightChatModel = lightChatModel;
    }

    public ChatModel get(ModelType type) {
        if (type == null) {
            return primaryChatModel;
        }
        return switch (type) {
            case PRIMARY -> primaryChatModel;
            case LIGHT -> lightChatModel;
        };
    }
}
