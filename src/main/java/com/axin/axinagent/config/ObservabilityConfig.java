package com.axin.axinagent.config;

/**
 * 可观测性配置常量。
 */
public class ObservabilityConfig {

    /**
     * Agent 步骤 Trace List 前缀。
     * key: obs:trace:step:{taskId}
     */
    public static final String REDIS_TRACE_STEP_PREFIX = "obs:trace:step:";

    /**
     * LLM 调用 Trace List 前缀。
     * key: obs:trace:llm:{taskId}
     */
    public static final String REDIS_TRACE_LLM_PREFIX = "obs:trace:llm:";

    /**
     * 全局任务指标 Hash Key。
     * key: obs:metrics:task
     */
    public static final String REDIS_METRICS_TASK_KEY = "obs:metrics:task";

    /**
     * 工具调用指标 Hash 前缀。
     * key: obs:metrics:tool:{toolName}
     */
    public static final String REDIS_METRICS_TOOL_PREFIX = "obs:metrics:tool:";

    /**
     * 全局延迟样本 List Key（毫秒）。
     * key: obs:metrics:latency
     */
    public static final String REDIS_METRICS_LATENCY_KEY = "obs:metrics:latency";

    /**
     * 可观测性 Redis Key 过期时间（秒）。
     */
    public static final long REDIS_TTL_SECONDS = RocketMqConfig.REDIS_TTL_SECONDS;

    /**
     * DashScope qwen-turbo 输入单价（元/千 token）。
     */
    public static final double INPUT_PRICE_PER_1K = 0.004D;

    /**
     * DashScope qwen-turbo 输出单价（元/千 token）。
     */
    public static final double OUTPUT_PRICE_PER_1K = 0.012D;

    private ObservabilityConfig() {
    }
}
