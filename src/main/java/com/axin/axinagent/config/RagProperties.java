package com.axin.axinagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG 配置属性
 */
@Data
@ConfigurationProperties(prefix = "axin.rag")
public class RagProperties {

    /**
     * 是否在启动时初始化 RAG 数据
     */
    private boolean initOnStartup = true;
}
