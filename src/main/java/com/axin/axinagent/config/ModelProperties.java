package com.axin.axinagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 双模型配置：axin.model.primary / axin.model.light
 */
@Data
@ConfigurationProperties(prefix = "axin.model")
public class ModelProperties {

    private ModelSpec primary = new ModelSpec();

    private ModelSpec light = new ModelSpec();

    @Data
    public static class ModelSpec {

        /** 模型名称，例如 qwen-max / qwen-turbo */
        private String modelName;

        /** 采样温度 */
        private Double temperature;

        /** 最大输出 token */
        private Integer maxTokens;
    }
}
